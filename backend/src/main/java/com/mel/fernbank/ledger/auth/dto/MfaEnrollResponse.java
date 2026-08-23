package com.mel.fernbank.ledger.auth.dto;

public record MfaEnrollResponse(String secret, String otpAuthUri) {}
