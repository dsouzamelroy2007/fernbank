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

	public record Metrics(String prometheusUser, String prometheusPassword) {}
}
