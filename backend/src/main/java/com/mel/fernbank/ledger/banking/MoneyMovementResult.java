package com.mel.fernbank.ledger.banking;

import com.mel.fernbank.ledger.domain.Money;
import java.time.Instant;
import java.util.UUID;

public record MoneyMovementResult(UUID transactionId, UUID accountId, Money newBalance, Instant executedAt) {}
