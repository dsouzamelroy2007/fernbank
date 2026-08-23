package com.mel.fernbank.ledger.banking;

import static org.assertj.core.api.Assertions.assertThat;

import com.mel.fernbank.ledger.TestcontainersConfiguration;
import com.mel.fernbank.ledger.domain.AccountType;
import com.mel.fernbank.ledger.domain.Customer;
import com.mel.fernbank.ledger.domain.Money;
import com.mel.fernbank.ledger.domain.User;
import com.mel.fernbank.ledger.repository.CustomerRepository;
import com.mel.fernbank.ledger.repository.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class GetStatementServiceIT {

	private static final int DEPOSIT_COUNT = 5;

	@Autowired
	private OpenAccountService openAccountService;

	@Autowired
	private DepositWithdrawService depositWithdrawService;

	@Autowired
	private GetStatementService getStatementService;

	@Autowired
	private CustomerRepository customerRepository;

	@Autowired
	private UserRepository userRepository;

	@Test
	void pagesThroughAllEntriesInDescendingOrderWithNoDuplicatesOrGaps() {
		UUID accountId = openAccount();
		UUID actor = actor();
		for (int i = 0; i < DEPOSIT_COUNT; i++) {
			depositWithdrawService.deposit(new DepositWithdrawCommand(
					accountId, Money.of(100, "USD"), "deposit " + i, actor, UUID.randomUUID().toString()));
		}
		Instant from = Instant.now().minus(1, ChronoUnit.HOURS);
		Instant to = Instant.now().plus(1, ChronoUnit.HOURS);

		Set<UUID> seenEntryIds = new LinkedHashSet<>();
		String cursor = null;
		boolean hasNext = true;
		int pages = 0;
		while (hasNext) {
			StatementPage page = getStatementService.getStatement(new StatementQuery(accountId, from, to, cursor, 2));
			page.entries().forEach(e -> seenEntryIds.add(e.entryId()));
			cursor = page.nextCursor();
			hasNext = page.hasNext();
			pages++;
			assertThat(pages).isLessThanOrEqualTo(10); // guard against an infinite loop bug
		}

		assertThat(seenEntryIds).hasSize(DEPOSIT_COUNT);
		assertThat(pages).isEqualTo(3); // 2 + 2 + 1
	}

	@Test
	void aDateRangeThatExcludesEverythingReturnsAnEmptyPage() {
		UUID accountId = openAccount();
		depositWithdrawService.deposit(new DepositWithdrawCommand(
				accountId, Money.of(100, "USD"), "deposit", actor(), UUID.randomUUID().toString()));

		StatementPage page = getStatementService.getStatement(new StatementQuery(
				accountId,
				Instant.now().minus(10, ChronoUnit.DAYS),
				Instant.now().minus(9, ChronoUnit.DAYS),
				null,
				10));

		assertThat(page.entries()).isEmpty();
		assertThat(page.hasNext()).isFalse();
	}

	private UUID openAccount() {
		Customer customer = customerRepository.save(new Customer("Statement Test Customer"));
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
}
