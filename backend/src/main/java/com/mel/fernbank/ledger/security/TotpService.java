package com.mel.fernbank.ledger.security;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.apache.commons.codec.binary.Base32;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriUtils;

/**
 * RFC 6238 TOTP (HMAC-SHA1, 6 digits, 30s step), hand-rolled rather than pulling in a
 * library whose main value is QR *image* generation we don't need (see ADR/plan notes)
 * — {@link #hotp} is verified against the RFC 6238 Appendix B test vectors in
 * {@code TotpServiceTest}.
 */
@Service
public class TotpService {

	private static final int TIME_STEP_SECONDS = 30;
	private static final int CODE_DIGITS = 6;
	private static final int SECRET_BYTES = 20;
	private static final int ALLOWED_STEP_DRIFT = 1;
	private static final String HMAC_ALGORITHM = "HmacSHA1";

	private final SecureRandom secureRandom = new SecureRandom();
	private final Base32 base32 = new Base32();

	public String generateSecret() {
		byte[] bytes = new byte[SECRET_BYTES];
		secureRandom.nextBytes(bytes);
		return base32.encodeToString(bytes).replace("=", "");
	}

	public String otpAuthUri(String secret, String accountEmail, String issuer) {
		return "otpauth://totp/%s:%s?secret=%s&issuer=%s&algorithm=SHA1&digits=%d&period=%d"
				.formatted(
						UriUtils.encode(issuer, StandardCharsets.UTF_8),
						UriUtils.encode(accountEmail, StandardCharsets.UTF_8),
						secret,
						UriUtils.encode(issuer, StandardCharsets.UTF_8),
						CODE_DIGITS,
						TIME_STEP_SECONDS);
	}

	public boolean verifyCode(String base32Secret, String code) {
		return verifyCode(base32Secret, code, Instant.now());
	}

	boolean verifyCode(String base32Secret, String code, Instant at) {
		if (code == null || code.length() != CODE_DIGITS) {
			return false;
		}
		byte[] key = base32.decode(base32Secret);
		long currentStep = at.getEpochSecond() / TIME_STEP_SECONDS;
		for (long step = currentStep - ALLOWED_STEP_DRIFT; step <= currentStep + ALLOWED_STEP_DRIFT; step++) {
			if (constantTimeEquals(hotp(key, step, CODE_DIGITS), code)) {
				return true;
			}
		}
		return false;
	}

	public String currentCode(String base32Secret, Instant at) {
		byte[] key = base32.decode(base32Secret);
		return hotp(key, at.getEpochSecond() / TIME_STEP_SECONDS, CODE_DIGITS);
	}

	static String hotp(byte[] key, long counter, int digits) {
		try {
			byte[] counterBytes = ByteBuffer.allocate(8).putLong(counter).array();
			Mac mac = Mac.getInstance(HMAC_ALGORITHM);
			mac.init(new SecretKeySpec(key, HMAC_ALGORITHM));
			byte[] hash = mac.doFinal(counterBytes);

			int offset = hash[hash.length - 1] & 0x0F;
			int binary = ((hash[offset] & 0x7f) << 24)
					| ((hash[offset + 1] & 0xff) << 16)
					| ((hash[offset + 2] & 0xff) << 8)
					| (hash[offset + 3] & 0xff);

			int otp = (int) (binary % (long) Math.pow(10, digits));
			return String.format(Locale.ROOT, "%0" + digits + "d", otp);
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException("Failed to compute HOTP", e);
		}
	}

	private static boolean constantTimeEquals(String a, String b) {
		return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
	}
}
