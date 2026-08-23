package com.mel.fernbank.ledger.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mel.fernbank.ledger.TestcontainersConfiguration;
import com.mel.fernbank.ledger.api.dto.DepositWithdrawRequest;
import com.mel.fernbank.ledger.api.dto.MoneyDto;
import com.mel.fernbank.ledger.api.dto.OpenAccountRequest;
import com.mel.fernbank.ledger.api.dto.ScheduleTransferRequest;
import com.mel.fernbank.ledger.api.dto.TransferRequest;
import com.mel.fernbank.ledger.auth.dto.LoginRequest;
import com.mel.fernbank.ledger.auth.dto.RegisterRequest;
import com.mel.fernbank.ledger.domain.AccountType;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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

/** No class-level {@code @Transactional}: see {@code AccountControllerIT}'s note on REQUIRES_NEW. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class TransferControllerIT {

	@Autowired
	private MockMvc mockMvc;

	private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

	@Test
	void transfersBetweenTwoOwnedAccountsByDestinationAccountId() throws Exception {
		String accessToken = registerAndLogin("ada-" + UUID.randomUUID() + "@example.com");
		String source = openAccount(accessToken, "USD");
		String destination = openAccount(accessToken, "USD");
		deposit(accessToken, source, "100.00");

		mockMvc.perform(post("/api/v1/transfers")
						.header("Authorization", "Bearer " + accessToken)
						.header("Idempotency-Key", UUID.randomUUID().toString())
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new TransferRequest(
								UUID.fromString(source), UUID.fromString(destination), null,
								new MoneyDto("40.00", "USD"), "rent"))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.sourceNewBalance.amount").value("60.00"))
				.andExpect(jsonPath("$.destinationNewBalance.amount").value("40.00"));
	}

	@Test
	void transfersByDestinationAccountNumberResolvesTheAccount() throws Exception {
		String senderToken = registerAndLogin("bob-" + UUID.randomUUID() + "@example.com");
		String recipientToken = registerAndLogin("carol-" + UUID.randomUUID() + "@example.com");
		String source = openAccount(senderToken, "USD");
		Map<String, Object> destinationAccount = openAccountDetail(recipientToken, "USD");
		String destinationAccountNumber = (String) destinationAccount.get("accountNumber");
		deposit(senderToken, source, "100.00");

		mockMvc.perform(post("/api/v1/transfers")
						.header("Authorization", "Bearer " + senderToken)
						.header("Idempotency-Key", UUID.randomUUID().toString())
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new TransferRequest(
								UUID.fromString(source), null, destinationAccountNumber,
								new MoneyDto("25.00", "USD"), "to a payee"))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.destinationNewBalance.amount").value("25.00"));
	}

	@Test
	void providingBothOrNeitherDestinationFieldIsRejected() throws Exception {
		String accessToken = registerAndLogin("dan-" + UUID.randomUUID() + "@example.com");
		String source = openAccount(accessToken, "USD");

		mockMvc.perform(post("/api/v1/transfers")
						.header("Authorization", "Bearer " + accessToken)
						.header("Idempotency-Key", UUID.randomUUID().toString())
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new TransferRequest(
								UUID.fromString(source), null, null, new MoneyDto("1.00", "USD"), null))))
				.andExpect(status().isBadRequest());
	}

	@Test
	void insufficientFundsReturnsUnprocessableContentProblemDetail() throws Exception {
		String accessToken = registerAndLogin("erin-" + UUID.randomUUID() + "@example.com");
		String source = openAccount(accessToken, "USD");
		String destination = openAccount(accessToken, "USD");

		mockMvc.perform(post("/api/v1/transfers")
						.header("Authorization", "Bearer " + accessToken)
						.header("Idempotency-Key", UUID.randomUUID().toString())
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new TransferRequest(
								UUID.fromString(source), UUID.fromString(destination), null,
								new MoneyDto("1.00", "USD"), null))))
				.andExpect(status().is(422))
				.andExpect(jsonPath("$.correlationId").exists());
	}

	@Test
	void transferWithoutIdempotencyKeyIsRejected() throws Exception {
		String accessToken = registerAndLogin("noheader-" + UUID.randomUUID() + "@example.com");
		String source = openAccount(accessToken, "USD");
		String destination = openAccount(accessToken, "USD");
		deposit(accessToken, source, "100.00");

		mockMvc.perform(post("/api/v1/transfers")
						.header("Authorization", "Bearer " + accessToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new TransferRequest(
								UUID.fromString(source), UUID.fromString(destination), null,
								new MoneyDto("10.00", "USD"), "no key"))))
				.andExpect(status().isBadRequest());
	}

	@Test
	void aCrossCustomerSourceAccountReturns404NotLeakingExistence() throws Exception {
		String ownerToken = registerAndLogin("owner-" + UUID.randomUUID() + "@example.com");
		String attackerToken = registerAndLogin("attacker-" + UUID.randomUUID() + "@example.com");
		String ownerAccount = openAccount(ownerToken, "USD");
		String attackerAccount = openAccount(attackerToken, "USD");

		mockMvc.perform(post("/api/v1/transfers")
						.header("Authorization", "Bearer " + attackerToken)
						.header("Idempotency-Key", UUID.randomUUID().toString())
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new TransferRequest(
								UUID.fromString(ownerAccount), UUID.fromString(attackerAccount), null,
								new MoneyDto("1.00", "USD"), null))))
				.andExpect(status().isNotFound());
	}

	@Test
	void aTransferAboveTheStepUpThresholdWithoutElevationIsForbidden() throws Exception {
		String accessToken = registerAndLogin("fay-" + UUID.randomUUID() + "@example.com");
		String source = openAccount(accessToken, "USD");
		String destination = openAccount(accessToken, "USD");
		deposit(accessToken, source, "2000.00");

		mockMvc.perform(post("/api/v1/transfers")
						.header("Authorization", "Bearer " + accessToken)
						.header("Idempotency-Key", UUID.randomUUID().toString())
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new TransferRequest(
								UUID.fromString(source), UUID.fromString(destination), null,
								new MoneyDto("1500.00", "USD"), "big transfer"))))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.type").value("https://fernbank.dev/problems/step-up-required"));
	}

	@Test
	void schedulesATransferForTheFuture() throws Exception {
		String accessToken = registerAndLogin("gia-" + UUID.randomUUID() + "@example.com");
		String source = openAccount(accessToken, "USD");
		String destination = openAccount(accessToken, "USD");
		deposit(accessToken, source, "50.00");

		mockMvc.perform(post("/api/v1/transfers/scheduled")
						.header("Authorization", "Bearer " + accessToken)
						.header("Idempotency-Key", UUID.randomUUID().toString())
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new ScheduleTransferRequest(
								UUID.fromString(source),
								UUID.fromString(destination),
								new MoneyDto("10.00", "USD"),
								"next month",
								Instant.now().plus(30, ChronoUnit.DAYS)))))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("PENDING"));
	}

	private String deposit(String accessToken, String accountId, String amount) throws Exception {
		return mockMvc.perform(post("/api/v1/accounts/" + accountId + "/deposits")
						.header("Authorization", "Bearer " + accessToken)
						.header("Idempotency-Key", UUID.randomUUID().toString())
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(
								new DepositWithdrawRequest(new MoneyDto(amount, "USD"), "seed"))))
				.andExpect(status().isOk())
				.andReturn()
				.getResponse()
				.getContentAsString();
	}

	private String openAccount(String accessToken, String currency) throws Exception {
		return (String) openAccountDetail(accessToken, currency).get("id");
	}

	private Map<String, Object> openAccountDetail(String accessToken, String currency) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/accounts")
						.header("Authorization", "Bearer " + accessToken)
						.header("Idempotency-Key", UUID.randomUUID().toString())
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new OpenAccountRequest(AccountType.CHECKING, currency))))
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
