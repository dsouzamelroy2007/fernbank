package com.mel.fernbank.ledger.api;

import com.mel.fernbank.ledger.domain.Customer;
import com.mel.fernbank.ledger.domain.User;
import com.mel.fernbank.ledger.repository.CustomerRepository;
import com.mel.fernbank.ledger.repository.UserRepository;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves the caller's own profile from the authenticated principal's id — never from
 * a client-supplied id. The pattern every future resource lookup (Phase 4+) must follow.
 */
@Service
public class ProfileService {

	private final UserRepository userRepository;
	private final CustomerRepository customerRepository;

	public ProfileService(UserRepository userRepository, CustomerRepository customerRepository) {
		this.userRepository = userRepository;
		this.customerRepository = customerRepository;
	}

	@Transactional(readOnly = true)
	public MeResponse getOwnProfile(UUID userId) {
		User user = userRepository.findById(userId).orElseThrow(NoSuchElementException::new);
		Customer customer = customerRepository.findById(user.getCustomerId()).orElseThrow(NoSuchElementException::new);
		return new MeResponse(user.getId(), customer.getId(), user.getEmail(), customer.getFullName());
	}
}
