package com.mel.fernbank.ledger.security;

/**
 * Thrown when an action requires a recent MFA elevation (see {@link StepUpAuthService})
 * and the caller's token doesn't carry one. Phase 3 will map this to an HTTP response
 * once a transfer endpoint actually throws it.
 */
public class StepUpRequiredException extends RuntimeException {

	public StepUpRequiredException() {
		super("This action requires a recent step-up authentication");
	}
}
