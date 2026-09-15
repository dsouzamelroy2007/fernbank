package com.mel.fernbank.ledger.banking;

public abstract sealed class DomainException extends RuntimeException
		permits AccountNotFoundException,
				AccountNotActiveException,
				CurrencyMismatchException,
				InsufficientFundsException,
				InvalidAmountException,
				SameAccountTransferException {

	protected DomainException(String message) {
		super(message);
	}
}
