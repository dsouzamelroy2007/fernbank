package com.mel.fernbank.ledger.auth;

public class SessionNotFoundException extends RuntimeException {

	public SessionNotFoundException() {
		super("Session not found");
	}
}
