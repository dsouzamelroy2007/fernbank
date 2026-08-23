package com.mel.fernbank.ledger.banking;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mel.fernbank.ledger.TestcontainersConfiguration;
import com.mel.fernbank.ledger.domain.AccountType;
import com.mel.fernbank.ledger.domain.Customer;
import com.mel.fernbank.ledger.domain.Money;
import com.mel.fernbank.ledger.domain.User;
import com.mel.fernbank.ledger.repository.CustomerRepository;
import com.mel.fernbank.ledger.repository.UserRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * {@link ScheduleTransferService#schedule}'s own validation, distinct from
 * {@link ScheduledTransferIT}, which tests {@link ScheduledTransferRunner} executing
 * already-scheduled due transfers.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ScheduleTransferServiceIT {

	@Autowired
	private OpenAccountService openAccountService;

	@Autowired
	private ScheduleTransferService scheduleTransferService;

	@Autowired
	private CustomerRepository customerRepository;

	@Autowired
	private UserRepository userRepository;

	@Test
	void schedulingWithAZeroOrNegativeAmountIsRejected() {
		UUID source = openAccount("USD");
		UUID destination = openAccount("USD");

		assertThatThrownBy(() -> scheduleTransferService.schedule(new ScheduleTransferCommand(
						source, destination, Money.of(0, "USD"), "zero", Instant.now().plusSeconds(60), actor())))
				.isInstanceOf(InvalidAmountException.class);
		assertThatThrownBy(() -> scheduleTransferService.schedule(new ScheduleTransferCommand(
						source, destination, Money.of(-100, "USD"), "negative", Instant.now().plusSeconds(60), actor())))
				.isInstanceOf(InvalidAmountException.class);
	}

	@Test
	void schedulingAnAccountToItselfIsRejected() {
		UUID account = openAccount("USD");

		assertThatThrownBy(() -> scheduleTransferService.schedule(new ScheduleTransferCommand(
						account, account, Money.of(1_000, "USD"), "x", Instant.now().plusSeconds(60), actor())))
				.isInstanceOf(SameAccountTransferException.class);
	}

	@Test
	void schedulingFromAnUnknownSourceAccountThrowsAccountNotFound() {
		UUID destination = openAccount("USD");

		assertThatThrownBy(() -> scheduleTransferService.schedule(new ScheduleTransferCommand(
						UUID.randomUUID(),
						destination,
						Money.of(1_000, "USD"),
						"ghost source",
						Instant.now().plusSeconds(60),
						actor())))
				.isInstanceOf(AccountNotFoundException.class);
	}

	@Test
	void schedulingToAnUnknownDestinationAccountThrowsAccountNotFound() {
		UUID source = openAccount("USD");

		assertThatThrownBy(() -> scheduleTransferService.schedule(new ScheduleTransferCommand(
						source,
						UUID.randomUUID(),
						Money.of(1_000, "USD"),
						"ghost destination",
						Instant.now().plusSeconds(60),
						actor())))
				.isInstanceOf(AccountNotFoundException.class);
	}

	@Test
	void schedulingWithInsufficientFundsIsRejected() {
		UUID source = openAccount("USD");
		UUID destination = openAccount("USD");

		assertThatThrownBy(() -> scheduleTransferService.schedule(new ScheduleTransferCommand(
						source, destination, Money.of(1_000, "USD"), "no funds", Instant.now().plusSeconds(60), actor())))
				.isInstanceOf(InsufficientFundsException.class);
	}

	@Test
	void schedulingBetweenDifferentCurrenciesThrowsCurrencyMismatch() {
		UUID source = openAccount("USD");
		UUID destination = openAccount("EUR");

		assertThatThrownBy(() -> scheduleTransferService.schedule(new ScheduleTransferCommand(
						source,
						destination,
						Money.of(1_000, "USD"),
						"currency mismatch",
						Instant.now().plusSeconds(60),
						actor())))
				.isInstanceOf(CurrencyMismatchException.class);
	}

	private UUID openAccount(String currency) {
		Customer customer = customerRepository.save(new Customer("Schedule Validation Customer"));
		return openAccountService
				.openAccount(new OpenAccountCommand(customer.getId(), AccountType.CHECKING, currency, null))
				.id();
	}

	/** {@code Transaction.createdByUserId} FKs to a real app_user row - can't use a bare random UUID. */
	private UUID actor() {
		Customer customer = customerRepository.save(new Customer("Actor"));
		User user = userRepository.save(
				new User(customer.getId(), "actor-" + UUID.randomUUID() + "@example.com", "hash"));
		return user.getId();
	}
}
