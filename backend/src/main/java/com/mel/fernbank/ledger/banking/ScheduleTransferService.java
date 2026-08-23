package com.mel.fernbank.ledger.banking;

import com.mel.fernbank.ledger.audit.AuditLogger;
import com.mel.fernbank.ledger.domain.Account;
import com.mel.fernbank.ledger.domain.AccountBalance;
import com.mel.fernbank.ledger.domain.ScheduledTransfer;
import com.mel.fernbank.ledger.repository.AccountBalanceRepository;
import com.mel.fernbank.ledger.repository.AccountRepository;
import com.mel.fernbank.ledger.repository.ScheduledTransferRepository;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ScheduleTransferService {

	private final ScheduledTransferRepository scheduledTransferRepository;
	private final AccountRepository accountRepository;
	private final AccountBalanceRepository accountBalanceRepository;
	private final AuditLogger auditLogger;

	public ScheduleTransferService(
			ScheduledTransferRepository scheduledTransferRepository,
			AccountRepository accountRepository,
			AccountBalanceRepository accountBalanceRepository,
			AuditLogger auditLogger) {
		this.scheduledTransferRepository = scheduledTransferRepository;
		this.accountRepository = accountRepository;
		this.accountBalanceRepository = accountBalanceRepository;
		this.auditLogger = auditLogger;
	}

	@Transactional
	public ScheduledTransfer schedule(ScheduleTransferCommand command) {
		if (command.amount().minorUnits() <= 0) {
			throw new InvalidAmountException(command.amount().minorUnits());
		}
		if (command.sourceAccountId().equals(command.destinationAccountId())) {
			throw new SameAccountTransferException(command.sourceAccountId());
		}
		Account source = accountRepository
				.findById(command.sourceAccountId())
				.orElseThrow(() -> new AccountNotFoundException(command.sourceAccountId()));
		Account destination = accountRepository
				.findById(command.destinationAccountId())
				.orElseThrow(() -> new AccountNotFoundException(command.destinationAccountId()));
		if (!source.getCurrency().equals(destination.getCurrency())) {
			throw new CurrencyMismatchException(source.getCurrency(), destination.getCurrency());
		}
		// Best-effort only, not a real hold: nothing stops the source balance from
		// being spent elsewhere between now and scheduledFor, so ScheduledTransferRunner
		// can still see InsufficientFundsException at execution time regardless of this
		// check. This just rejects the common case (scheduling more than you have)
		// immediately instead of silently accepting it and failing days later.
		AccountBalance sourceBalance = accountBalanceRepository
				.findById(source.getId())
				.orElseThrow(() -> new AccountNotFoundException(source.getId()));
		if (sourceBalance.getBalance().subtract(command.amount()).minorUnits() < 0) {
			throw new InsufficientFundsException(source.getId());
		}

		ScheduledTransfer scheduled = scheduledTransferRepository.save(new ScheduledTransfer(
				source.getId(),
				destination.getId(),
				command.amount(),
				command.description(),
				command.scheduledFor(),
				command.initiatingUserId()));

		auditLogger.record(
				command.initiatingUserId(),
				"account.transfer_scheduled",
				Map.of("scheduledTransferId", scheduled.getId().toString()));

		return scheduled;
	}
}
