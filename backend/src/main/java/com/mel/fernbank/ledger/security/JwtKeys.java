package com.mel.fernbank.ledger.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.RSAKey;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Holds the RSA keypair used to sign and verify this app's JWTs. Loaded from
 * {@code JWT_PRIVATE_KEY}/{@code JWT_PUBLIC_KEY} PEM env vars if set; otherwise an
 * ephemeral 2048-bit keypair is generated at startup — fine for dev/test, but tokens
 * won't survive a restart.
 */
@Component
public class JwtKeys {

	private static final Logger log = LoggerFactory.getLogger(JwtKeys.class);

	private final RSAKey rsaKey;

	public JwtKeys(FernbankProperties properties) {
		FernbankProperties.Jwt jwt = properties.jwt();
		if (jwt.privateKeyPem() != null
				&& !jwt.privateKeyPem().isBlank()
				&& jwt.publicKeyPem() != null
				&& !jwt.publicKeyPem().isBlank()) {
			this.rsaKey = finalizeWithThumbprintKid(
					parsePublicKey(jwt.publicKeyPem()), parsePrivateKey(jwt.privateKeyPem()));
		} else {
			log.warn(
					"JWT_PRIVATE_KEY/JWT_PUBLIC_KEY not set - generating an ephemeral RSA keypair. "
							+ "Tokens issued this run will be invalid after a restart.");
			this.rsaKey = finalizeWithThumbprintKid(generateEphemeralKeyPair());
		}
	}

	public RSAKey rsaKey() {
		return rsaKey;
	}

	private static KeyPair generateEphemeralKeyPair() {
		try {
			KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
			generator.initialize(2048);
			return generator.generateKeyPair();
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("RSA key generation is not available", e);
		}
	}

	private static RSAKey finalizeWithThumbprintKid(KeyPair keyPair) {
		return finalizeWithThumbprintKid((RSAPublicKey) keyPair.getPublic(), (RSAPrivateKey) keyPair.getPrivate());
	}

	private static RSAKey finalizeWithThumbprintKid(RSAPublicKey publicKey, RSAPrivateKey privateKey) {
		try {
			RSAKey withoutKid =
					new RSAKey.Builder(publicKey).privateKey(privateKey).build();
			String kid = withoutKid.computeThumbprint().toString();
			return new RSAKey.Builder(withoutKid).keyID(kid).build();
		} catch (JOSEException e) {
			throw new IllegalStateException("Failed to compute JWK thumbprint", e);
		}
	}

	private static RSAPrivateKey parsePrivateKey(String pem) {
		try {
			byte[] der = Base64.getDecoder().decode(stripPemHeaders(pem));
			KeyFactory keyFactory = KeyFactory.getInstance("RSA");
			return (RSAPrivateKey) keyFactory.generatePrivate(new PKCS8EncodedKeySpec(der));
		} catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
			throw new IllegalStateException("Invalid JWT_PRIVATE_KEY", e);
		}
	}

	private static RSAPublicKey parsePublicKey(String pem) {
		try {
			byte[] der = Base64.getDecoder().decode(stripPemHeaders(pem));
			KeyFactory keyFactory = KeyFactory.getInstance("RSA");
			return (RSAPublicKey) keyFactory.generatePublic(new X509EncodedKeySpec(der));
		} catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
			throw new IllegalStateException("Invalid JWT_PUBLIC_KEY", e);
		}
	}

	private static String stripPemHeaders(String pem) {
		return pem.replaceAll("-----(BEGIN|END)[A-Z ]*-----", "").replaceAll("\\s", "");
	}
}
