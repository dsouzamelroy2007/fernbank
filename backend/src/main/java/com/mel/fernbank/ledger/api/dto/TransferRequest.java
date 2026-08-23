package com.mel.fernbank.ledger.api.dto;

import com.mel.fernbank.ledger.validation.Iban;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Exactly one of {@code destinationAccountId}/{@code destinationAccountNumber} must be
 * present - checked in {@code TransferController}, not here, since a Bean Validation
 * class-level constraint would be overkill for a single XOR check.
 */
@Schema(
		description = "Exactly one of destinationAccountId or destinationAccountNumber must be "
				+ "provided, never both or neither - enforced at request-handling time, not by field "
				+ "validation, and rejected with a 400 bad-request if violated.")
public record TransferRequest(
		@NotNull UUID sourceAccountId,
		@Schema(description = "Destination by account id - mutually exclusive with destinationAccountNumber.")
				UUID destinationAccountId,
		@Schema(description = "Destination by account number - mutually exclusive with destinationAccountId.")
				@Iban
				String destinationAccountNumber,
		@NotNull @Valid MoneyDto amount,
		String description) {}
