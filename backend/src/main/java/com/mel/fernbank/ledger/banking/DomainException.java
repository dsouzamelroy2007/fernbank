package com.mel.fernbank.ledger.banking;

/**
 * Sealed hierarchy of business-rule violations for money movement. Not mapped to HTTP
 * anywhere in this phase — Phase 4 adds a {@code @RestControllerAdvice} translating
 * these to RFC 9457 Problem Details. Idempotency conflicts are a separate, cross-cutting
 * concern (not banking-specific — see {@code idempotency.IdempotencyConflictException}),
 * so deliberately not part of this closed set.
 */
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
