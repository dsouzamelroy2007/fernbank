package com.mel.fernbank.ledger.security;

import java.time.Instant;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Checks whether an access token carries a recent MFA elevation (the {@code
 * elevated_until} claim added by {@code POST /auth/step-up}). Phase 3 will call
 * {@link #requireElevated(Jwt)} before permitting a large transfer; nothing calls it
 * yet since transfers don't exist in this phase.
 */
@Component
public class StepUpAuthService {

	public static final String ELEVATED_UNTIL_CLAIM = "elevated_until";

	public boolean isElevated(Jwt jwt) {
		Long elevatedUntilEpochSeconds = jwt.getClaim(ELEVATED_UNTIL_CLAIM);
		return elevatedUntilEpochSeconds != null
				&& Instant.ofEpochSecond(elevatedUntilEpochSeconds).isAfter(Instant.now());
	}

	public void requireElevated(Jwt jwt) {
		if (!isElevated(jwt)) {
			throw new StepUpRequiredException();
		}
	}
}
