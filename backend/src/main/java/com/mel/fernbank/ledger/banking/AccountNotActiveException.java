package com.mel.fernbank.ledger.banking;

import com.mel.fernbank.ledger.domain.AccountStatus;
import java.util.UUID;

public final class AccountNotActiveException extends DomainException {

	public AccountNotActiveException(UUID accountId, AccountStatus status) {
		super("Account %s is %s and cannot be debited".formatted(accountId, status));
	}
}
