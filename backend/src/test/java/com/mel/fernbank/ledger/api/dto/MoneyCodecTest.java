package com.mel.fernbank.ledger.api.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mel.fernbank.ledger.domain.Money;
import org.junit.jupiter.api.Test;

class MoneyCodecTest {

	private final MoneyCodec codec = new MoneyCodec();

	@Test
	void convertsADecimalStringToMinorUnits() {
		Money money = codec.toDomain(new MoneyDto("125.50", "USD"));

		assertThat(money).isEqualTo(Money.of(12_550, "USD"));
	}

	@Test
	void convertsMinorUnitsBackToADecimalString() {
		MoneyDto dto = codec.toDto(Money.of(12_550, "USD"));

		assertThat(dto.amount()).isEqualTo("125.50");
		assertThat(dto.currency()).isEqualTo("USD");
	}

	@Test
	void padsAWholeAmountToTheCurrencysFractionDigits() {
		Money money = codec.toDomain(new MoneyDto("125", "USD"));

		assertThat(money).isEqualTo(Money.of(12_500, "USD"));
	}

	@Test
	void rejectsMorePrecisionThanTheCurrencyAllows() {
		assertThatThrownBy(() -> codec.toDomain(new MoneyDto("10.001", "USD")))
				.isInstanceOf(ArithmeticException.class);
	}

	@Test
	void rejectsANonNumericAmount() {
		assertThatThrownBy(() -> codec.toDomain(new MoneyDto("not-a-number", "USD")))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void roundTripsThroughDtoAndBackToTheSameMoney() {
		Money original = Money.of(999_999, "EUR");

		Money roundTripped = codec.toDomain(codec.toDto(original));

		assertThat(roundTripped).isEqualTo(original);
	}
}
