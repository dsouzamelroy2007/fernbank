package com.mel.fernbank.ledger.banking;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import com.mel.fernbank.ledger.LogCapture;
import com.mel.fernbank.ledger.TestcontainersConfiguration;
import com.mel.fernbank.ledger.domain.AccountType;
import com.mel.fernbank.ledger.domain.Customer;
import com.mel.fernbank.ledger.domain.Money;
import com.mel.fernbank.ledger.domain.ScheduledTransfer;
import com.mel.fernbank.ledger.domain.ScheduledTransferStatus;
import com.mel.fernbank.ledger.domain.User;
import com.mel.fernbank.ledger.repository.AccountBalanceRepository;
import com.mel.fernbank.ledger.repository.CustomerRepository;
import com.mel.fernbank.ledger.repository.ScheduledTransferRepository;
import com.mel.fernbank.ledger.repository.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ScheduledTransferIT {

	@Autowired
	private OpenAccountService openAccountService;

	@Autowired
	private DepositWithdrawService depositWithdrawService;

	@Autowired
	private ScheduleTransferService scheduleTransferService;

	@Autowired
	private ScheduledTransferRunner scheduledTransferRunner;

	@Autowired
	private ScheduledTransferRepository scheduledTransferRepository;

	@Autowired
	private CustomerRepository customerRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private AccountBalanceRepository accountBalanceRepository;

	@Test
	void aDueTransferExecutesWhenTheRunnerRuns() {
		UUID source = openAccount();
		UUID destination = openAccount();
		fund(source, 5_000);

		ScheduledTransfer scheduled = scheduleTransferService.schedule(new ScheduleTransferCommand(
				source, destination, Money.of(1_000, "USD"), "due now", Instant.now().minusSeconds(5), actor()));

		scheduledTransferRunner.executeDueTransfers(Instant.now());

		ScheduledTransfer reloaded =
				scheduledTransferRepository.findById(scheduled.getId()).orElseThrow();
		assertThat(reloaded.getStatus()).isEqualTo(ScheduledTransferStatus.EXECUTED);
		assertThat(accountBalanceRepository.findById(destination).orElseThrow().getBalance())
				.isEqualTo(Money.of(1_000, "USD"));
	}

	@Test
	void runInvokesExecuteDueTransfersThroughTheSelfProxyAndExecutesADueTransfer() {
		UUID source = openAccount();
		UUID destination = openAccount();
		fund(source, 5_000);

		ScheduledTransfer scheduled = scheduleTransferService.schedule(new ScheduleTransferCommand(
				source, destination, Money.of(1_000, "USD"), "due now", Instant.now().minusSeconds(5), actor()));

		scheduledTransferRunner.run();

		ScheduledTransfer reloaded =
				scheduledTransferRepository.findById(scheduled.getId()).orElseThrow();
		assertThat(reloaded.getStatus()).isEqualTo(ScheduledTransferStatus.EXECUTED);
		assertThat(accountBalanceRepository.findById(destination).orElseThrow().getBalance())
				.isEqualTo(Money.of(1_000, "USD"));
	}

	@Test
	void aFutureDatedTransferIsNotExecutedEarly() {
		UUID source = openAccount();
		UUID destination = openAccount();
		fund(source, 5_000);

		ScheduledTransfer scheduled = scheduleTransferService.schedule(new ScheduleTransferCommand(
				source,
				destination,
				Money.of(1_000, "USD"),
				"future",
				Instant.now().plus(1, ChronoUnit.DAYS),
				actor()));

		scheduledTransferRunner.executeDueTransfers(Instant.now());

		ScheduledTransfer reloaded =
				scheduledTransferRepository.findById(scheduled.getId()).orElseThrow();
		assertThat(reloaded.getStatus()).isEqualTo(ScheduledTransferStatus.PENDING);
		assertThat(accountBalanceRepository.findById(destination).orElseThrow().getBalance())
				.isEqualTo(Money.zero("USD"));
	}

	@Test
	void aFailingScheduledTransferRetriesBeforeBeingMarkedFailed() {
		UUID source = openAccount();
		UUID destination = openAccount();
		fund(source, 1_000);

		ScheduledTransfer scheduled = scheduleTransferService.schedule(new ScheduleTransferCommand(
				source, destination, Money.of(1_000, "USD"), "will fail at execution", Instant.now().minusSeconds(5), actor()));

		// The fund check at schedule time is best-effort, not a real hold - draining
		// the source afterward is exactly the gap it documents, and the only way to
		// make execution fail now that scheduling itself checks the balance upfront.
		depositWithdrawService.withdraw(new DepositWithdrawCommand(
				source, Money.of(1_000, "USD"), "drain", actor(), UUID.randomUUID().toString()));

		try (LogCapture logs = LogCapture.attachTo(ScheduledTransferRunner.class)) {
			scheduledTransferRunner.executeDueTransfers(Instant.now());
			assertThat(logs.hasEventAtLevelContaining(Level.WARN, "will retry")).isTrue();
		}
		ScheduledTransfer afterFirstAttempt =
				scheduledTransferRepository.findById(scheduled.getId()).orElseThrow();
		assertThat(afterFirstAttempt.getStatus()).isEqualTo(ScheduledTransferStatus.PENDING);
		assertThat(afterFirstAttempt.getAttemptCount()).isEqualTo(1);

		scheduledTransferRunner.executeDueTransfers(Instant.now());
		scheduledTransferRunner.executeDueTransfers(Instant.now());

		ScheduledTransfer afterThirdAttempt =
				scheduledTransferRepository.findById(scheduled.getId()).orElseThrow();
		assertThat(afterThirdAttempt.getStatus()).isEqualTo(ScheduledTransferStatus.FAILED);
		assertThat(afterThirdAttempt.getAttemptCount()).isEqualTo(3);
		assertThat(afterThirdAttempt.getFailureReason()).isNotBlank();
	}

	private UUID openAccount() {
		Customer customer = customerRepository.save(new Customer("Scheduled Transfer Customer"));
		return openAccountService
				.openAccount(new OpenAccountCommand(customer.getId(), AccountType.CHECKING, "USD", null))
				.id();
	}

	private void fund(UUID accountId, long minorUnits) {
		depositWithdrawService.deposit(new DepositWithdrawCommand(
				accountId, Money.of(minorUnits, "USD"), "seed", actor(), UUID.randomUUID().toString()));
	}

	/** {@code Transaction.createdByUserId} FKs to a real app_user row - can't use a bare random UUID. */
	private UUID actor() {
		Customer customer = customerRepository.save(new Customer("Actor"));
		User user = userRepository.save(
				new User(customer.getId(), "actor-" + UUID.randomUUID() + "@example.com", "hash"));
		return user.getId();
	}
}
