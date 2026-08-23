package com.mel.fernbank.ledger.banking;

import com.mel.fernbank.ledger.domain.Money;
import java.time.Instant;
import java.util.UUID;

public record TransferResult(
		UUID transactionId,
		UUID sourceAccountId,
		Money sourceNewBalance,
		UUID destinationAccountId,
		Money destinationNewBalance,
		Instant executedAt) {}
