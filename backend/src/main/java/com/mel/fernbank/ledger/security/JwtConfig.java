package com.mel.fernbank.ledger.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.security.interfaces.RSAPublicKey;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@Configuration
public class JwtConfig {

	@Bean
	public NimbusJwtEncoder jwtEncoder(JwtKeys jwtKeys) {
		JWKSet jwkSet = new JWKSet(jwtKeys.rsaKey());
		return new NimbusJwtEncoder(new ImmutableJWKSet<>(jwkSet));
	}

	@Bean
	public JwtDecoder jwtDecoder(JwtKeys jwtKeys, FernbankProperties properties) {
		try {
			RSAPublicKey publicKey = jwtKeys.rsaKey().toRSAPublicKey();
			NimbusJwtDecoder decoder =
					NimbusJwtDecoder.withPublicKey(publicKey).build();
			decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.jwt().issuer()));
			return decoder;
		} catch (JOSEException e) {
			throw new IllegalStateException("Failed to derive RSA public key for JWT decoding", e);
		}
	}

	/** Public JWK set (private key material stripped) for {@code GET /oauth2/jwks}. */
	@Bean
	public List<JWK> publicJwks(JwtKeys jwtKeys) {
		return new JWKSet(jwtKeys.rsaKey()).toPublicJWKSet().getKeys();
	}
}
