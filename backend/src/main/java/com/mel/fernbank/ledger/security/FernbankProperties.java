package com.mel.fernbank.ledger.security;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "fernbank")
public record FernbankProperties(Jwt jwt, Cors cors, Auth auth, Metrics metrics) {

	public record Jwt(
			String issuer,
			Duration accessTokenTtl,
			Duration refreshTokenTtl,
			Duration mfaChallengeTtl,
			Duration stepUpTtl,
			String privateKeyPem,
			String publicKeyPem) {}

	public record Cors(List<String> allowedOrigins) {}

	public record Auth(
			int recoveryCodeCount,
			int loginRateLimitAttempts,
			Duration loginRateLimitWindow,
			long stepUpThresholdMinorUnits,
			String internalServiceKey) {}

	/** Scrape credential for /actuator/prometheus - HTTP Basic, not the JWT resource
	 * server, since Prometheus has no OAuth2 client-credentials flow to talk to and
	 * this app has no service-account token issuance. Kept separate from the
	 * hasRole("ADMIN") JWT gate on every other /actuator/** path. */
	public record Metrics(String prometheusUser, String prometheusPassword) {}
}
