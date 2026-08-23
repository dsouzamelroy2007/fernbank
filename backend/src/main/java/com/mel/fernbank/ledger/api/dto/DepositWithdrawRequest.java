package com.mel.fernbank.ledger.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record DepositWithdrawRequest(@NotNull @Valid MoneyDto amount, String description) {}
