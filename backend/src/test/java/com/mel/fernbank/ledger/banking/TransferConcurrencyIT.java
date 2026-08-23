package com.mel.fernbank.ledger.banking;

import static org.assertj.core.api.Assertions.assertThat;

import com.mel.fernbank.ledger.TestcontainersConfiguration;
import com.mel.fernbank.ledger.domain.AccountType;
import com.mel.fernbank.ledger.domain.Customer;
import com.mel.fernbank.ledger.domain.Money;
import com.mel.fernbank.ledger.domain.User;
import com.mel.fernbank.ledger.repository.AccountBalanceRepository;
import com.mel.fernbank.ledger.repository.CustomerRepository;
import com.mel.fernbank.ledger.repository.LedgerEntryRepository;
import com.mel.fernbank.ledger.repository.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * 50 concurrent $1 transfers out of a $20 account: proves optimistic-retry (see
 * {@link OptimisticRetryTemplate}) neither loses a legitimate transfer to a lock
 * conflict nor lets the balance go negative under contention. No thread here is
 * Spring-managed - each call to {@link TransferService#transfer} starts its own fresh
 * transaction regardless of calling thread, which is exactly what's being tested.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class TransferConcurrencyIT {

	private static final int ATTEMPTS = 50;
	private static final long STARTING_BALANCE_MINOR_UNITS = 2_000; // $20.00
	private static final long TRANSFER_AMOUNT_MINOR_UNITS = 100; // $1.00
	private static final long EXPECTED_SUCCESSES = STARTING_BALANCE_MINOR_UNITS / TRANSFER_AMOUNT_MINOR_UNITS;

	@Autowired
	private OpenAccountService openAccountService;

	@Autowired
	private DepositWithdrawService depositWithdrawService;

	@Autowired
	private TransferService transferService;

	@Autowired
	private CustomerRepository customerRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private AccountBalanceRepository accountBalanceRepository;

	@Autowired
	private LedgerEntryRepository ledgerEntryRepository;

	@Autowired
	private ReconciliationService reconciliationService;

	@Test
	void fiftyConcurrentTransfersNeverOverdraftAndNeverLoseAnEntry() throws Exception {
		UUID source = openAccount();
		UUID destination = openAccount();
		UUID actor = actor();
		depositWithdrawService.deposit(new DepositWithdrawCommand(
				source, Money.of(STARTING_BALANCE_MINOR_UNITS, "USD"), "seed", actor, key()));

		List<Callable<Boolean>> attempts = new ArrayList<>();
		for (int i = 0; i < ATTEMPTS; i++) {
			attempts.add(() -> {
				try {
					transferService.transfer(new TransferCommand(
							source,
							destination,
							Money.of(TRANSFER_AMOUNT_MINOR_UNITS, "USD"),
							"concurrent",
							actor,
							key(),
							false));
					return true;
				} catch (InsufficientFundsException e) {
					return false;
				}
			});
		}

		ExecutorService executor = Executors.newFixedThreadPool(20);
		List<Future<Boolean>> futures = executor.invokeAll(attempts);
		executor.shutdown();
		assertThat(executor.awaitTermination(60, TimeUnit.SECONDS)).isTrue();

		long successCount = 0;
		for (Future<Boolean> future : futures) {
			if (future.get()) {
				successCount++;
			}
		}

		assertThat(successCount).isEqualTo(EXPECTED_SUCCESSES);
		assertThat(accountBalanceRepository.findById(source).orElseThrow().getBalance())
				.isEqualTo(Money.zero("USD"));
		assertThat(accountBalanceRepository.findById(destination).orElseThrow().getBalance())
				.isEqualTo(Money.of(STARTING_BALANCE_MINOR_UNITS, "USD"));

		// Destination started unfunded, so every one of its entries came from a
		// successful transfer - proves none were silently dropped under contention.
		assertThat(ledgerEntryRepository.findByAccountIdOrderByCreatedAtDesc(destination))
				.hasSize((int) successCount);

		assertThat(reconciliationService.reconcile().isHealthy()).isTrue();
	}

	private UUID openAccount() {
		Customer customer = customerRepository.save(new Customer("Concurrency Test Customer"));
		return openAccountService
				.openAccount(new OpenAccountCommand(customer.getId(), AccountType.CHECKING, "USD", null))
				.id();
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
