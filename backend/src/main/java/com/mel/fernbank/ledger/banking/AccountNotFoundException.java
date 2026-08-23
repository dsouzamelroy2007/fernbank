package com.mel.fernbank.ledger.banking;

import java.util.UUID;

public final class AccountNotFoundException extends DomainException {

	public AccountNotFoundException(UUID accountId) {
		super("Account not found: " + accountId);
	}
}
