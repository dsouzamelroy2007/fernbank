package com.mel.fernbank.ledger.banking;

import com.mel.fernbank.ledger.domain.Money;
import java.util.UUID;

public record TransferCommand(
		UUID sourceAccountId,
		UUID destinationAccountId,
		Money amount,
		String description,
		UUID initiatingUserId,
		String idempotencyKey,
		boolean stepUpVerified) {}
