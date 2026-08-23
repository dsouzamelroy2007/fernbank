package com.mel.fernbank.ledger.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Level;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mel.fernbank.ledger.LogCapture;
import com.mel.fernbank.ledger.TestcontainersConfiguration;
import com.mel.fernbank.ledger.auth.dto.LoginRequest;
import com.mel.fernbank.ledger.auth.dto.RefreshRequest;
import com.mel.fernbank.ledger.auth.dto.RegisterRequest;
import com.mel.fernbank.ledger.domain.Role;
import com.mel.fernbank.ledger.domain.User;
import com.mel.fernbank.ledger.repository.UserRepository;
import com.mel.fernbank.ledger.security.FernbankProperties;
import com.mel.fernbank.ledger.security.LoginRateLimiter;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthenticationFlowIT {

	@Autowired
	private MockMvc mockMvc;

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private JwtEncoder jwtEncoder;

	@Autowired
	private FernbankProperties properties;

	@Test
	void expiredAccessTokenIsRejected() throws Exception {
		String email = "expired@example.com";
		register(email, "correct horse battery staple");
		User user = userRepository.findByEmail(email).orElseThrow();

		Instant now = Instant.now();
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuer(properties.jwt().issuer())
				.issuedAt(now.minusSeconds(120))
				.expiresAt(now.minusSeconds(60))
				.subject(user.getId().toString())
				.claim("customer_id", user.getCustomerId().toString())
				.claim("roles", List.of())
				.claim("token_use", "access")
				.build();
		String expiredToken =
				jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();

		mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + expiredToken))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void tamperedTokenSignatureIsRejected() throws Exception {
		String email = "tampered@example.com";
		register(email, "correct horse battery staple");
		Map<String, Object> login = login(email, "correct horse battery staple");
		String accessToken = (String) login.get("accessToken");

		String[] parts = accessToken.split("\\.");
		char[] signatureChars = parts[2].toCharArray();
		signatureChars[0] = signatureChars[0] == 'A' ? 'B' : 'A';
		String tamperedToken = parts[0] + "." + parts[1] + "." + new String(signatureChars);

		mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + tamperedToken))
				.andExpect(status().isUnauthorized());
	}

	/**
	 * NOT_SUPPORTED rather than the class-level rollback semantics: both registrations
	 * must land in their own genuinely separate, immediately-committing transaction
	 * (matching two real HTTP requests) so the unique constraint on app_user.email is
	 * actually checked at flush time - inside one shared, never-committed test
	 * transaction, Hibernate can defer both UUID-keyed inserts past the point where
	 * this test observes them, masking the real behavior.
	 */
	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	void registeringTheSameEmailTwiceIsRejectedAsConflictNotServerError() throws Exception {
		String email = "duplicate-" + UUID.randomUUID() + "@example.com";
		register(email, "correct horse battery staple");

		mockMvc.perform(post("/api/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(
								new RegisterRequest("Second User", email, "another password"))))
				.andExpect(status().isConflict());
	}

	@Test
	void registeredUserCanLoginAndAccessOwnProfile_anonymousAndWrongRoleAreBlocked() throws Exception {
		String email = "ada@example.com";
		register(email, "correct horse battery staple");
		Map<String, Object> login = login(email, "correct horse battery staple");
		assertThat(login.get("status")).isEqualTo("AUTHENTICATED");
		String accessToken = (String) login.get("accessToken");

		mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + accessToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value(email));

		mockMvc.perform(get("/api/v1/me")).andExpect(status().isUnauthorized());

		mockMvc.perform(get("/api/v1/admin/ping").header("Authorization", "Bearer " + accessToken))
				.andExpect(status().isForbidden());
	}

	@Test
	void adminRoleCanAccessAdminEndpoint() throws Exception {
		String email = "admin@example.com";
		register(email, "correct horse battery staple");
		User user = userRepository.findByEmail(email).orElseThrow();
		user.grantRole(Role.ADMIN);
		userRepository.save(user);

		Map<String, Object> login = login(email, "correct horse battery staple");
		String accessToken = (String) login.get("accessToken");

		mockMvc.perform(get("/api/v1/admin/ping").header("Authorization", "Bearer " + accessToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("pong"));
	}

	@Test
	void refreshRotatesTheTokenAndReuseOfARotatedTokenRevokesTheWholeFamily() throws Exception {
		String email = "grace@example.com";
		register(email, "correct horse battery staple");
		Map<String, Object> login = login(email, "correct horse battery staple");
		String firstRefreshToken = (String) login.get("refreshToken");

		Map<String, Object> rotated = refresh(firstRefreshToken);
		String secondRefreshToken = (String) rotated.get("refreshToken");
		assertThat(secondRefreshToken).isNotEqualTo(firstRefreshToken);

		// Reusing the already-rotated first token is reuse detection: rejected, and it
		// revokes the whole family - so the legitimately-rotated second token also stops working.
		try (LogCapture logs = LogCapture.attachTo(AuthenticationService.class)) {
			mockMvc.perform(post("/api/v1/auth/refresh")
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(new RefreshRequest(firstRefreshToken))))
					.andExpect(status().isUnauthorized());

			assertThat(logs.hasEventAtLevelContaining(Level.WARN, "Refresh token reuse detected"))
					.isTrue();
		}

		mockMvc.perform(post("/api/v1/auth/refresh")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new RefreshRequest(secondRefreshToken))))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void repeatedBadLoginsAreRateLimitedAndTheAccountIsLockedPersistently() throws Exception {
		String email = "bob@example.com";
		register(email, "correct horse battery staple");

		for (int i = 0; i < 5; i++) {
			mockMvc.perform(post("/api/v1/auth/login")
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(new LoginRequest(email, "wrong password"))))
					.andExpect(status().isUnauthorized());
		}

		// 6th attempt within the window trips the Bucket4j rate limiter.
		try (LogCapture logs = LogCapture.attachTo(LoginRateLimiter.class)) {
			mockMvc.perform(post("/api/v1/auth/login")
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(new LoginRequest(email, "wrong password"))))
					.andExpect(status().isTooManyRequests());

			assertThat(logs.hasEventAtLevelContaining(Level.WARN, "Login rate limit exceeded"))
					.isTrue();
		}

		User user = userRepository.findByEmail(email).orElseThrow();
		assertThat(user.isLocked()).isTrue();
		assertThat(user.getFailedLoginAttempts()).isGreaterThanOrEqualTo(5);
	}

	private void register(String email, String password) throws Exception {
		mockMvc.perform(post("/api/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new RegisterRequest("Test User", email, password))))
				.andExpect(status().isCreated());
	}

	private Map<String, Object> login(String email, String password) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new LoginRequest(email, password))))
				.andExpect(status().isOk())
				.andReturn();
		return objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
	}

	private Map<String, Object> refresh(String refreshToken) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/auth/refresh")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new RefreshRequest(refreshToken))))
				.andExpect(status().isOk())
				.andReturn();
		return objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
	}
}
