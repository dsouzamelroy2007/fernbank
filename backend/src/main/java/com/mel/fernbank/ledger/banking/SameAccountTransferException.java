package com.mel.fernbank.ledger.banking;

import java.util.UUID;

public final class SameAccountTransferException extends DomainException {

	public SameAccountTransferException(UUID accountId) {
		super("Cannot transfer an account to itself: %s".formatted(accountId));
	}
}
