package com.mel.fernbank.ledger.security;

import java.util.HashMap;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class PasswordEncoderConfig {

	private static final String DEFAULT_ENCODER_ID = "argon2";
	private static final int BCRYPT_STRENGTH = 12;

	@Bean
	public PasswordEncoder passwordEncoder() {
		Map<String, PasswordEncoder> encoders = new HashMap<>();
		encoders.put(DEFAULT_ENCODER_ID, Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8());
		encoders.put("bcrypt", new BCryptPasswordEncoder(BCRYPT_STRENGTH));
		return new DelegatingPasswordEncoder(DEFAULT_ENCODER_ID, encoders);
	}
}
