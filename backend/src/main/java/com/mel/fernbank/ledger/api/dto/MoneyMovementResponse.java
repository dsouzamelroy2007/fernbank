package com.mel.fernbank.ledger.api.dto;

import java.time.Instant;
import java.util.UUID;

public record MoneyMovementResponse(UUID transactionId, UUID accountId, MoneyDto newBalance, Instant executedAt) {}
