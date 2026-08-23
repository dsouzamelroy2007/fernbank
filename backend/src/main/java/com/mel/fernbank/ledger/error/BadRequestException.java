package com.mel.fernbank.ledger.error;

/** A generic 400 for client-input problems that don't fit Bean Validation, e.g. an XOR
 * between two request fields or an account number that doesn't resolve to any account. */
public class BadRequestException extends RuntimeException {

	public BadRequestException(String message) {
		super(message);
	}
}
