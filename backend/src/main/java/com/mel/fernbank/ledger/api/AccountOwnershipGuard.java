package com.mel.fernbank.ledger.api;

import com.mel.fernbank.ledger.banking.AccountNotFoundException;
import com.mel.fernbank.ledger.domain.Account;
import com.mel.fernbank.ledger.repository.AccountRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Every controller that takes a client-supplied {@code accountId} must resolve the
 * account through this guard, never through {@link AccountRepository} directly.
 * Missing and not-owned both throw the same {@link AccountNotFoundException} (mapped to
 * 404) - cross-customer access must not be distinguishable from "doesn't exist"
 * (see CONTRIBUTING.md).
 */
@Component
public class AccountOwnershipGuard {

	private final AccountRepository accountRepository;

	public AccountOwnershipGuard(AccountRepository accountRepository) {
		this.accountRepository = accountRepository;
	}

	public Account requireOwnedAccount(UUID accountId, UUID customerId) {
		Account account = accountRepository.findById(accountId).orElseThrow(() -> new AccountNotFoundException(accountId));
		if (!account.getCustomerId().equals(customerId)) {
			throw new AccountNotFoundException(accountId);
		}
		return account;
	}
}
