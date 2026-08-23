package com.mel.fernbank.ledger.auth.dto;

import java.util.List;

public record MfaEnrollConfirmResponse(List<String> recoveryCodes) {}
