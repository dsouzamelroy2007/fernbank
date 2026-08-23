package com.mel.fernbank.ledger.api.dto;

import com.mel.fernbank.ledger.domain.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import org.springframework.stereotype.Component;

/**
 * Converts between the domain's minor-units {@link Money} and the decimal-string
 * {@link MoneyDto} used over the wire. {@code RoundingMode.UNNECESSARY} in
 * {@link #toDomain} rejects any amount with more fractional digits than the currency
 * allows (e.g. "10.001" for USD) rather than silently rounding it.
 */
@Component
public class MoneyCodec {

	public Money toDomain(MoneyDto dto) {
		int fractionDigits = fractionDigitsFor(dto.currency());
		BigDecimal decimal = parse(dto.amount());
		BigDecimal scaled = decimal.setScale(fractionDigits, RoundingMode.UNNECESSARY);
		long minorUnits = scaled.movePointRight(fractionDigits).longValueExact();
		return Money.of(minorUnits, dto.currency());
	}

	public MoneyDto toDto(Money money) {
		int fractionDigits = fractionDigitsFor(money.currencyCode());
		BigDecimal decimal = BigDecimal.valueOf(money.minorUnits()).movePointLeft(fractionDigits);
		return new MoneyDto(decimal.toPlainString(), money.currencyCode());
	}

	private BigDecimal parse(String amount) {
		try {
			return new BigDecimal(amount);
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("Amount is not a valid decimal number: " + amount, e);
		}
	}

	private int fractionDigitsFor(String currencyCode) {
		return Currency.getInstance(currencyCode).getDefaultFractionDigits();
	}
}
