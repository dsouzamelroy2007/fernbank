package com.mel.fernbank.ledger.api;

import com.mel.fernbank.ledger.banking.AccountNotFoundException;
import com.mel.fernbank.ledger.domain.Account;
import com.mel.fernbank.ledger.repository.AccountRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;

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
