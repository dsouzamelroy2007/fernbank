package com.mel.fernbank.ledger.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Currency;

public class IsoCurrencyValidator implements ConstraintValidator<IsoCurrency, String> {

	@Override
	public boolean isValid(String value, ConstraintValidatorContext context) {
		if (value == null) {
			return true;
		}
		try {
			Currency.getInstance(value);
			return true;
		} catch (IllegalArgumentException e) {
			return false;
		}
	}
}
