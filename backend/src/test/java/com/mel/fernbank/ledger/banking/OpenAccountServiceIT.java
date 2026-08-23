package com.mel.fernbank.ledger.banking;

import static org.assertj.core.api.Assertions.assertThat;

import com.mel.fernbank.ledger.TestcontainersConfiguration;
import com.mel.fernbank.ledger.domain.AccountBalance;
import com.mel.fernbank.ledger.domain.AccountStatus;
import com.mel.fernbank.ledger.domain.AccountType;
import com.mel.fernbank.ledger.domain.Customer;
import com.mel.fernbank.ledger.repository.AccountBalanceRepository;
import com.mel.fernbank.ledger.repository.CustomerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * No class-level {@code @Transactional} here (or anywhere else in this package): the
 * money-movement executors commit via {@code REQUIRES_NEW}, which would silently
 * escape a test-managed transaction's rollback anyway - every test uses fresh data
 * instead, and the whole Testcontainers Postgres instance is thrown away after the run.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class OpenAccountServiceIT {

	@Autowired
	private OpenAccountService openAccountService;

	@Autowired
	private CustomerRepository customerRepository;

	@Autowired
	private AccountBalanceRepository accountBalanceRepository;

	@Test
	void opensAnAccountWithAZeroBalanceAndAValidAccountNumber() {
		Customer customer = customerRepository.save(new Customer("Ada Lovelace"));

		AccountResult result = openAccountService.openAccount(
				new OpenAccountCommand(customer.getId(), AccountType.CHECKING, "USD", null));

		assertThat(result.accountNumber()).hasSize(20).startsWith("FB");
		assertThat(result.type()).isEqualTo(AccountType.CHECKING);
		assertThat(result.currency()).isEqualTo("USD");
		assertThat(result.status()).isEqualTo(AccountStatus.ACTIVE);

		AccountBalance balance = accountBalanceRepository.findById(result.id()).orElseThrow();
		assertThat(balance.getBalance().minorUnits()).isZero();
		assertThat(balance.getBalance().currencyCode()).isEqualTo("USD");
	}

	@Test
	void twoAccountsForTheSameCustomerGetDifferentAccountNumbers() {
		Customer customer = customerRepository.save(new Customer("Grace Hopper"));

		AccountResult first = openAccountService.openAccount(
				new OpenAccountCommand(customer.getId(), AccountType.CHECKING, "USD", null));
		AccountResult second = openAccountService.openAccount(
				new OpenAccountCommand(customer.getId(), AccountType.SAVINGS, "USD", null));

		assertThat(first.accountNumber()).isNotEqualTo(second.accountNumber());
	}
}
