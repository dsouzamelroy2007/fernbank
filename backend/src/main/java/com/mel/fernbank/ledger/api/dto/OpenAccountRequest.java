package com.mel.fernbank.ledger.api.dto;

import com.mel.fernbank.ledger.domain.AccountType;
import com.mel.fernbank.ledger.validation.IsoCurrency;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record OpenAccountRequest(@NotNull AccountType type, @NotBlank @IsoCurrency String currency) {}
