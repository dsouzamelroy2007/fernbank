package com.mel.fernbank.ledger.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Converter
public class MfaSecretConverter implements AttributeConverter<String, String> {

	private static final Logger log = LoggerFactory.getLogger(MfaSecretConverter.class);
	private static final String ALGORITHM = "AES/GCM/NoPadding";
	private static final int GCM_TAG_LENGTH_BITS = 128;
	private static final int GCM_IV_LENGTH_BYTES = 12;

	private static final SecretKeySpec KEY = loadOrGenerateKey();
	private static final SecureRandom RANDOM = new SecureRandom();

	@Override
	public String convertToDatabaseColumn(String attribute) {
		if (attribute == null) {
			return null;
		}
		try {
			byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
			RANDOM.nextBytes(iv);
			Cipher cipher = Cipher.getInstance(ALGORITHM);
			cipher.init(Cipher.ENCRYPT_MODE, KEY, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
			byte[] ciphertext = cipher.doFinal(attribute.getBytes(StandardCharsets.UTF_8));
			byte[] combined = new byte[iv.length + ciphertext.length];
			System.arraycopy(iv, 0, combined, 0, iv.length);
			System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
			return Base64.getEncoder().encodeToString(combined);
		} catch (Exception e) {
			throw new IllegalStateException("Failed to encrypt MFA secret", e);
		}
	}

	@Override
	public String convertToEntityAttribute(String dbData) {
		if (dbData == null) {
			return null;
		}
		try {
			byte[] combined = Base64.getDecoder().decode(dbData);
			byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
			System.arraycopy(combined, 0, iv, 0, iv.length);
			Cipher cipher = Cipher.getInstance(ALGORITHM);
			cipher.init(Cipher.DECRYPT_MODE, KEY, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
			byte[] plaintext = cipher.doFinal(combined, iv.length, combined.length - iv.length);
			return new String(plaintext, StandardCharsets.UTF_8);
		} catch (Exception e) {
			log.warn("Failed to decrypt an MFA secret - treating as absent (likely an encryption-key rotation)", e);
			return null;
		}
	}

	private static SecretKeySpec loadOrGenerateKey() {
		String encoded = System.getenv("MFA_SECRET_ENCRYPTION_KEY");
		byte[] keyBytes;
		if (encoded == null || encoded.isBlank()) {
			keyBytes = new byte[32];
			new SecureRandom().nextBytes(keyBytes);
			System.err.println(
					"WARNING: MFA_SECRET_ENCRYPTION_KEY not set — generated an ephemeral key. "
							+ "MFA secrets encrypted this run will not decrypt after a restart.");
		} else {
			keyBytes = Base64.getDecoder().decode(encoded);
		}
		return new SecretKeySpec(keyBytes, "AES");
	}
}
