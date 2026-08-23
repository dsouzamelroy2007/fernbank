package com.mel.fernbank.ledger.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mel.fernbank.ledger.TestcontainersConfiguration;
import com.mel.fernbank.ledger.repository.AccountBalanceRepository;
import com.mel.fernbank.ledger.repository.AccountRepository;
import com.mel.fernbank.ledger.repository.CustomerRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.PersistenceContext;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Two separate persistence contexts load the same {@link AccountBalance} row, both at
 * version 0. Both are then updated: the first save wins and bumps the version, the
 * second is stale and must be rejected — proving concurrent transfers can't silently
 * overwrite each other's balance change.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class AccountBalanceOptimisticLockingIT {

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Autowired
	private CustomerRepository customerRepository;

	@Autowired
	private AccountRepository accountRepository;

	@Autowired
	private AccountBalanceRepository accountBalanceRepository;

	@PersistenceContext
	private EntityManager entityManager;

	private TransactionTemplate transactionTemplate;
	private UUID accountId;

	@BeforeEach
	void openAnAccountWithABalance() {
		transactionTemplate = new TransactionTemplate(transactionManager);

		accountId = transactionTemplate.execute(status -> {
			Customer customer = customerRepository.save(new Customer("Grace Hopper"));
			Account account =
					accountRepository.save(new Account(customer.getId(), "FB-LOCK-0001", AccountType.CHECKING, "USD"));
			accountBalanceRepository.save(new AccountBalance(account.getId(), Money.of(10_000, "USD")));
			return account.getId();
		});
	}

	@Test
	void secondWriterWithAStaleVersionIsRejected() {
		AccountBalance staleCopyA = transactionTemplate.execute(status -> entityManager.find(AccountBalance.class, accountId));
		AccountBalance staleCopyB = transactionTemplate.execute(status -> entityManager.find(AccountBalance.class, accountId));

		assertThat(staleCopyA.getVersion()).isZero();
		assertThat(staleCopyB.getVersion()).isZero();

		transactionTemplate.executeWithoutResult(status -> {
			staleCopyA.setBalance(staleCopyA.getBalance().add(Money.of(500, "USD")));
			entityManager.merge(staleCopyA);
			entityManager.flush();
		});

		assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
					staleCopyB.setBalance(staleCopyB.getBalance().subtract(Money.of(200, "USD")));
					entityManager.merge(staleCopyB);
					entityManager.flush();
				}))
				.isInstanceOf(OptimisticLockException.class);

		AccountBalance current = accountBalanceRepository.findById(accountId).orElseThrow();
		assertThat(current.getBalance()).isEqualTo(Money.of(10_500, "USD"));
		assertThat(current.getVersion()).isEqualTo(1);
	}
}
