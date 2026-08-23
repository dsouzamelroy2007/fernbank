package com.mel.fernbank.ledger.banking;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import com.mel.fernbank.ledger.LogCapture;
import com.mel.fernbank.ledger.TestcontainersConfiguration;
import com.mel.fernbank.ledger.domain.AccountBalance;
import com.mel.fernbank.ledger.domain.AccountType;
import com.mel.fernbank.ledger.domain.Customer;
import com.mel.fernbank.ledger.domain.Money;
import com.mel.fernbank.ledger.domain.User;
import com.mel.fernbank.ledger.repository.AccountBalanceRepository;
import com.mel.fernbank.ledger.repository.AuditEventRepository;
import com.mel.fernbank.ledger.repository.CustomerRepository;
import com.mel.fernbank.ledger.repository.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ReconciliationServiceIT {

	@Autowired
	private OpenAccountService openAccountService;

	@Autowired
	private DepositWithdrawService depositWithdrawService;

	@Autowired
	private TransferService transferService;

	@Autowired
	private ReconciliationService reconciliationService;

	@Autowired
	private CustomerRepository customerRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private AccountBalanceRepository accountBalanceRepository;

	@Autowired
	private AuditEventRepository auditEventRepository;

	@Test
	void scheduledCheckInvokesReconcileThroughTheSelfProxyAndDetectsAHealthySystem() {
		UUID a = openAccount();
		UUID b = openAccount();
		depositWithdrawService.deposit(
				new DepositWithdrawCommand(a, Money.of(5_000, "USD"), "seed", actor(), key()));
		transferService.transfer(new TransferCommand(a, b, Money.of(1_500, "USD"), "move", actor(), key(), false));

		long discrepanciesBefore = countDiscrepancyEvents();
		reconciliationService.scheduledCheck();
		long discrepanciesAfter = countDiscrepancyEvents();

		assertThat(discrepanciesAfter).isEqualTo(discrepanciesBefore);
	}

	@Test
	void aHealthySystemAfterVariousMovementsReportsNoDiscrepancies() {
		UUID a = openAccount();
		UUID b = openAccount();
		depositWithdrawService.deposit(
				new DepositWithdrawCommand(a, Money.of(5_000, "USD"), "seed", actor(), key()));
		transferService.transfer(new TransferCommand(
				a, b, Money.of(1_500, "USD"), "move", actor(), key(), false));
		depositWithdrawService.withdraw(
				new DepositWithdrawCommand(b, Money.of(500, "USD"), "atm", actor(), key()));

		ReconciliationReport report = reconciliationService.reconcile();

		assertThat(report.isHealthy()).isTrue();
	}

	@Test
	void aCorruptedBalanceIsDetected() {
		UUID a = openAccount();
		depositWithdrawService.deposit(
				new DepositWithdrawCommand(a, Money.of(5_000, "USD"), "seed", actor(), key()));

		// Ledger entries are trigger-protected from mutation (Phase 1), but the balance
		// projection is a normal mutable row - corrupt it directly to prove the
		// reconciliation check would actually catch a real desync, not just pass by
		// construction. reconcile() scans the whole database, so this MUST be restored
		// afterward - other tests in this suite (e.g. TransferConcurrencyIT) also call
		// reconcile() and would otherwise see this leftover corruption as their own.
		AccountBalance balance = accountBalanceRepository.findById(a).orElseThrow();
		Money correctBalance = balance.getBalance();
		balance.setBalance(Money.of(999_999, "USD"));
		accountBalanceRepository.save(balance);

		try {
			ReconciliationReport report = reconciliationService.reconcile();

			assertThat(report.isHealthy()).isFalse();
			assertThat(report.discrepancies()).anyMatch(d -> d.description().contains(a.toString()));

			try (LogCapture logs = LogCapture.attachTo(ReconciliationService.class)) {
				reconciliationService.scheduledCheck();
				assertThat(logs.hasEventAtLevelContaining(Level.WARN, "Reconciliation found"))
						.isTrue();
			}
		} finally {
			AccountBalance toRestore = accountBalanceRepository.findById(a).orElseThrow();
			toRestore.setBalance(correctBalance);
			accountBalanceRepository.save(toRestore);
		}
	}

	private long countDiscrepancyEvents() {
		return auditEventRepository.findAll().stream()
				.filter(e -> e.getEventType().equals("reconciliation.discrepancy_found"))
				.count();
	}

	private UUID openAccount() {
		Customer customer = customerRepository.save(new Customer("Reconciliation Test Customer"));
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
