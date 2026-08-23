package com.mel.fernbank.ledger.auth;

public class InvalidMfaChallengeException extends RuntimeException {

	public InvalidMfaChallengeException() {
		super("Invalid or expired MFA challenge, or wrong code");
	}
}
