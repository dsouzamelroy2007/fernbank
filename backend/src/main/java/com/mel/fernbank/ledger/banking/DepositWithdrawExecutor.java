package com.mel.fernbank.ledger.banking;

import com.mel.fernbank.ledger.domain.Account;
import com.mel.fernbank.ledger.domain.AccountBalance;
import com.mel.fernbank.ledger.domain.AccountStatus;
import com.mel.fernbank.ledger.domain.AccountType;
import com.mel.fernbank.ledger.domain.LedgerEntry;
import com.mel.fernbank.ledger.domain.Money;
import com.mel.fernbank.ledger.domain.Transaction;
import com.mel.fernbank.ledger.repository.AccountBalanceRepository;
import com.mel.fernbank.ledger.repository.AccountRepository;
import com.mel.fernbank.ledger.repository.LedgerEntryRepository;
import com.mel.fernbank.ledger.repository.TransactionRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * One attempt at a deposit or withdrawal. Each public method is its own fresh
 * transaction ({@code REQUIRES_NEW} - see {@link TransferExecutor}'s javadoc for why
 * plain {@code @Transactional} isn't safe enough here), called by
 * {@link DepositWithdrawService} through {@link OptimisticRetryTemplate} so a stale
 * {@code @Version} on either balance row retries with fresh reads rather than
 * corrupting anything.
 */
@Component
class DepositWithdrawExecutor {

	private final AccountRepository accountRepository;
	private final AccountBalanceRepository accountBalanceRepository;
	private final AccountNumberGenerator accountNumberGenerator;
	private final TransactionRepository transactionRepository;
	private final LedgerEntryRepository ledgerEntryRepository;

	DepositWithdrawExecutor(
			AccountRepository accountRepository,
			AccountBalanceRepository accountBalanceRepository,
			AccountNumberGenerator accountNumberGenerator,
			TransactionRepository transactionRepository,
			LedgerEntryRepository ledgerEntryRepository) {
		this.accountRepository = accountRepository;
		this.accountBalanceRepository = accountBalanceRepository;
		this.accountNumberGenerator = accountNumberGenerator;
		this.transactionRepository = transactionRepository;
		this.ledgerEntryRepository = ledgerEntryRepository;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public MoneyMovementResult deposit(DepositWithdrawCommand command) {
		requirePositiveAmount(command.amount());
		Account customerAccount = getAccount(command.accountId());
		Account cashClearing = getOrCreateCashClearing(customerAccount.getCurrency());

		Transaction transaction =
				transactionRepository.save(new Transaction(command.description(), command.initiatingUserId()));
		ledgerEntryRepository.save(
				new LedgerEntry(transaction.getId(), cashClearing.getId(), command.amount().negate()));
		ledgerEntryRepository.save(
				new LedgerEntry(transaction.getId(), customerAccount.getId(), command.amount()));

		adjustBalance(cashClearing.getId(), command.amount().negate());
		Money newBalance = adjustBalance(customerAccount.getId(), command.amount());

		return new MoneyMovementResult(transaction.getId(), customerAccount.getId(), newBalance, Instant.now());
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public MoneyMovementResult withdraw(DepositWithdrawCommand command) {
		requirePositiveAmount(command.amount());
		Account customerAccount = getAccount(command.accountId());
		if (customerAccount.getStatus() != AccountStatus.ACTIVE) {
			throw new AccountNotActiveException(customerAccount.getId(), customerAccount.getStatus());
		}
		Account cashClearing = getOrCreateCashClearing(customerAccount.getCurrency());

		AccountBalance customerBalance = accountBalanceRepository
				.findById(customerAccount.getId())
				.orElseThrow(() -> new AccountNotFoundException(customerAccount.getId()));
		Money afterWithdrawal = customerBalance.getBalance().subtract(command.amount());
		if (afterWithdrawal.minorUnits() < 0) {
			throw new InsufficientFundsException(customerAccount.getId());
		}

		Transaction transaction =
				transactionRepository.save(new Transaction(command.description(), command.initiatingUserId()));
		ledgerEntryRepository.save(
				new LedgerEntry(transaction.getId(), customerAccount.getId(), command.amount().negate()));
		ledgerEntryRepository.save(
				new LedgerEntry(transaction.getId(), cashClearing.getId(), command.amount()));

		customerBalance.setBalance(afterWithdrawal);
		accountBalanceRepository.save(customerBalance);
		adjustBalance(cashClearing.getId(), command.amount());

		return new MoneyMovementResult(transaction.getId(), customerAccount.getId(), afterWithdrawal, Instant.now());
	}

	private Account getAccount(UUID accountId) {
		return accountRepository.findById(accountId).orElseThrow(() -> new AccountNotFoundException(accountId));
	}

	private Account getOrCreateCashClearing(String currency) {
		return accountRepository
				.findByTypeAndCurrency(AccountType.SYSTEM, currency)
				.orElseGet(() -> createCashClearing(currency));
	}

	private Account createCashClearing(String currency) {
		try {
			String accountNumber = accountNumberGenerator.generate();
			Account account = accountRepository.save(new Account(
					SystemAccounts.SYSTEM_CUSTOMER_ID, accountNumber, AccountType.SYSTEM, currency));
			accountBalanceRepository.save(new AccountBalance(account.getId(), Money.zero(currency)));
			return account;
		} catch (DataIntegrityViolationException e) {
			// Lost a race against a concurrent lazy-create; the partial unique index
			// (currency) WHERE type='SYSTEM' caught it - the other creator won.
			return accountRepository
					.findByTypeAndCurrency(AccountType.SYSTEM, currency)
					.orElseThrow(() -> e);
		}
	}

	private Money adjustBalance(UUID accountId, Money delta) {
		AccountBalance balance = accountBalanceRepository
				.findById(accountId)
				.orElseThrow(() -> new AccountNotFoundException(accountId));
		Money newBalance = balance.getBalance().add(delta);
		balance.setBalance(newBalance);
		accountBalanceRepository.save(balance);
		return newBalance;
	}

	private void requirePositiveAmount(Money amount) {
		if (amount.minorUnits() <= 0) {
			throw new InvalidAmountException(amount.minorUnits());
		}
	}
}
