package com.mel.fernbank.ledger.validation;

import com.mel.fernbank.ledger.banking.AccountNumberGenerator;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.stereotype.Component;

@Component
public class IbanValidator implements ConstraintValidator<Iban, String> {

	private final AccountNumberGenerator accountNumberGenerator;

	public IbanValidator(AccountNumberGenerator accountNumberGenerator) {
		this.accountNumberGenerator = accountNumberGenerator;
	}

	@Override
	public boolean isValid(String value, ConstraintValidatorContext context) {
		return value == null || accountNumberGenerator.isValidChecksum(value);
	}
}
