package com.mel.fernbank.ledger.auth.dto;

public record LoginResponse(Status status, String accessToken, String refreshToken, String mfaToken) {

	public enum Status {
		AUTHENTICATED,
		MFA_REQUIRED
	}

	public static LoginResponse authenticated(String accessToken, String refreshToken) {
		return new LoginResponse(Status.AUTHENTICATED, accessToken, refreshToken, null);
	}

	public static LoginResponse mfaRequired(String mfaToken) {
		return new LoginResponse(Status.MFA_REQUIRED, null, null, mfaToken);
	}
}
