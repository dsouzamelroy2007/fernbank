package com.mel.fernbank.ledger.banking;

import java.time.Instant;
import java.util.UUID;

public record StatementQuery(UUID accountId, Instant from, Instant to, String cursor, int pageSize) {}
