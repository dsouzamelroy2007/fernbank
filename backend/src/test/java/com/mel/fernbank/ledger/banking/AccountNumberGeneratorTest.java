package com.mel.fernbank.ledger.banking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.mel.fernbank.ledger.domain.Account;
import com.mel.fernbank.ledger.repository.AccountRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountNumberGeneratorTest {

	@Mock
	private AccountRepository accountRepository;

	@Test
	void generatesAnAccountNumberWithAValidChecksum() {
		when(accountRepository.findByAccountNumber(anyString())).thenReturn(Optional.empty());
		AccountNumberGenerator generator = new AccountNumberGenerator(accountRepository);

		String accountNumber = generator.generate();

		assertThat(accountNumber).hasSize(20).startsWith("FB");
		assertThat(generator.isValidChecksum(accountNumber)).isTrue();
	}

	@Test
	void rejectsATamperedAccountNumber() {
		when(accountRepository.findByAccountNumber(anyString())).thenReturn(Optional.empty());
		AccountNumberGenerator generator = new AccountNumberGenerator(accountRepository);
		String accountNumber = generator.generate();
		char lastDigit = accountNumber.charAt(accountNumber.length() - 1);
		char flipped = lastDigit == '0' ? '1' : '0';
		String tampered = accountNumber.substring(0, accountNumber.length() - 1) + flipped;

		assertThat(generator.isValidChecksum(tampered)).isFalse();
	}

	@Test
	void retriesOnCollisionAndEventuallyGivesUp() {
		when(accountRepository.findByAccountNumber(anyString()))
				.thenReturn(Optional.of(new Account(null, "collides", null, "USD")));
		AccountNumberGenerator generator = new AccountNumberGenerator(accountRepository);

		assertThatThrownBy(generator::generate).isInstanceOf(IllegalStateException.class);
	}
}
