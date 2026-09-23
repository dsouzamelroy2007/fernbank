package com.mel.fernbank.ledger.banking;

import com.mel.fernbank.ledger.audit.AuditLogger;
import com.mel.fernbank.ledger.idempotency.IdempotencyGuard;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class DepositWithdrawService {

	private static final Logger log = LoggerFactory.getLogger(DepositWithdrawService.class);

	private final DepositWithdrawExecutor executor;
	private final OptimisticRetryTemplate retryTemplate;
	private final IdempotencyGuard idempotencyGuard;
	private final AuditLogger auditLogger;

	public DepositWithdrawService(
			DepositWithdrawExecutor executor,
			OptimisticRetryTemplate retryTemplate,
			IdempotencyGuard idempotencyGuard,
			AuditLogger auditLogger) {
		this.executor = executor;
		this.retryTemplate = retryTemplate;
		this.idempotencyGuard = idempotencyGuard;
		this.auditLogger = auditLogger;
	}

	public MoneyMovementResult deposit(DepositWithdrawCommand command) {
		log.debug(
				"Processing deposit: accountId={}, userId={}, amountMinorUnits={}",
				command.accountId(),
				command.initiatingUserId(),
				command.amount().minorUnits());
		return idempotencyGuard.execute(
				command.initiatingUserId(),
				command.idempotencyKey(),
				command,
				MoneyMovementResult.class,
				() -> {
					MoneyMovementResult result = retryTemplate.execute(() -> executor.deposit(command));
					auditLogger.record(
							command.initiatingUserId(),
							"account.deposit",
							Map.of(
									"accountId", command.accountId().toString(),
									"amountMinorUnits", String.valueOf(command.amount().minorUnits())));
					log.info(
							"Deposit completed: accountId={}, transactionId={}, newBalanceMinorUnits={}",
							result.accountId(),
							result.transactionId(),
							result.newBalance().minorUnits());
					return result;
				});
	}

	public MoneyMovementResult withdraw(DepositWithdrawCommand command) {
		log.debug(
				"Processing withdrawal: accountId={}, userId={}, amountMinorUnits={}",
				command.accountId(),
				command.initiatingUserId(),
				command.amount().minorUnits());
		return idempotencyGuard.execute(
				command.initiatingUserId(),
				command.idempotencyKey(),
				command,
				MoneyMovementResult.class,
				() -> {
					MoneyMovementResult result = retryTemplate.execute(() -> executor.withdraw(command));
					auditLogger.record(
							command.initiatingUserId(),
							"account.withdraw",
							Map.of(
									"accountId", command.accountId().toString(),
									"amountMinorUnits", String.valueOf(command.amount().minorUnits())));
					log.info(
							"Withdrawal completed: accountId={}, transactionId={}, newBalanceMinorUnits={}",
							result.accountId(),
							result.transactionId(),
							result.newBalance().minorUnits());
					return result;
				});
	}
}
