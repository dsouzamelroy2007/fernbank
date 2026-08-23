package com.mel.fernbank.ledger.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public record ScheduleTransferRequest(
		@NotNull UUID sourceAccountId,
		@NotNull UUID destinationAccountId,
		@NotNull @Valid MoneyDto amount,
		String description,
		@Schema(description = "Must be in the future.") @NotNull @Future Instant scheduledFor) {}
