package com.mel.fernbank.ledger.banking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mel.fernbank.ledger.TestcontainersConfiguration;
import com.mel.fernbank.ledger.domain.Account;
import com.mel.fernbank.ledger.domain.AccountBalance;
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
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class DepositWithdrawServiceIT {

	@Autowired
	private OpenAccountService openAccountService;

	@Autowired
	private DepositWithdrawService depositWithdrawService;

	@Autowired
	private CustomerRepository customerRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private AccountRepository accountRepository;

	@Autowired
	private AccountBalanceRepository accountBalanceRepository;

	@Test
	void depositCreditsTheAccountAndUsesTheSharedCashClearingAccount() {
		// The USD CASH_CLEARING account is process-wide (one per currency, lazily
		// created - see the partial unique index in V3), so other tests in this suite
		// may have already created and moved it. Assert the *delta*, not an absolute
		// starting balance.
		UUID accountId = openAccount("USD");
		Money cashClearingBalanceBefore = accountRepository
				.findByTypeAndCurrency(AccountType.SYSTEM, "USD")
				.flatMap(a -> accountBalanceRepository.findById(a.getId()))
				.map(AccountBalance::getBalance)
				.orElse(Money.zero("USD"));

		MoneyMovementResult result = depositWithdrawService.deposit(
				new DepositWithdrawCommand(accountId, Money.of(5_000, "USD"), "payday", actor(), key()));

		assertThat(result.newBalance()).isEqualTo(Money.of(5_000, "USD"));
		assertThat(accountBalanceRepository.findById(accountId).orElseThrow().getBalance())
				.isEqualTo(Money.of(5_000, "USD"));

		Account cashClearing = accountRepository
				.findByTypeAndCurrency(AccountType.SYSTEM, "USD")
				.orElseThrow();
		assertThat(accountBalanceRepository.findById(cashClearing.getId()).orElseThrow().getBalance())
				.isEqualTo(cashClearingBalanceBefore.subtract(Money.of(5_000, "USD")));
	}

	@Test
	void withdrawDebitsTheAccountAndRejectsInsufficientFunds() {
		UUID accountId = openAccount("USD");
		depositWithdrawService.deposit(
				new DepositWithdrawCommand(accountId, Money.of(1_000, "USD"), "seed", actor(), key()));

		MoneyMovementResult result = depositWithdrawService.withdraw(
				new DepositWithdrawCommand(accountId, Money.of(400, "USD"), "atm", actor(), key()));
		assertThat(result.newBalance()).isEqualTo(Money.of(600, "USD"));

		assertThatThrownBy(() -> depositWithdrawService.withdraw(
						new DepositWithdrawCommand(accountId, Money.of(10_000, "USD"), "too much", actor(), key())))
				.isInstanceOf(InsufficientFundsException.class);
	}

	@Test
	void frozenAccountRejectsWithdrawalButStillAcceptsDeposit() {
		UUID accountId = openAccount("USD");
		Account account = accountRepository.findById(accountId).orElseThrow();
		account.setStatus(AccountStatus.FROZEN);
		accountRepository.save(account);

		depositWithdrawService.deposit(
				new DepositWithdrawCommand(accountId, Money.of(500, "USD"), "still allowed", actor(), key()));

		assertThatThrownBy(() -> depositWithdrawService.withdraw(
						new DepositWithdrawCommand(accountId, Money.of(100, "USD"), "blocked", actor(), key())))
				.isInstanceOf(AccountNotActiveException.class);
	}

	@Test
	void depositToAnUnknownAccountThrowsAccountNotFound() {
		assertThatThrownBy(() -> depositWithdrawService.deposit(
						new DepositWithdrawCommand(UUID.randomUUID(), Money.of(1_000, "USD"), "ghost", actor(), key())))
				.isInstanceOf(AccountNotFoundException.class);
	}

	@Test
	void depositOfZeroOrNegativeAmountIsRejected() {
		UUID accountId = openAccount("USD");

		assertThatThrownBy(() -> depositWithdrawService.deposit(
						new DepositWithdrawCommand(accountId, Money.of(0, "USD"), "zero", actor(), key())))
				.isInstanceOf(InvalidAmountException.class);
		assertThatThrownBy(() -> depositWithdrawService.deposit(
						new DepositWithdrawCommand(accountId, Money.of(-500, "USD"), "negative", actor(), key())))
				.isInstanceOf(InvalidAmountException.class);
	}

	@Test
	void withdrawFromAnUnknownAccountThrowsAccountNotFound() {
		assertThatThrownBy(() -> depositWithdrawService.withdraw(
						new DepositWithdrawCommand(UUID.randomUUID(), Money.of(1_000, "USD"), "ghost", actor(), key())))
				.isInstanceOf(AccountNotFoundException.class);
	}

	@Test
	void withdrawOfZeroOrNegativeAmountIsRejected() {
		UUID accountId = openAccount("USD");
		depositWithdrawService.deposit(
				new DepositWithdrawCommand(accountId, Money.of(1_000, "USD"), "seed", actor(), key()));

		assertThatThrownBy(() -> depositWithdrawService.withdraw(
						new DepositWithdrawCommand(accountId, Money.of(0, "USD"), "zero", actor(), key())))
				.isInstanceOf(InvalidAmountException.class);
		assertThatThrownBy(() -> depositWithdrawService.withdraw(
						new DepositWithdrawCommand(accountId, Money.of(-500, "USD"), "negative", actor(), key())))
				.isInstanceOf(InvalidAmountException.class);
	}

	@Test
	void sameIdempotencyKeyAndBodyReplaysTheOriginalResultWithoutDoubleEffect() {
		UUID accountId = openAccount("USD");
		UUID userId = actor();
		String idempotencyKey = key();
		DepositWithdrawCommand command =
				new DepositWithdrawCommand(accountId, Money.of(2_000, "USD"), "bonus", userId, idempotencyKey);

		MoneyMovementResult first = depositWithdrawService.deposit(command);
		MoneyMovementResult second = depositWithdrawService.deposit(command);

		assertThat(second.transactionId()).isEqualTo(first.transactionId());
		assertThat(accountBalanceRepository.findById(accountId).orElseThrow().getBalance())
				.isEqualTo(Money.of(2_000, "USD"));
	}

	@Test
	void sameIdempotencyKeyWithADifferentBodyIsRejected() {
		UUID accountId = openAccount("USD");
		UUID userId = actor();
		String idempotencyKey = key();
		depositWithdrawService.deposit(
				new DepositWithdrawCommand(accountId, Money.of(2_000, "USD"), "bonus", userId, idempotencyKey));

		assertThatThrownBy(() -> depositWithdrawService.deposit(
						new DepositWithdrawCommand(accountId, Money.of(3_000, "USD"), "different", userId, idempotencyKey)))
				.isInstanceOf(IdempotencyConflictException.class);
	}

	private UUID openAccount(String currency) {
		Customer customer = customerRepository.save(new Customer("Test Customer"));
		AccountResult result = openAccountService.openAccount(
				new OpenAccountCommand(customer.getId(), AccountType.CHECKING, currency, null));
		return result.id();
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
