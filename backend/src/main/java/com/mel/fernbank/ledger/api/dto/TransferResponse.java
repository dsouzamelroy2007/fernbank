package com.mel.fernbank.ledger.api.dto;

import java.time.Instant;
import java.util.UUID;

public record TransferResponse(
		UUID transactionId,
		UUID sourceAccountId,
		MoneyDto sourceNewBalance,
		UUID destinationAccountId,
		MoneyDto destinationNewBalance,
		Instant executedAt) {}
