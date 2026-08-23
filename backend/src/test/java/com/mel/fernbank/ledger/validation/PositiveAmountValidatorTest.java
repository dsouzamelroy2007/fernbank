package com.mel.fernbank.ledger.validation;

import static org.assertj.core.api.Assertions.assertThat;

import com.mel.fernbank.ledger.api.dto.MoneyCodec;
import com.mel.fernbank.ledger.api.dto.MoneyDto;
import org.junit.jupiter.api.Test;

class PositiveAmountValidatorTest {

	private final PositiveAmountValidator validator = new PositiveAmountValidator(new MoneyCodec());

	@Test
	void acceptsAPositiveAmount() {
		assertThat(validator.isValid(new MoneyDto("10.00", "USD"), null)).isTrue();
	}

	@Test
	void rejectsZero() {
		assertThat(validator.isValid(new MoneyDto("0.00", "USD"), null)).isFalse();
	}

	@Test
	void rejectsANegativeAmount() {
		assertThat(validator.isValid(new MoneyDto("-5.00", "USD"), null)).isFalse();
	}

	@Test
	void rejectsMorePrecisionThanTheCurrencyAllows() {
		assertThat(validator.isValid(new MoneyDto("10.001", "USD"), null)).isFalse();
	}

	@Test
	void leavesAnUnknownCurrencyToTheIsoCurrencyValidator() {
		assertThat(validator.isValid(new MoneyDto("10.00", "XXXX"), null)).isTrue();
	}

	@Test
	void nonNumericAmountIsInvalid() {
		assertThat(validator.isValid(new MoneyDto("not-a-number", "USD"), null)).isFalse();
	}
}
