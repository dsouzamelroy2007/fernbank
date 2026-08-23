package com.mel.fernbank.ledger.api.dto;

import java.time.Instant;
import java.util.UUID;

public record SessionResponse(UUID id, Instant issuedAt, Instant expiresAt) {}
