package com.mel.fernbank.ledger.banking;

import com.mel.fernbank.ledger.domain.Account;
import com.mel.fernbank.ledger.domain.AccountBalance;
import com.mel.fernbank.ledger.domain.AccountStatus;
import com.mel.fernbank.ledger.domain.LedgerEntry;
import com.mel.fernbank.ledger.domain.Money;
import com.mel.fernbank.ledger.domain.Transaction;
import com.mel.fernbank.ledger.repository.AccountBalanceRepository;
import com.mel.fernbank.ledger.repository.AccountRepository;
import com.mel.fernbank.ledger.repository.LedgerEntryRepository;
import com.mel.fernbank.ledger.repository.TransactionRepository;
import com.mel.fernbank.ledger.security.FernbankProperties;
import com.mel.fernbank.ledger.security.StepUpRequiredException;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
class TransferExecutor {

	private final AccountRepository accountRepository;
	private final AccountBalanceRepository accountBalanceRepository;
	private final TransactionRepository transactionRepository;
	private final LedgerEntryRepository ledgerEntryRepository;
	private final FernbankProperties properties;

	TransferExecutor(
			AccountRepository accountRepository,
			AccountBalanceRepository accountBalanceRepository,
			TransactionRepository transactionRepository,
			LedgerEntryRepository ledgerEntryRepository,
			FernbankProperties properties) {
		this.accountRepository = accountRepository;
		this.accountBalanceRepository = accountBalanceRepository;
		this.transactionRepository = transactionRepository;
		this.ledgerEntryRepository = ledgerEntryRepository;
		this.properties = properties;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public TransferResult transfer(TransferCommand command) {
		Money amount = command.amount();
		if (amount.minorUnits() <= 0) {
			throw new InvalidAmountException(amount.minorUnits());
		}
		if (command.sourceAccountId().equals(command.destinationAccountId())) {
			throw new SameAccountTransferException(command.sourceAccountId());
		}
		if (amount.minorUnits() >= properties.auth().stepUpThresholdMinorUnits() && !command.stepUpVerified()) {
			throw new StepUpRequiredException();
		}

		Account source = getAccount(command.sourceAccountId());
		Account destination = getAccount(command.destinationAccountId());
		if (!source.getCurrency().equals(destination.getCurrency())) {
			throw new CurrencyMismatchException(source.getCurrency(), destination.getCurrency());
		}
		if (source.getStatus() != AccountStatus.ACTIVE) {
			throw new AccountNotActiveException(source.getId(), source.getStatus());
		}

		AccountBalance sourceBalance = accountBalanceRepository
				.findById(source.getId())
				.orElseThrow(() -> new AccountNotFoundException(source.getId()));
		Money sourceNewBalance = sourceBalance.getBalance().subtract(amount);
		if (sourceNewBalance.minorUnits() < 0) {
			throw new InsufficientFundsException(source.getId());
		}

		Transaction transaction =
				transactionRepository.save(new Transaction(command.description(), command.initiatingUserId()));
		ledgerEntryRepository.save(new LedgerEntry(transaction.getId(), source.getId(), amount.negate()));
		ledgerEntryRepository.save(new LedgerEntry(transaction.getId(), destination.getId(), amount));

		sourceBalance.setBalance(sourceNewBalance);
		accountBalanceRepository.save(sourceBalance);

		AccountBalance destinationBalance = accountBalanceRepository
				.findById(destination.getId())
				.orElseThrow(() -> new AccountNotFoundException(destination.getId()));
		Money destinationNewBalance = destinationBalance.getBalance().add(amount);
		destinationBalance.setBalance(destinationNewBalance);
		accountBalanceRepository.save(destinationBalance);

		return new TransferResult(
				transaction.getId(),
				source.getId(),
				sourceNewBalance,
				destination.getId(),
				destinationNewBalance,
				Instant.now());
	}

	private Account getAccount(UUID accountId) {
		return accountRepository.findById(accountId).orElseThrow(() -> new AccountNotFoundException(accountId));
	}
}
