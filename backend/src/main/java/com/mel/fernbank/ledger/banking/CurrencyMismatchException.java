package com.mel.fernbank.ledger.banking;

public final class CurrencyMismatchException extends DomainException {

	public CurrencyMismatchException(String sourceCurrency, String destinationCurrency) {
		super("Currency mismatch: %s vs %s (same-currency only for v1)".formatted(sourceCurrency, destinationCurrency));
	}
}
