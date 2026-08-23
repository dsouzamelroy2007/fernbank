package com.mel.fernbank.ledger.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mel.fernbank.ledger.TestcontainersConfiguration;
import com.mel.fernbank.ledger.api.dto.PayeeRequest;
import com.mel.fernbank.ledger.auth.dto.LoginRequest;
import com.mel.fernbank.ledger.auth.dto.RegisterRequest;
import com.mel.fernbank.ledger.banking.AccountNumberGenerator;
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
import org.springframework.transaction.annotation.Transactional;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PayeeControllerIT {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private AccountNumberGenerator accountNumberGenerator;

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void addsListsAndDeletesAPayee() throws Exception {
		String accessToken = registerAndLogin("ivy-" + UUID.randomUUID() + "@example.com");
		String targetAccountNumber = accountNumberGenerator.generate();

		MvcResult created = mockMvc.perform(post("/api/v1/payees")
						.header("Authorization", "Bearer " + accessToken)
						.header("Idempotency-Key", UUID.randomUUID().toString())
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new PayeeRequest("Landlord", targetAccountNumber))))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.name").value("Landlord"))
				.andReturn();
		String payeeId = ((Map<?, ?>) objectMapper.readValue(created.getResponse().getContentAsString(), Map.class)).get("id").toString();

		mockMvc.perform(get("/api/v1/payees").header("Authorization", "Bearer " + accessToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1));

		mockMvc.perform(delete("/api/v1/payees/" + payeeId).header("Authorization", "Bearer " + accessToken))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/v1/payees").header("Authorization", "Bearer " + accessToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void rejectsAPayeeWithAnInvalidAccountNumberChecksum() throws Exception {
		String accessToken = registerAndLogin("jack-" + UUID.randomUUID() + "@example.com");

		mockMvc.perform(post("/api/v1/payees")
						.header("Authorization", "Bearer " + accessToken)
						.header("Idempotency-Key", UUID.randomUUID().toString())
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new PayeeRequest("Bad Payee", "FB0000000000000000000000"))))
				.andExpect(status().isBadRequest());
	}

	@Test
	void aCrossCustomerPayeeDeleteReturns404() throws Exception {
		String ownerToken = registerAndLogin("owner-" + UUID.randomUUID() + "@example.com");
		String attackerToken = registerAndLogin("attacker-" + UUID.randomUUID() + "@example.com");
		String targetAccountNumber = accountNumberGenerator.generate();

		MvcResult created = mockMvc.perform(post("/api/v1/payees")
						.header("Authorization", "Bearer " + ownerToken)
						.header("Idempotency-Key", UUID.randomUUID().toString())
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new PayeeRequest("Landlord", targetAccountNumber))))
				.andExpect(status().isCreated())
				.andReturn();
		String payeeId = ((Map<?, ?>) objectMapper.readValue(created.getResponse().getContentAsString(), Map.class)).get("id").toString();

		mockMvc.perform(delete("/api/v1/payees/" + payeeId).header("Authorization", "Bearer " + attackerToken))
				.andExpect(status().isNotFound());
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
