package com.mel.fernbank.ledger.auth;

public class WrongPasswordException extends RuntimeException {

	public WrongPasswordException() {
		super("Current password is incorrect");
	}
}
