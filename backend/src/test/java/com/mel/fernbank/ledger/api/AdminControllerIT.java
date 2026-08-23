package com.mel.fernbank.ledger.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mel.fernbank.ledger.TestcontainersConfiguration;
import com.mel.fernbank.ledger.api.dto.DepositWithdrawRequest;
import com.mel.fernbank.ledger.api.dto.FreezeAccountRequest;
import com.mel.fernbank.ledger.api.dto.MoneyDto;
import com.mel.fernbank.ledger.api.dto.OpenAccountRequest;
import com.mel.fernbank.ledger.auth.dto.LoginRequest;
import com.mel.fernbank.ledger.auth.dto.RegisterRequest;
import com.mel.fernbank.ledger.domain.AccountType;
import com.mel.fernbank.ledger.domain.Role;
import com.mel.fernbank.ledger.domain.User;
import com.mel.fernbank.ledger.repository.UserRepository;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** No class-level {@code @Transactional}: freeze/deposit go through the same executors AccountControllerIT avoids it for. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AdminControllerIT {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	private final ObjectMapper objectMapper = new ObjectMapper();

	/**
	 * Deposits are still allowed on a FROZEN account - only debits are blocked
	 * ({@code AccountNotActiveException}'s javadoc: "cannot be debited") - so the
	 * regression check here is a withdrawal, not a deposit.
	 */
	@Test
	void adminCanFreezeAnAccountAndFurtherWithdrawalsAreRejected() throws Exception {
		String customerToken = registerAndLogin("kim-" + UUID.randomUUID() + "@example.com");
		String accountId = openAccount(customerToken);
		mockMvc.perform(post("/api/v1/accounts/" + accountId + "/deposits")
						.header("Authorization", "Bearer " + customerToken)
						.header("Idempotency-Key", UUID.randomUUID().toString())
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(
								new DepositWithdrawRequest(new MoneyDto("50.00", "USD"), "seed"))))
				.andExpect(status().isOk());
		String adminToken = registerAdminAndLogin("admin-" + UUID.randomUUID() + "@example.com");

		mockMvc.perform(patch("/api/v1/admin/accounts/" + accountId + "/freeze")
						.header("Authorization", "Bearer " + adminToken)
						.header("Idempotency-Key", UUID.randomUUID().toString())
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new FreezeAccountRequest("suspected fraud"))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("FROZEN"));

		mockMvc.perform(post("/api/v1/accounts/" + accountId + "/withdrawals")
						.header("Authorization", "Bearer " + customerToken)
						.header("Idempotency-Key", UUID.randomUUID().toString())
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(
								new DepositWithdrawRequest(new MoneyDto("10.00", "USD"), "should fail"))))
				.andExpect(status().isConflict());
	}

	@Test
	void freezingAsANonAdminIsForbidden() throws Exception {
		String customerToken = registerAndLogin("leo-" + UUID.randomUUID() + "@example.com");
		String accountId = openAccount(customerToken);

		mockMvc.perform(patch("/api/v1/admin/accounts/" + accountId + "/freeze")
						.header("Authorization", "Bearer " + customerToken)
						.header("Idempotency-Key", UUID.randomUUID().toString())
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new FreezeAccountRequest("nope"))))
				.andExpect(status().isForbidden());
	}

	@Test
	void adminCanListAuditEvents() throws Exception {
		String customerToken = registerAndLogin("mia-" + UUID.randomUUID() + "@example.com");
		openAccount(customerToken);
		String adminToken = registerAdminAndLogin("admin2-" + UUID.randomUUID() + "@example.com");

		mockMvc.perform(get("/api/v1/admin/audit-events").header("Authorization", "Bearer " + adminToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data").isArray());
	}

	private String openAccount(String accessToken) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/accounts")
						.header("Authorization", "Bearer " + accessToken)
						.header("Idempotency-Key", UUID.randomUUID().toString())
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new OpenAccountRequest(AccountType.CHECKING, "USD"))))
				.andExpect(status().isCreated())
				.andReturn();
		Map<?, ?> body = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
		return (String) body.get("id");
	}

	/** Roles are baked into the JWT at login time, so the admin role must be granted before logging in. */
	private String registerAdminAndLogin(String email) throws Exception {
		register(email);
		User user = userRepository.findByEmail(email).orElseThrow();
		user.grantRole(Role.ADMIN);
		userRepository.save(user);
		return login(email);
	}

	private String registerAndLogin(String email) throws Exception {
		register(email);
		return login(email);
	}

	private void register(String email) throws Exception {
		mockMvc.perform(post("/api/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(
								new RegisterRequest("Test User", email, "correct horse battery staple"))))
				.andExpect(status().isCreated());
	}

	private String login(String email) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new LoginRequest(email, "correct horse battery staple"))))
				.andExpect(status().isOk())
				.andReturn();
		Map<?, ?> login = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
		return (String) login.get("accessToken");
	}
}
