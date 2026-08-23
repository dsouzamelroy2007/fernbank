package com.mel.fernbank.ledger.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MoneyTest {

	@Test
	void addsAmountsOfTheSameCurrency() {
		Money sum = Money.of(1_000, "USD").add(Money.of(250, "USD"));

		assertThat(sum).isEqualTo(Money.of(1_250, "USD"));
	}

	@Test
	void subtractsAmountsOfTheSameCurrency() {
		Money difference = Money.of(1_000, "USD").subtract(Money.of(250, "USD"));

		assertThat(difference).isEqualTo(Money.of(750, "USD"));
	}

	@Test
	void negatesAnAmount() {
		assertThat(Money.of(500, "USD").negate()).isEqualTo(Money.of(-500, "USD"));
	}

	@Test
	void zeroCreatesAZeroAmountInTheGivenCurrency() {
		assertThat(Money.zero("EUR")).isEqualTo(Money.of(0, "EUR"));
	}

	@Test
	void addRejectsMismatchedCurrencies() {
		Money usd = Money.of(100, "USD");
		Money eur = Money.of(100, "EUR");

		assertThatThrownBy(() -> usd.add(eur)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void subtractRejectsMismatchedCurrencies() {
		Money usd = Money.of(100, "USD");
		Money eur = Money.of(100, "EUR");

		assertThatThrownBy(() -> usd.subtract(eur)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void ofRejectsAnInvalidCurrencyCode() {
		assertThatThrownBy(() -> Money.of(100, "XXXX")).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void addOverflowsExactlyRatherThanWrapping() {
		Money max = Money.of(Long.MAX_VALUE, "USD");

		assertThatThrownBy(() -> max.add(Money.of(1, "USD"))).isInstanceOf(ArithmeticException.class);
	}

	@Test
	void equalAmountsAreEqualAndHaveTheSameHashCode() {
		Money a = Money.of(1_500, "GBP");
		Money b = Money.of(1_500, "GBP");

		assertThat(a).isEqualTo(b);
		assertThat(a.hashCode()).isEqualTo(b.hashCode());
	}
}
