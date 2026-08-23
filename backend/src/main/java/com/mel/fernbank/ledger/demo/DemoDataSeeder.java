package com.mel.fernbank.ledger.demo;

import com.mel.fernbank.ledger.auth.AuthenticationService;
import com.mel.fernbank.ledger.auth.EmailAlreadyRegisteredException;
import com.mel.fernbank.ledger.auth.dto.RegisterRequest;
import com.mel.fernbank.ledger.banking.AccountResult;
import com.mel.fernbank.ledger.banking.DepositWithdrawCommand;
import com.mel.fernbank.ledger.banking.DepositWithdrawService;
import com.mel.fernbank.ledger.banking.OpenAccountCommand;
import com.mel.fernbank.ledger.banking.OpenAccountService;
import com.mel.fernbank.ledger.banking.TransferCommand;
import com.mel.fernbank.ledger.banking.TransferService;
import com.mel.fernbank.ledger.domain.AccountType;
import com.mel.fernbank.ledger.domain.Money;
import com.mel.fernbank.ledger.domain.User;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Fills a fresh {@code demo} environment with a handful of real customers, accounts,
 * and transactions - run through the same domain services the API uses (
 * {@link AuthenticationService}, {@link OpenAccountService}, {@link
 * DepositWithdrawService}, {@link TransferService}), not hand-written SQL, so every
 * seeded balance is an honestly double-entry outcome rather than a row that could drift
 * from the ledger invariant. Idempotent by construction: {@link
 * AuthenticationService#register} throws {@link EmailAlreadyRegisteredException} on a
 * repeat run, which this treats as "this customer's whole seed sequence already
 * happened" and skips it entirely - restarting the compose stack never double-seeds.
 *
 * <p>Every {@code createdAt} on a transaction/ledger entry is set by the domain layer to
 * "now" (see {@code Transaction}) - there's no supported way to backdate a seeded
 * transaction to look like it happened months ago, so this seeds a realistic *count and
 * variety* of activity instead of literal historical dates.
 */
@Component
@Profile("demo")
public class DemoDataSeeder implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

	private static final String DEMO_PASSWORD = "DemoPass123!";
	private static final String CURRENCY = "USD";

	private record DemoCustomer(String fullName, String email) {}

	private static final List<DemoCustomer> DEMO_CUSTOMERS = List.of(
			new DemoCustomer("Ava Thompson", "ava.demo@fernbank.example"),
			new DemoCustomer("Noah Kim", "noah.demo@fernbank.example"),
			new DemoCustomer("Priya Sharma", "priya.demo@fernbank.example"));

	private final AuthenticationService authenticationService;
	private final OpenAccountService openAccountService;
	private final DepositWithdrawService depositWithdrawService;
	private final TransferService transferService;

	public DemoDataSeeder(
			AuthenticationService authenticationService,
			OpenAccountService openAccountService,
			DepositWithdrawService depositWithdrawService,
			TransferService transferService) {
		this.authenticationService = authenticationService;
		this.openAccountService = openAccountService;
		this.depositWithdrawService = depositWithdrawService;
		this.transferService = transferService;
	}

	@Override
	public void run(ApplicationArguments args) {
		SeededAccounts ava = null;
		SeededAccounts noah = null;
		for (DemoCustomer demo : DEMO_CUSTOMERS) {
			SeededAccounts seeded = seedCustomer(demo);
			if (seeded == null) {
				continue;
			}
			if (demo.email().startsWith("ava.")) {
				ava = seeded;
			}
			if (demo.email().startsWith("noah.")) {
				noah = seeded;
			}
		}
		if (ava != null && noah != null) {
			transfer(ava.checkingId(), noah.checkingId(), ava.userId(), 12500, "Split dinner bill");
		}

		log.info(
				"Demo environment ready - sign in with any of {} / password {}",
				DEMO_CUSTOMERS.stream().map(DemoCustomer::email).toList(),
				DEMO_PASSWORD);
	}

	private record SeededAccounts(UUID userId, UUID checkingId, UUID savingsId) {}

	private SeededAccounts seedCustomer(DemoCustomer demo) {
		User user;
		try {
			user = authenticationService.register(new RegisterRequest(demo.fullName(), demo.email(), DEMO_PASSWORD));
		} catch (EmailAlreadyRegisteredException e) {
			log.debug("Demo customer {} already seeded, skipping", demo.email());
			return null;
		}

		AccountResult checking = openAccountService.openAccount(
				new OpenAccountCommand(user.getCustomerId(), AccountType.CHECKING, CURRENCY, user.getId()));
		AccountResult savings = openAccountService.openAccount(
				new OpenAccountCommand(user.getCustomerId(), AccountType.SAVINGS, CURRENCY, user.getId()));

		deposit(checking.id(), user.getId(), 250000, "Initial funding");
		deposit(checking.id(), user.getId(), 320000, "Salary payment");
		withdraw(checking.id(), user.getId(), 95000, "Rent payment");
		deposit(checking.id(), user.getId(), 42000, "Freelance income");
		withdraw(checking.id(), user.getId(), 18000, "Utility bill");
		deposit(savings.id(), user.getId(), 50000, "Initial funding");
		transfer(checking.id(), savings.id(), user.getId(), 30000, "Move to savings");

		return new SeededAccounts(user.getId(), checking.id(), savings.id());
	}

	private void deposit(UUID accountId, UUID actingUserId, long minorUnits, String description) {
		depositWithdrawService.deposit(new DepositWithdrawCommand(
				accountId, Money.of(minorUnits, CURRENCY), description, actingUserId, UUID.randomUUID().toString()));
	}

	private void withdraw(UUID accountId, UUID actingUserId, long minorUnits, String description) {
		depositWithdrawService.withdraw(new DepositWithdrawCommand(
				accountId, Money.of(minorUnits, CURRENCY), description, actingUserId, UUID.randomUUID().toString()));
	}

	private void transfer(
			UUID sourceAccountId, UUID destinationAccountId, UUID actingUserId, long minorUnits, String description) {
		transferService.transfer(new TransferCommand(
				sourceAccountId,
				destinationAccountId,
				Money.of(minorUnits, CURRENCY),
				description,
				actingUserId,
				UUID.randomUUID().toString(),
				false));
	}
}
