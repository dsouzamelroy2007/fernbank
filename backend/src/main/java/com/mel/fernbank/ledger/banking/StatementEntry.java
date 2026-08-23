package com.mel.fernbank.ledger.banking;

import com.mel.fernbank.ledger.domain.Money;
import java.time.Instant;
import java.util.UUID;

public record StatementEntry(UUID entryId, UUID transactionId, Instant createdAt, Money amount, String description) {}
