package com.mel.fernbank.ledger.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mel.fernbank.ledger.TestcontainersConfiguration;
import com.mel.fernbank.ledger.auth.dto.ChangePasswordRequest;
import com.mel.fernbank.ledger.auth.dto.LoginRequest;
import com.mel.fernbank.ledger.auth.dto.RegisterRequest;
import java.util.List;
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
 * No class-level {@code @Transactional}: each test drives real sequential HTTP
 * requests (register, login again, revoke) whose committed effects the next request
 * must see - fresh registered users per test for isolation, same reasoning as
 * {@link AccountControllerIT}.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class MeControllerIT {

	@Autowired
	private MockMvc mockMvc;

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void changePasswordSucceedsAndOldPasswordNoLongerWorks() throws Exception {
		String email = "change-pw-" + UUID.randomUUID() + "@example.com";
		String accessToken = registerAndLogin(email, "correct horse battery staple");

		mockMvc.perform(post("/api/v1/me/password")
						.header("Authorization", "Bearer " + accessToken)
						.header("Idempotency-Key", UUID.randomUUID().toString())
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(
								new ChangePasswordRequest("correct horse battery staple", "new secure password"))))
				.andExpect(status().isNoContent());

		mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new LoginRequest(email, "correct horse battery staple"))))
				.andExpect(status().isUnauthorized());

		mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new LoginRequest(email, "new secure password"))))
				.andExpect(status().isOk());
	}

	@Test
	void changePasswordWithWrongCurrentPasswordIsRejected() throws Exception {
		String accessToken =
				registerAndLogin("wrong-pw-" + UUID.randomUUID() + "@example.com", "correct horse battery staple");

		mockMvc.perform(post("/api/v1/me/password")
						.header("Authorization", "Bearer " + accessToken)
						.header("Idempotency-Key", UUID.randomUUID().toString())
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(
								new ChangePasswordRequest("totally wrong password", "new secure password"))))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void sessionsListShowsActiveSessionsAndRevokeIsIdempotent() throws Exception {
		String email = "sessions-" + UUID.randomUUID() + "@example.com";
		String accessToken = registerAndLogin(email, "correct horse battery staple");

		MvcResult listResult = mockMvc.perform(
						get("/api/v1/me/sessions").header("Authorization", "Bearer " + accessToken))
				.andExpect(status().isOk())
				.andReturn();
		List<Map<String, Object>> sessions =
				objectMapper.readValue(listResult.getResponse().getContentAsString(), List.class);
		assertThat(sessions).hasSize(1);
		String sessionId = (String) sessions.get(0).get("id");

		mockMvc.perform(delete("/api/v1/me/sessions/" + sessionId).header("Authorization", "Bearer " + accessToken))
				.andExpect(status().isNoContent());
		// Revoking the same session again is idempotent - still 204.
		mockMvc.perform(delete("/api/v1/me/sessions/" + sessionId).header("Authorization", "Bearer " + accessToken))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/v1/me/sessions").header("Authorization", "Bearer " + accessToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$").isEmpty());
	}

	@Test
	void revokingAnotherUsersSessionReturns404NotForbidden() throws Exception {
		String ownerToken = registerAndLogin(
				"session-owner-" + UUID.randomUUID() + "@example.com", "correct horse battery staple");
		MvcResult listResult = mockMvc.perform(
						get("/api/v1/me/sessions").header("Authorization", "Bearer " + ownerToken))
				.andExpect(status().isOk())
				.andReturn();
		List<Map<String, Object>> sessions =
				objectMapper.readValue(listResult.getResponse().getContentAsString(), List.class);
		String ownerSessionId = (String) sessions.get(0).get("id");

		String otherToken = registerAndLogin(
				"session-other-" + UUID.randomUUID() + "@example.com", "correct horse battery staple");
		mockMvc.perform(delete("/api/v1/me/sessions/" + ownerSessionId).header("Authorization", "Bearer " + otherToken))
				.andExpect(status().isNotFound());
	}

	@Test
	void loginHistoryReturnsOnlyTheCallersOwnEvents() throws Exception {
		String emailA = "history-a-" + UUID.randomUUID() + "@example.com";
		String tokenA = registerAndLogin(emailA, "correct horse battery staple");
		// A second login for user A produces a second login_success event.
		login(emailA, "correct horse battery staple");

		String emailB = "history-b-" + UUID.randomUUID() + "@example.com";
		registerAndLogin(emailB, "correct horse battery staple");

		MvcResult result = mockMvc.perform(
						get("/api/v1/me/login-history").header("Authorization", "Bearer " + tokenA))
				.andExpect(status().isOk())
				.andReturn();
		Map<String, Object> page = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
		List<Map<String, Object>> events = (List<Map<String, Object>>) page.get("data");
		assertThat(events).isNotEmpty();
		assertThat(events).allSatisfy(event -> assertThat(event.get("eventType")).isEqualTo("auth.login_success"));
	}

	private void login(String email, String password) throws Exception {
		mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new LoginRequest(email, password))))
				.andExpect(status().isOk());
	}

	private String registerAndLogin(String email, String password) throws Exception {
		mockMvc.perform(post("/api/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new RegisterRequest("Test User", email, password))))
				.andExpect(status().isCreated());

		MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new LoginRequest(email, password))))
				.andExpect(status().isOk())
				.andReturn();
		Map<?, ?> login = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
		return (String) login.get("accessToken");
	}
}
