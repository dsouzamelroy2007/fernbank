package com.mel.fernbank.ledger.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TotpServiceTest {

	/** RFC 6238 Appendix B's 20-byte ASCII secret, used raw (not base32-decoded). */
	private static final byte[] RFC_SECRET = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

	@ParameterizedTest
	@CsvSource({"59, 94287082", "1111111109, 07081804", "1111111111, 14050471", "1234567890, 89005924"})
	void hotpMatchesRfc6238AppendixBTestVectors(long unixTime, String expectedCode) {
		long timeStep = unixTime / 30;

		assertThat(TotpService.hotp(RFC_SECRET, timeStep, 8)).isEqualTo(expectedCode);
	}

	@Test
	void generatedSecretRoundTripsThroughVerifyCode() {
		TotpService totpService = new TotpService();
		String secret = totpService.generateSecret();
		Instant now = Instant.now();

		String code = totpService.currentCode(secret, now);

		assertThat(totpService.verifyCode(secret, code, now)).isTrue();
	}

	@Test
	void verifyCodeRejectsAWrongCode() {
		TotpService totpService = new TotpService();
		String secret = totpService.generateSecret();

		assertThat(totpService.verifyCode(secret, "000000", Instant.now())).isFalse();
	}

	@Test
	void verifyCodeToleratesOneStepOfClockDrift() {
		TotpService totpService = new TotpService();
		String secret = totpService.generateSecret();
		Instant now = Instant.now();
		String code = totpService.currentCode(secret, now);

		assertThat(totpService.verifyCode(secret, code, now.plusSeconds(30))).isTrue();
		assertThat(totpService.verifyCode(secret, code, now.plusSeconds(90))).isFalse();
	}

	@Test
	void otpAuthUriContainsTheSecretAndIssuer() {
		TotpService totpService = new TotpService();
		String secret = totpService.generateSecret();

		String uri = totpService.otpAuthUri(secret, "ada@example.com", "fernbank");

		assertThat(uri)
				.startsWith("otpauth://totp/")
				.contains("secret=" + secret)
				.contains("issuer=fernbank");
	}
}
