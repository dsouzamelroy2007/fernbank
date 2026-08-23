package com.mel.fernbank.ledger.validation;

import com.mel.fernbank.ledger.api.dto.MoneyCodec;
import com.mel.fernbank.ledger.api.dto.MoneyDto;
import com.mel.fernbank.ledger.domain.Money;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Currency;
import org.springframework.stereotype.Component;

@Component
public class PositiveAmountValidator implements ConstraintValidator<PositiveAmount, MoneyDto> {

	private final MoneyCodec moneyCodec;

	public PositiveAmountValidator(MoneyCodec moneyCodec) {
		this.moneyCodec = moneyCodec;
	}

	@Override
	public boolean isValid(MoneyDto dto, ConstraintValidatorContext context) {
		if (dto == null || dto.amount() == null || dto.currency() == null) {
			return true;
		}
		if (!isKnownCurrency(dto.currency())) {
			// Let @IsoCurrency on the currency field report this instead of duplicating it here.
			return true;
		}
		try {
			Money money = moneyCodec.toDomain(dto);
			return money.minorUnits() > 0;
		} catch (ArithmeticException | IllegalArgumentException e) {
			return false;
		}
	}

	private boolean isKnownCurrency(String currencyCode) {
		try {
			Currency.getInstance(currencyCode);
			return true;
		} catch (IllegalArgumentException e) {
			return false;
		}
	}
}
