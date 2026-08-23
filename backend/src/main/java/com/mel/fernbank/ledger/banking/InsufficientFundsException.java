package com.mel.fernbank.ledger.banking;

import java.util.UUID;

public final class InsufficientFundsException extends DomainException {

	public InsufficientFundsException(UUID accountId) {
		super("Account %s has insufficient funds for this debit".formatted(accountId));
	}
}
