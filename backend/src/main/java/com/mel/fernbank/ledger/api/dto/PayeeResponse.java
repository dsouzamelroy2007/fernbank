package com.mel.fernbank.ledger.api.dto;

import java.time.Instant;
import java.util.UUID;

public record PayeeResponse(UUID id, String name, String targetAccountNumber, Instant createdAt) {}
