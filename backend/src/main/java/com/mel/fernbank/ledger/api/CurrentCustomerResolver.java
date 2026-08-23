package com.mel.fernbank.ledger.api;

import com.mel.fernbank.ledger.domain.User;
import com.mel.fernbank.ledger.repository.UserRepository;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Resolves the authenticated principal's {@code customerId} from the JWT {@code sub}
 * claim (a user id) - the one and only source of identity every resource lookup in this
 * package must use. Never trust a client-supplied {@code customerId}/{@code accountId}
 * to imply access (see CONTRIBUTING.md).
 */
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
