package com.mel.fernbank.ledger.auth;

/** Thrown by {@link AuthenticationService#register} when the email is already taken. */
public class EmailAlreadyRegisteredException extends RuntimeException {

	public EmailAlreadyRegisteredException() {
		super("Email is already registered");
	}
}
