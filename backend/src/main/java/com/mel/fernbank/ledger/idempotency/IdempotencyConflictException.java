package com.mel.fernbank.ledger.idempotency;

/**
 * Thrown when an Idempotency-Key is reused with a different request body. Deliberately
 * not part of any one feature's sealed domain-error hierarchy - {@link IdempotencyGuard}
 * is a cross-cutting concern reusable by any write operation, not just banking's.
 */
public class IdempotencyConflictException extends RuntimeException {

	public IdempotencyConflictException(String idempotencyKey) {
		super("Idempotency-Key '%s' was already used with a different request body".formatted(idempotencyKey));
	}
}
