package com.mel.fernbank.ledger.banking;

public final class InvalidAmountException extends DomainException {

	public InvalidAmountException(long minorUnits) {
		super("Amount must be positive, got: " + minorUnits);
	}
}
