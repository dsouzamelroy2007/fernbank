package com.mel.fernbank.ledger.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class IsoCurrencyValidatorTest {

	private final IsoCurrencyValidator validator = new IsoCurrencyValidator();

	@Test
	void nullIsValidSoRequirednessIsLeftToNotBlank() {
		assertThat(validator.isValid(null, null)).isTrue();
	}

	@Test
	void acceptsAKnownIsoCurrencyCode() {
		assertThat(validator.isValid("USD", null)).isTrue();
	}

	@Test
	void rejectsAnUnknownCurrencyCode() {
		assertThat(validator.isValid("XXXX", null)).isFalse();
	}
}
