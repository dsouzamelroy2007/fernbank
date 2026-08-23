package com.mel.fernbank.ledger.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class StepUpAuthServiceTest {

	private final StepUpAuthService service = new StepUpAuthService();

	@Test
	void notElevatedWhenClaimIsAbsent() {
		Jwt jwt = jwtWithClaims(Map.of());

		assertThat(service.isElevated(jwt)).isFalse();
		assertThatThrownBy(() -> service.requireElevated(jwt)).isInstanceOf(StepUpRequiredException.class);
	}

	@Test
	void elevatedWhenClaimIsInTheFuture() {
		Jwt jwt = jwtWithClaims(Map.of(
				StepUpAuthService.ELEVATED_UNTIL_CLAIM,
				Instant.now().plusSeconds(300).getEpochSecond()));

		assertThat(service.isElevated(jwt)).isTrue();
		service.requireElevated(jwt);
	}

	@Test
	void notElevatedWhenClaimIsInThePast() {
		Jwt jwt = jwtWithClaims(Map.of(
				StepUpAuthService.ELEVATED_UNTIL_CLAIM,
				Instant.now().minusSeconds(60).getEpochSecond()));

		assertThat(service.isElevated(jwt)).isFalse();
	}

	private Jwt jwtWithClaims(Map<String, Object> extraClaims) {
		Jwt.Builder builder = Jwt.withTokenValue("test-token")
				.header("alg", "RS256")
				.subject("11111111-1111-1111-1111-111111111111")
				.issuedAt(Instant.now())
				.expiresAt(Instant.now().plusSeconds(600));
		extraClaims.forEach(builder::claim);
		return builder.build();
	}
}
