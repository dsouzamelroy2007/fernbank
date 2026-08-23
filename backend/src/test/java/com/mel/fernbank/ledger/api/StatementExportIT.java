package com.mel.fernbank.ledger.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mel.fernbank.ledger.TestcontainersConfiguration;
import com.mel.fernbank.ledger.api.dto.DepositWithdrawRequest;
import com.mel.fernbank.ledger.api.dto.MoneyDto;
import com.mel.fernbank.ledger.api.dto.OpenAccountRequest;
import com.mel.fernbank.ledger.auth.dto.LoginRequest;
import com.mel.fernbank.ledger.auth.dto.RegisterRequest;
import com.mel.fernbank.ledger.domain.AccountType;
import java.nio.charset.StandardCharsets;
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
 * No class-level {@code @Transactional}: deposits go through a {@code REQUIRES_NEW}
 * executor (same reasoning as {@link AccountControllerIT}) - fresh registered users
 * per test instead.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class StatementExportIT {

	@Autowired
	private MockMvc mockMvc;

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void csvExportIncludesTheDepositDescriptionAndAmount() throws Exception {
		String accessToken = registerAndLogin("csv-export-" + UUID.randomUUID() + "@example.com");
		String accountId = openAccount(accessToken);
		deposit(accessToken, accountId, "100.00", "birthday gift");

		MvcResult result = mockMvc.perform(get("/api/v1/accounts/" + accountId + "/statement/export")
						.header("Authorization", "Bearer " + accessToken)
						.param("format", "csv"))
				.andExpect(status().isOk())
				.andReturn();

		assertThat(result.getResponse().getContentType()).startsWith("text/csv");
		assertThat(result.getResponse().getHeader("Content-Disposition")).contains("attachment");
		String csv = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
		assertThat(csv).contains("date,description,amount,currency");
		assertThat(csv).contains("birthday gift");
		assertThat(csv).contains("100.00");
	}

	@Test
	void pdfExportProducesANonEmptyPdfDocument() throws Exception {
		String accessToken = registerAndLogin("pdf-export-" + UUID.randomUUID() + "@example.com");
		String accountId = openAccount(accessToken);
		deposit(accessToken, accountId, "50.00", "seed");

		MvcResult result = mockMvc.perform(get("/api/v1/accounts/" + accountId + "/statement/export")
						.header("Authorization", "Bearer " + accessToken)
						.param("format", "pdf"))
				.andExpect(status().isOk())
				.andReturn();

		assertThat(result.getResponse().getContentType()).isEqualTo(MediaType.APPLICATION_PDF_VALUE);
		byte[] body = result.getResponse().getContentAsByteArray();
		assertThat(body.length).isGreaterThan(0);
		assertThat(new String(body, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
	}

	@Test
	void exportForAnotherCustomersAccountReturns404NotForbidden() throws Exception {
		String ownerToken = registerAndLogin("export-owner-" + UUID.randomUUID() + "@example.com");
		String accountId = openAccount(ownerToken);

		String otherToken = registerAndLogin("export-other-" + UUID.randomUUID() + "@example.com");
		mockMvc.perform(get("/api/v1/accounts/" + accountId + "/statement/export")
						.header("Authorization", "Bearer " + otherToken))
				.andExpect(status().isNotFound());
	}

	private void deposit(String accessToken, String accountId, String amount, String description) throws Exception {
		mockMvc.perform(post("/api/v1/accounts/" + accountId + "/deposits")
						.header("Authorization", "Bearer " + accessToken)
						.header("Idempotency-Key", UUID.randomUUID().toString())
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(
								new DepositWithdrawRequest(new MoneyDto(amount, "USD"), description))))
				.andExpect(status().isOk());
	}

	private String openAccount(String accessToken) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/accounts")
						.header("Authorization", "Bearer " + accessToken)
						.header("Idempotency-Key", UUID.randomUUID().toString())
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new OpenAccountRequest(AccountType.CHECKING, "USD"))))
				.andExpect(status().isCreated())
				.andReturn();
		Map<?, ?> account = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
		return (String) account.get("id");
	}

	private String registerAndLogin(String email) throws Exception {
		mockMvc.perform(post("/api/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(
								new RegisterRequest("Test User", email, "correct horse battery staple"))))
				.andExpect(status().isCreated());

		MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(
								new LoginRequest(email, "correct horse battery staple"))))
				.andExpect(status().isOk())
				.andReturn();
		Map<?, ?> login = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
		return (String) login.get("accessToken");
	}
}
