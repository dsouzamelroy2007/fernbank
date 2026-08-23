package com.mel.fernbank.ledger.auth;

public class RateLimitExceededException extends RuntimeException {

	public RateLimitExceededException() {
		super("Too many login attempts, try again later");
	}
}
