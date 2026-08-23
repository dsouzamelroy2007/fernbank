package com.mel.fernbank.ledger.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mel.fernbank.ledger.TestcontainersConfiguration;
import com.mel.fernbank.ledger.auth.dto.LoginRequest;
import com.mel.fernbank.ledger.auth.dto.MfaEnrollConfirmRequest;
import com.mel.fernbank.ledger.auth.dto.MfaVerifyRequest;
import com.mel.fernbank.ledger.auth.dto.RegisterRequest;
import com.mel.fernbank.ledger.security.TotpService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MfaFlowIT {

	private static final String EMAIL = "totp-user@example.com";
	private static final String PASSWORD = "correct horse battery staple";

	@Autowired
	private MockMvc mockMvc;

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Autowired
	private TotpService totpService;

	private String accessToken;

	@Test
	void enrollConfirmThenLoginRequiresMfaAndVerifyWithTotpSucceeds() throws Exception {
		accessToken = registerLoginAndGetAccessToken();
		String secret = enroll();
		List<String> recoveryCodes = confirmEnrollment(secret);
		assertThat(recoveryCodes).hasSize(10);

		Map<String, Object> login = login();
		assertThat(login.get("status")).isEqualTo("MFA_REQUIRED");
		String mfaToken = (String) login.get("mfaToken");
		assertThat(login.get("accessToken")).isNull();

		String code = totpService.currentCode(secret, Instant.now());
		Map<String, Object> verified = verify(mfaToken, code);
		assertThat(verified.get("status")).isEqualTo("AUTHENTICATED");
		assertThat(verified.get("accessToken")).isNotNull();
	}

	@Test
	void aRecoveryCodeWorksOnceThenIsRejectedOnReuse() throws Exception {
		accessToken = registerLoginAndGetAccessToken();
		String secret = enroll();
		List<String> recoveryCodes = confirmEnrollment(secret);
		String recoveryCode = recoveryCodes.get(0);

		Map<String, Object> login = login();
		String mfaToken = (String) login.get("mfaToken");

		Map<String, Object> verified = verify(mfaToken, recoveryCode);
		assertThat(verified.get("status")).isEqualTo("AUTHENTICATED");

		// The mfa token itself is single-purpose but not consumed by verification, so
		// get a fresh challenge to prove the *recovery code* - not the challenge - is
		// what's single-use.
		Map<String, Object> secondLogin = login();
		String secondMfaToken = (String) secondLogin.get("mfaToken");

		mockMvc.perform(post("/api/v1/auth/mfa/verify")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new MfaVerifyRequest(secondMfaToken, recoveryCode))))
				.andExpect(status().isUnauthorized());
	}

	private String registerLoginAndGetAccessToken() throws Exception {
		mockMvc.perform(post("/api/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new RegisterRequest("Totp User", EMAIL, PASSWORD))))
				.andExpect(status().isCreated());

		Map<String, Object> login = login();
		return (String) login.get("accessToken");
	}

	private String enroll() throws Exception {
		MvcResult result = mockMvc.perform(
						post("/api/v1/auth/mfa/enroll").header("Authorization", "Bearer " + accessToken))
				.andExpect(status().isOk())
				.andReturn();
		Map<String, Object> body = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
		return (String) body.get("secret");
	}

	@SuppressWarnings("unchecked")
	private List<String> confirmEnrollment(String secret) throws Exception {
		String code = totpService.currentCode(secret, Instant.now());
		MvcResult result = mockMvc.perform(post("/api/v1/auth/mfa/enroll/confirm")
						.header("Authorization", "Bearer " + accessToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new MfaEnrollConfirmRequest(code))))
				.andExpect(status().isOk())
				.andReturn();
		Map<String, Object> body = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
		return (List<String>) body.get("recoveryCodes");
	}

	private Map<String, Object> login() throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new LoginRequest(EMAIL, PASSWORD))))
				.andExpect(status().isOk())
				.andReturn();
		return objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
	}

	private Map<String, Object> verify(String mfaToken, String code) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/auth/mfa/verify")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new MfaVerifyRequest(mfaToken, code))))
				.andExpect(status().isOk())
				.andReturn();
		return objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
	}
}
