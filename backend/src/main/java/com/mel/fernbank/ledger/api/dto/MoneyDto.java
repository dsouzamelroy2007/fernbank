package com.mel.fernbank.ledger.api.dto;

import com.mel.fernbank.ledger.validation.IsoCurrency;
import com.mel.fernbank.ledger.validation.PositiveAmount;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * Money as it crosses HTTP: a decimal string, never a raw number that could round-trip
 * through JS floating point (CONTRIBUTING.md non-negotiable). Converted to/from the domain
 * {@code Money} value object by {@link MoneyCodec}.
 */
@PositiveAmount
public record MoneyDto(
		@Schema(
						description =
								"Decimal string, using the currency's own minor-unit precision (e.g. 2 places "
										+ "for USD) - must be strictly positive.",
						example = "125.50")
				@NotBlank
				String amount,
		@Schema(description = "ISO-4217 currency code.", example = "USD") @NotBlank @IsoCurrency String currency) {}
