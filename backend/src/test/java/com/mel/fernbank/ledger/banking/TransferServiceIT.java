package com.mel.fernbank.ledger.banking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mel.fernbank.ledger.TestcontainersConfiguration;
import com.mel.fernbank.ledger.domain.Account;
import com.mel.fernbank.ledger.domain.AccountStatus;
import com.mel.fernbank.ledger.domain.AccountType;
import com.mel.fernbank.ledger.domain.Customer;
import com.mel.fernbank.ledger.domain.Money;
import com.mel.fernbank.ledger.domain.User;
import com.mel.fernbank.ledger.idempotency.IdempotencyConflictException;
import com.mel.fernbank.ledger.repository.AccountBalanceRepository;
import com.mel.fernbank.ledger.repository.AccountRepository;
import com.mel.fernbank.ledger.repository.CustomerRepository;
import com.mel.fernbank.ledger.repository.UserRepository;
import com.mel.fernbank.ledger.security.StepUpRequiredException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class TransferServiceIT {

	@Autowired
	private OpenAccountService openAccountService;

	@Autowired
	private DepositWithdrawService depositWithdrawService;

	@Autowired
	private TransferService transferService;

	@Autowired
	private CustomerRepository customerRepository;

	@Autowired
	private AccountRepository accountRepository;

	@Autowired
	private AccountBalanceRepository accountBalanceRepository;

	@Autowired
	private UserRepository userRepository;

	@Test
	void transfersMoneyBetweenTwoAccounts() {
		UUID source = openAccount("USD");
		UUID destination = openAccount("USD");
		fund(source, 10_000);

		TransferResult result = transferService.transfer(
				new TransferCommand(source, destination, Money.of(2_500, "USD"), "rent", actor(), key(), false));

		assertThat(result.sourceNewBalance()).isEqualTo(Money.of(7_500, "USD"));
		assertThat(result.destinationNewBalance()).isEqualTo(Money.of(2_500, "USD"));
	}

	@Test
	void rejectsATransferBetweenDifferentCurrencies() {
		UUID source = openAccount("USD");
		UUID destination = openAccount("EUR");
		fund(source, 10_000);

		assertThatThrownBy(() -> transferService.transfer(
						new TransferCommand(source, destination, Money.of(100, "USD"), "x", actor(), key(), false)))
				.isInstanceOf(CurrencyMismatchException.class);
	}

	@Test
	void rejectsATransferWithInsufficientFunds() {
		UUID source = openAccount("USD");
		UUID destination = openAccount("USD");
		fund(source, 100);

		assertThatThrownBy(() -> transferService.transfer(
						new TransferCommand(source, destination, Money.of(200, "USD"), "x", actor(), key(), false)))
				.isInstanceOf(InsufficientFundsException.class);
	}

	@Test
	void frozenSourceBlocksDebitButFrozenDestinationStillAcceptsCredit() {
		UUID source = openAccount("USD");
		UUID destination = openAccount("USD");
		fund(source, 10_000);
		fund(destination, 0);
		freeze(destination);

		TransferResult result = transferService.transfer(
				new TransferCommand(source, destination, Money.of(500, "USD"), "ok", actor(), key(), false));
		assertThat(result.destinationNewBalance()).isEqualTo(Money.of(500, "USD"));

		freeze(source);
		assertThatThrownBy(() -> transferService.transfer(
						new TransferCommand(source, destination, Money.of(100, "USD"), "x", actor(), key(), false)))
				.isInstanceOf(AccountNotActiveException.class);
	}

	@Test
	void aTransferAtOrAboveTheStepUpThresholdRequiresElevation() {
		UUID source = openAccount("USD");
		UUID destination = openAccount("USD");
		fund(source, 1_000_000);

		assertThatThrownBy(() -> transferService.transfer(new TransferCommand(
						source, destination, Money.of(100_000, "USD"), "big", actor(), key(), false)))
				.isInstanceOf(StepUpRequiredException.class);

		TransferResult result = transferService.transfer(new TransferCommand(
				source, destination, Money.of(100_000, "USD"), "big, elevated", actor(), key(), true));
		assertThat(result.destinationNewBalance()).isEqualTo(Money.of(100_000, "USD"));
	}

	@Test
	void transferOfZeroOrNegativeAmountIsRejected() {
		UUID source = openAccount("USD");
		UUID destination = openAccount("USD");
		fund(source, 10_000);

		assertThatThrownBy(() -> transferService.transfer(
						new TransferCommand(source, destination, Money.of(0, "USD"), "x", actor(), key(), false)))
				.isInstanceOf(InvalidAmountException.class);
		assertThatThrownBy(() -> transferService.transfer(
						new TransferCommand(source, destination, Money.of(-100, "USD"), "x", actor(), key(), false)))
				.isInstanceOf(InvalidAmountException.class);
	}

	@Test
	void transferringAnAccountToItselfIsRejected() {
		UUID account = openAccount("USD");
		fund(account, 10_000);

		assertThatThrownBy(() -> transferService.transfer(
						new TransferCommand(account, account, Money.of(2_500, "USD"), "x", actor(), key(), false)))
				.isInstanceOf(SameAccountTransferException.class);

		assertThat(accountBalanceRepository.findById(account).orElseThrow().getBalance())
				.isEqualTo(Money.of(10_000, "USD"));
	}

	@Test
	void transferFromAnUnknownSourceAccountThrowsAccountNotFound() {
		UUID destination = openAccount("USD");

		assertThatThrownBy(() -> transferService.transfer(new TransferCommand(
						UUID.randomUUID(), destination, Money.of(100, "USD"), "x", actor(), key(), false)))
				.isInstanceOf(AccountNotFoundException.class);
	}

	@Test
	void transferToAnUnknownDestinationAccountThrowsAccountNotFound() {
		UUID source = openAccount("USD");
		fund(source, 10_000);

		assertThatThrownBy(() -> transferService.transfer(new TransferCommand(
						source, UUID.randomUUID(), Money.of(100, "USD"), "x", actor(), key(), false)))
				.isInstanceOf(AccountNotFoundException.class);
	}

	@Test
	void sameIdempotencyKeyAndBodyReplaysTheOriginalResultWithoutDoubleEffect() {
		UUID source = openAccount("USD");
		UUID destination = openAccount("USD");
		fund(source, 10_000);
		UUID userId = actor();
		String idempotencyKey = key();
		TransferCommand command =
				new TransferCommand(source, destination, Money.of(1_500, "USD"), "rent", userId, idempotencyKey, false);

		TransferResult first = transferService.transfer(command);
		TransferResult second = transferService.transfer(command);

		assertThat(second.transactionId()).isEqualTo(first.transactionId());
		assertThat(accountBalanceRepository.findById(destination).orElseThrow().getBalance())
				.isEqualTo(Money.of(1_500, "USD"));
	}

	@Test
	void sameIdempotencyKeyWithADifferentBodyIsRejected() {
		UUID source = openAccount("USD");
		UUID destination = openAccount("USD");
		fund(source, 10_000);
		UUID userId = actor();
		String idempotencyKey = key();
		transferService.transfer(
				new TransferCommand(source, destination, Money.of(1_500, "USD"), "rent", userId, idempotencyKey, false));

		assertThatThrownBy(() -> transferService.transfer(new TransferCommand(
						source, destination, Money.of(2_000, "USD"), "different", userId, idempotencyKey, false)))
				.isInstanceOf(IdempotencyConflictException.class);
	}

	private UUID openAccount(String currency) {
		Customer customer = customerRepository.save(new Customer("Test Customer"));
		return openAccountService
				.openAccount(new OpenAccountCommand(customer.getId(), AccountType.CHECKING, currency, null))
				.id();
	}

	private void fund(UUID accountId, long minorUnits) {
		if (minorUnits <= 0) {
			return;
		}
		String currency = accountRepository.findById(accountId).orElseThrow().getCurrency();
		depositWithdrawService.deposit(
				new DepositWithdrawCommand(accountId, Money.of(minorUnits, currency), "seed", actor(), key()));
	}

	private void freeze(UUID accountId) {
		Account account = accountRepository.findById(accountId).orElseThrow();
		account.setStatus(AccountStatus.FROZEN);
		accountRepository.save(account);
	}

	/** {@code Transaction.createdByUserId} FKs to a real app_user row - can't use a bare random UUID. */
	private UUID actor() {
		Customer customer = customerRepository.save(new Customer("Actor"));
		User user = userRepository.save(
				new User(customer.getId(), "actor-" + UUID.randomUUID() + "@example.com", "hash"));
		return user.getId();
	}

	private String key() {
		return UUID.randomUUID().toString();
	}
}
