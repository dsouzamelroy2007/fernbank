package com.mel.fernbank.ledger.auth;

public class InvalidRefreshTokenException extends RuntimeException {

	public InvalidRefreshTokenException() {
		super("Invalid or expired refresh token");
	}
}
