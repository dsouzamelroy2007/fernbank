package com.mel.fernbank.ledger.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;
import org.junit.jupiter.api.Test;

class MfaSecretConverterTest {

	private final MfaSecretConverter converter = new MfaSecretConverter();

	@Test
	void roundTripsASecret() {
		String encrypted = converter.convertToDatabaseColumn("JBSWY3DPEHPK3PXP");

		assertThat(encrypted).isNotEqualTo("JBSWY3DPEHPK3PXP");
		assertThat(converter.convertToEntityAttribute(encrypted)).isEqualTo("JBSWY3DPEHPK3PXP");
	}

	@Test
	void nullPassesThroughBothDirections() {
		assertThat(converter.convertToDatabaseColumn(null)).isNull();
		assertThat(converter.convertToEntityAttribute(null)).isNull();
	}

	@Test
	void undecryptableCiphertextDegradesToNullInsteadOfThrowing() {
		// Tampering with real ciphertext simulates what an encryption-key rotation
		// looks like from this converter's perspective (a GCM tag that no longer
		// matches the live key) without juggling two different
		// MFA_SECRET_ENCRYPTION_KEY values in one test process. Regression test for a
		// real incident: this used to throw, and Hibernate applies the converter at
		// row-hydration time, so ANY query touching a row with one stale secret failed
		// - including one triggered from an ApplicationRunner, which crashed the whole
		// app at startup, not just one request.
		String encrypted = converter.convertToDatabaseColumn("JBSWY3DPEHPK3PXP");
		byte[] tampered = Base64.getDecoder().decode(encrypted);
		tampered[tampered.length - 1] ^= 0x01;
		String tamperedEncoded = Base64.getEncoder().encodeToString(tampered);

		assertThat(converter.convertToEntityAttribute(tamperedEncoded)).isNull();
	}
}
