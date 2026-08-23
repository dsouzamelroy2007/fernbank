package com.mel.fernbank.ledger.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record MfaVerifyRequest(@NotBlank String mfaToken, @NotBlank String code) {}
