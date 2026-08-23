package com.mel.fernbank.ledger.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mel.fernbank.ledger.TestcontainersConfiguration;
import com.mel.fernbank.ledger.repository.AccountRepository;
import com.mel.fernbank.ledger.repository.CustomerRepository;
import com.mel.fernbank.ledger.repository.LedgerEntryRepository;
import com.mel.fernbank.ledger.repository.TransactionRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.exception.GenericJDBCException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class LedgerIntegrityIT {

	@Autowired
	private CustomerRepository customerRepository;

	@Autowired
	private AccountRepository accountRepository;

	@Autowired
	private TransactionRepository transactionRepository;

	@Autowired
	private LedgerEntryRepository ledgerEntryRepository;

	@PersistenceContext
	private EntityManager entityManager;

	private Account sourceAccount;
	private Account destinationAccount;

	@BeforeEach
	void openTwoAccounts() {
		Customer customer = customerRepository.save(new Customer("Ada Lovelace"));
		sourceAccount =
				accountRepository.save(new Account(customer.getId(), "FB-SOURCE-0001", AccountType.CHECKING, "USD"));
		destinationAccount = accountRepository.save(
				new Account(customer.getId(), "FB-DEST-0001", AccountType.SAVINGS, "USD"));
	}

	@Test
	void balancedTwoEntryTransactionPersistsCleanly() {
		Transaction transaction = transactionRepository.save(new Transaction("test transfer", null));

		ledgerEntryRepository.save(
				new LedgerEntry(transaction.getId(), sourceAccount.getId(), Money.of(-500, "USD")));
		ledgerEntryRepository.save(
				new LedgerEntry(transaction.getId(), destinationAccount.getId(), Money.of(500, "USD")));

		entityManager.flush();

		assertThat(ledgerEntryRepository.findByTransactionId(transaction.getId())).hasSize(2);
	}

	@Test
	void unbalancedEntriesAreRejectedAtCommit() {
		Transaction transaction = transactionRepository.save(new Transaction("unbalanced", null));

		ledgerEntryRepository.save(
				new LedgerEntry(transaction.getId(), sourceAccount.getId(), Money.of(-500, "USD")));
		ledgerEntryRepository.save(
				new LedgerEntry(transaction.getId(), destinationAccount.getId(), Money.of(400, "USD")));

		assertThatThrownBy(this::forceDeferredConstraintCheck)
				.isInstanceOf(GenericJDBCException.class)
				.hasMessageContaining("do not sum to zero");
	}

	@Test
	void singleEntryTransactionIsRejectedAtCommit() {
		Transaction transaction = transactionRepository.save(new Transaction("lonely entry", null));

		ledgerEntryRepository.save(
				new LedgerEntry(transaction.getId(), sourceAccount.getId(), Money.of(-500, "USD")));

		assertThatThrownBy(this::forceDeferredConstraintCheck)
				.isInstanceOf(GenericJDBCException.class)
				.hasMessageContaining("fewer than 2 ledger entries");
	}

	@Test
	void mixedCurrencyEntriesAreRejectedAtCommit() {
		Transaction transaction = transactionRepository.save(new Transaction("mixed currency", null));

		ledgerEntryRepository.save(
				new LedgerEntry(transaction.getId(), sourceAccount.getId(), Money.of(-500, "USD")));
		ledgerEntryRepository.save(
				new LedgerEntry(transaction.getId(), destinationAccount.getId(), Money.of(500, "EUR")));

		assertThatThrownBy(this::forceDeferredConstraintCheck)
				.isInstanceOf(GenericJDBCException.class)
				.hasMessageContaining("more than one currency");
	}

	@Test
	void ledgerEntriesCannotBeUpdated() {
		Transaction transaction = transactionRepository.save(new Transaction("immutable check", null));
		LedgerEntry entry = ledgerEntryRepository.save(
				new LedgerEntry(transaction.getId(), sourceAccount.getId(), Money.of(-500, "USD")));
		ledgerEntryRepository.save(
				new LedgerEntry(transaction.getId(), destinationAccount.getId(), Money.of(500, "USD")));
		entityManager.flush();
		entityManager.clear();

		assertThatThrownBy(() -> entityManager
						.createNativeQuery("UPDATE ledger_entry SET amount_minor_units = -999 WHERE id = ?1")
						.setParameter(1, entry.getId())
						.executeUpdate())
				.isInstanceOf(GenericJDBCException.class)
				.hasMessageContaining("append-only");
	}

	@Test
	void ledgerEntriesCannotBeDeleted() {
		Transaction transaction = transactionRepository.save(new Transaction("immutable delete check", null));
		LedgerEntry entry = ledgerEntryRepository.save(
				new LedgerEntry(transaction.getId(), sourceAccount.getId(), Money.of(-500, "USD")));
		ledgerEntryRepository.save(
				new LedgerEntry(transaction.getId(), destinationAccount.getId(), Money.of(500, "USD")));
		entityManager.flush();
		entityManager.clear();

		assertThatThrownBy(() -> entityManager
						.createNativeQuery("DELETE FROM ledger_entry WHERE id = ?1")
						.setParameter(1, entry.getId())
						.executeUpdate())
				.isInstanceOf(GenericJDBCException.class)
				.hasMessageContaining("append-only");
	}

	/**
	 * The zero-sum/entry-count/currency check is a DEFERRABLE INITIALLY DEFERRED
	 * constraint trigger, so it only fires at COMMIT. Tests run inside a transaction
	 * that gets rolled back rather than committed, so SET CONSTRAINTS ALL IMMEDIATE
	 * forces the check to run now, inside the still-open transaction.
	 */
	private void forceDeferredConstraintCheck() {
		entityManager.flush();
		entityManager.createNativeQuery("SET CONSTRAINTS ALL IMMEDIATE").executeUpdate();
	}
}
