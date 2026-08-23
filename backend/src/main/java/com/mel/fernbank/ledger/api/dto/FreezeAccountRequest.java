package com.mel.fernbank.ledger.api.dto;

import jakarta.validation.constraints.NotBlank;

public record FreezeAccountRequest(@NotBlank String reason) {}
