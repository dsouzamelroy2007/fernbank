package com.mel.fernbank.ledger.api;

import com.mel.fernbank.ledger.domain.User;
import com.mel.fernbank.ledger.repository.UserRepository;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class CurrentCustomerResolver {

	private final UserRepository userRepository;

	public CurrentCustomerResolver(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	public UUID resolveCustomerId(JwtAuthenticationToken authentication) {
		Jwt jwt = authentication.getToken();
		UUID userId = UUID.fromString(jwt.getSubject());
		User user = userRepository.findById(userId).orElseThrow(NoSuchElementException::new);
		return user.getCustomerId();
	}
}
