package com.mel.fernbank.ledger.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mel.fernbank.ledger.TestcontainersConfiguration;
import com.mel.fernbank.ledger.api.dto.DepositWithdrawRequest;
import com.mel.fernbank.ledger.api.dto.MoneyDto;
import com.mel.fernbank.ledger.api.dto.OpenAccountRequest;
import com.mel.fernbank.ledger.auth.dto.LoginRequest;
import com.mel.fernbank.ledger.auth.dto.RegisterRequest;
import com.mel.fernbank.ledger.domain.AccountType;
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

/**
 * No class-level {@code @Transactional}: deposits/withdrawals go through
 * {@code REQUIRES_NEW} executors, which would silently escape a test-managed
 * transaction's rollback (see {@code OpenAccountServiceIT}'s note) - every test uses
 * fresh registered users instead.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AccountControllerIT {

	@Autowired
	private MockMvc mockMvc;

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void opensAnAccountAndReadsItBackWithEtagSupportingConditionalGet() throws Exception {
		String accessToken = registerAndLogin("ada-" + UUID.randomUUID() + "@example.com");

		Map<String, Object> account = openAccount(accessToken);
		String accountId = (String) account.get("id");
		assertThat(account.get("status")).isEqualTo("ACTIVE");
		assertThat(((Map<?, ?>) account.get("balance")).get("amount")).isEqualTo("0.00");

		MvcResult result = mockMvc.perform(get("/api/v1/accounts/" + accountId).header("Authorization", "Bearer " + accessToken))
				.andExpect(status().isOk())
				.andReturn();
		String etag = result.getResponse().getHeader("ETag");
		assertThat(etag).isNotBlank();

		mockMvc.perform(get("/api/v1/accounts/" + accountId)
						.header("Authorization", "Bearer " + accessToken)
						.header("If-None-Match", etag))
				.andExpect(status().isNotModified());
	}

	@Test
	void depositAndWithdrawUpdateTheBalance() throws Exception {
		String accessToken = registerAndLogin("grace-" + UUID.randomUUID() + "@example.com");
		Map<String, Object> account = openAccount(accessToken);
		String accountId = (String) account.get("id");

		mockMvc.perform(post("/api/v1/accounts/" + accountId + "/deposits")
						.header("Authorization", "Bearer " + accessToken)
						.header("Idempotency-Key", UUID.randomUUID().toString())
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(
								new DepositWithdrawRequest(new MoneyDto("100.00", "USD"), "seed"))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.newBalance.amount").value("100.00"));

		mockMvc.perform(post("/api/v1/accounts/" + accountId + "/withdrawals")
						.header("Authorization", "Bearer " + accessToken)
						.header("Idempotency-Key", UUID.randomUUID().toString())
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(
								new DepositWithdrawRequest(new MoneyDto("30.00", "USD"), "atm"))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.newBalance.amount").value("70.00"));
	}

	/** The literal IDOR regression test deferred from Phase 2/3: cross-customer access is 404, not 403. */
	@Test
	void crossCustomerAccountAccessReturns404NotLeakingExistence() throws Exception {
		String ownerToken = registerAndLogin("owner-" + UUID.randomUUID() + "@example.com");
		String attackerToken = registerAndLogin("attacker-" + UUID.randomUUID() + "@example.com");
		Map<String, Object> account = openAccount(ownerToken);
		String accountId = (String) account.get("id");

		mockMvc.perform(get("/api/v1/accounts/" + accountId).header("Authorization", "Bearer " + attackerToken))
				.andExpect(status().isNotFound());

		mockMvc.perform(post("/api/v1/accounts/" + accountId + "/deposits")
						.header("Authorization", "Bearer " + attackerToken)
						.header("Idempotency-Key", UUID.randomUUID().toString())
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(
								new DepositWithdrawRequest(new MoneyDto("1.00", "USD"), "attack"))))
				.andExpect(status().isNotFound());
	}

	@Test
	void openingAnAccountWithoutAnIdempotencyKeyIsRejected() throws Exception {
		String accessToken = registerAndLogin("noheader-" + UUID.randomUUID() + "@example.com");

		mockMvc.perform(post("/api/v1/accounts")
						.header("Authorization", "Bearer " + accessToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new OpenAccountRequest(AccountType.CHECKING, "USD"))))
				.andExpect(status().isBadRequest());
	}

	@Test
	void depositWithoutIdempotencyKeyIsRejected() throws Exception {
		String accessToken = registerAndLogin("noheader-deposit-" + UUID.randomUUID() + "@example.com");
		Map<String, Object> account = openAccount(accessToken);
		String accountId = (String) account.get("id");

		mockMvc.perform(post("/api/v1/accounts/" + accountId + "/deposits")
						.header("Authorization", "Bearer " + accessToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(
								new DepositWithdrawRequest(new MoneyDto("10.00", "USD"), "no key"))))
				.andExpect(status().isBadRequest());
	}

	@Test
	void withdrawWithoutIdempotencyKeyIsRejected() throws Exception {
		String accessToken = registerAndLogin("noheader-withdraw-" + UUID.randomUUID() + "@example.com");
		Map<String, Object> account = openAccount(accessToken);
		String accountId = (String) account.get("id");

		mockMvc.perform(post("/api/v1/accounts/" + accountId + "/withdrawals")
						.header("Authorization", "Bearer " + accessToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(
								new DepositWithdrawRequest(new MoneyDto("10.00", "USD"), "no key"))))
				.andExpect(status().isBadRequest());
	}

	@Test
	void depositWithAMalformedAmountStringIsRejectedAsBadRequestNotServerError() throws Exception {
		String accessToken = registerAndLogin("malformed-amount-" + UUID.randomUUID() + "@example.com");
		Map<String, Object> account = openAccount(accessToken);
		String accountId = (String) account.get("id");

		mockMvc.perform(post("/api/v1/accounts/" + accountId + "/deposits")
						.header("Authorization", "Bearer " + accessToken)
						.header("Idempotency-Key", UUID.randomUUID().toString())
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(
								new DepositWithdrawRequest(new MoneyDto("not-a-number", "USD"), "garbage"))))
				.andExpect(status().isBadRequest());
	}

	@Test
	void replayingTheSameIdempotencyKeyReturnsTheOriginalAccountRatherThanOpeningASecondOne() throws Exception {
		String accessToken = registerAndLogin("replay-" + UUID.randomUUID() + "@example.com");
		String idempotencyKey = UUID.randomUUID().toString();
		String body = objectMapper.writeValueAsString(new OpenAccountRequest(AccountType.CHECKING, "USD"));

		MvcResult first = mockMvc.perform(post("/api/v1/accounts")
						.header("Authorization", "Bearer " + accessToken)
						.header("Idempotency-Key", idempotencyKey)
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isCreated())
				.andReturn();
		MvcResult second = mockMvc.perform(post("/api/v1/accounts")
						.header("Authorization", "Bearer " + accessToken)
						.header("Idempotency-Key", idempotencyKey)
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andReturn();

		Map<?, ?> firstBody = objectMapper.readValue(first.getResponse().getContentAsString(), Map.class);
		Map<?, ?> secondBody = objectMapper.readValue(second.getResponse().getContentAsString(), Map.class);
		assertThat(secondBody.get("id")).isEqualTo(firstBody.get("id"));

		mockMvc.perform(get("/api/v1/accounts").header("Authorization", "Bearer " + accessToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1));
	}

	private Map<String, Object> openAccount(String accessToken) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/accounts")
						.header("Authorization", "Bearer " + accessToken)
						.header("Idempotency-Key", UUID.randomUUID().toString())
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new OpenAccountRequest(AccountType.CHECKING, "USD"))))
				.andExpect(status().isCreated())
				.andReturn();
		return objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
	}

	private String registerAndLogin(String email) throws Exception {
		mockMvc.perform(post("/api/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new RegisterRequest("Test User", email, "correct horse battery staple"))))
				.andExpect(status().isCreated());

		MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new LoginRequest(email, "correct horse battery staple"))))
				.andExpect(status().isOk())
				.andReturn();
		Map<?, ?> login = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
		return (String) login.get("accessToken");
	}
}
