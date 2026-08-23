package com.mel.fernbank.ledger.banking;

import com.mel.fernbank.ledger.domain.Money;
import java.time.Instant;
import java.util.UUID;

public record ScheduleTransferCommand(
		UUID sourceAccountId,
		UUID destinationAccountId,
		Money amount,
		String description,
		Instant scheduledFor,
		UUID initiatingUserId) {}
