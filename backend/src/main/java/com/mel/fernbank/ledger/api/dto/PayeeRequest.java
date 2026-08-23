package com.mel.fernbank.ledger.api.dto;

import com.mel.fernbank.ledger.validation.Iban;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record PayeeRequest(
		@NotBlank String name,
		@Schema(description = "IBAN-shaped account number.", example = "FB1234567890123456789012") @NotBlank @Iban
				String targetAccountNumber) {}
