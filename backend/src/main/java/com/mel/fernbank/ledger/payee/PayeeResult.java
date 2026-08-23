package com.mel.fernbank.ledger.payee;

import java.time.Instant;
import java.util.UUID;

public record PayeeResult(UUID id, String name, String targetAccountNumber, Instant createdAt) {}
