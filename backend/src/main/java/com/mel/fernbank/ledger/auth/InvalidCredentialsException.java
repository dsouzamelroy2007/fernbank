package com.mel.fernbank.ledger.auth;

/**
 * Deliberately generic: thrown for unknown email, wrong password, locked account, and
 * disabled account alike, so the HTTP response never reveals which one it was.
 */
public class InvalidCredentialsException extends RuntimeException {

	public InvalidCredentialsException() {
		super("Invalid credentials");
	}
}
