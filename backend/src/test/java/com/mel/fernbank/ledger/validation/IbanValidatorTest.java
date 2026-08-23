package com.mel.fernbank.ledger.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.mel.fernbank.ledger.banking.AccountNumberGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IbanValidatorTest {

	@Mock
	private AccountNumberGenerator accountNumberGenerator;

	private IbanValidator validator;

	@BeforeEach
	void setUp() {
		validator = new IbanValidator(accountNumberGenerator);
	}

	@Test
	void nullIsValidSoOptionalFieldsCanStayUnset() {
		assertThat(validator.isValid(null, null)).isTrue();
	}

	@Test
	void delegatesNonNullValuesToTheChecksumAlgorithm() {
		when(accountNumberGenerator.isValidChecksum("FB1234567890123456789012")).thenReturn(true);

		assertThat(validator.isValid("FB1234567890123456789012", null)).isTrue();
	}

	@Test
	void rejectsAnAccountNumberWithABadChecksum() {
		when(accountNumberGenerator.isValidChecksum("FB0000000000000000000000")).thenReturn(false);

		assertThat(validator.isValid("FB0000000000000000000000", null)).isFalse();
	}
}
