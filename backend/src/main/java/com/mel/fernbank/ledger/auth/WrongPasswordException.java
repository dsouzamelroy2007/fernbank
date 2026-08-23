package com.mel.fernbank.ledger.auth;

/** Thrown by {@link AuthenticationService#changePassword} when currentPassword doesn't match. */
public class WrongPasswordException extends RuntimeException {

	public WrongPasswordException() {
		super("Current password is incorrect");
	}
}
