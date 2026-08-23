package com.mel.fernbank.ledger.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record StepUpRequest(@NotBlank String code) {}
