package com.mel.fernbank.ledger.security;

public class StepUpRequiredException extends RuntimeException {

	public StepUpRequiredException() {
		super("This action requires a recent step-up authentication");
	}
}
