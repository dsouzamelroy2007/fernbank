package com.mel.fernbank.ledger.api.dto;

import com.mel.fernbank.ledger.domain.ScheduledTransferStatus;
import java.time.Instant;
import java.util.UUID;

public record ScheduledTransferResponse(
		UUID id,
		UUID sourceAccountId,
		UUID destinationAccountId,
		MoneyDto amount,
		String description,
		Instant scheduledFor,
		ScheduledTransferStatus status) {}
