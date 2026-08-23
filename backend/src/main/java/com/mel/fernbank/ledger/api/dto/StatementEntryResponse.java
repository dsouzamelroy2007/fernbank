package com.mel.fernbank.ledger.api.dto;

import java.time.Instant;
import java.util.UUID;

public record StatementEntryResponse(UUID id, UUID transactionId, Instant createdAt, MoneyDto amount, String description) {}
