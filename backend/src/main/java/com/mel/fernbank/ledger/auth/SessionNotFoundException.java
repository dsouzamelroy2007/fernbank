package com.mel.fernbank.ledger.auth;

/**
 * Thrown when a session id doesn't exist or belongs to another user - both cases are
 * indistinguishable, same no-existence-leak convention as
 * {@code api.AccountOwnershipGuard} (see CONTRIBUTING.md).
 */
public class SessionNotFoundException extends RuntimeException {

	public SessionNotFoundException() {
		super("Session not found");
	}
}
