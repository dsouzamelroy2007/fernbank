package com.mel.fernbank.ledger.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.Currency;
import java.util.Objects;

@Embeddable
public class Money {

	@Column(name = "amount_minor_units", nullable = false)
	private long minorUnits;

	@Column(name = "currency", nullable = false, length = 3)
	private String currencyCode;

	protected Money() {
	}

	private Money(long minorUnits, String currencyCode) {
		this.minorUnits = minorUnits;
		this.currencyCode = validateCurrency(currencyCode);
	}

	public static Money of(long minorUnits, String currencyCode) {
		return new Money(minorUnits, currencyCode);
	}

	public static Money zero(String currencyCode) {
		return new Money(0L, currencyCode);
	}

	private static String validateCurrency(String currencyCode) {
		Objects.requireNonNull(currencyCode, "currencyCode must not be null");
		try {
			return Currency.getInstance(currencyCode).getCurrencyCode();
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("Invalid ISO-4217 currency code: " + currencyCode, e);
		}
	}

	public Money add(Money other) {
		requireSameCurrency(other);
		return new Money(Math.addExact(this.minorUnits, other.minorUnits), this.currencyCode);
	}

	public Money subtract(Money other) {
		requireSameCurrency(other);
		return new Money(Math.subtractExact(this.minorUnits, other.minorUnits), this.currencyCode);
	}

	public Money negate() {
		return new Money(Math.negateExact(this.minorUnits), this.currencyCode);
	}

	private void requireSameCurrency(Money other) {
		Objects.requireNonNull(other, "other must not be null");
		if (!this.currencyCode.equals(other.currencyCode)) {
			throw new IllegalArgumentException(
					"Currency mismatch: %s vs %s".formatted(this.currencyCode, other.currencyCode));
		}
	}

	public long minorUnits() {
		return minorUnits;
	}

	public String currencyCode() {
		return currencyCode;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof Money money)) {
			return false;
		}
		return minorUnits == money.minorUnits && currencyCode.equals(money.currencyCode);
	}

	@Override
	public int hashCode() {
		return Objects.hash(minorUnits, currencyCode);
	}

	@Override
	public String toString() {
		return "%d %s".formatted(minorUnits, currencyCode);
	}
}
