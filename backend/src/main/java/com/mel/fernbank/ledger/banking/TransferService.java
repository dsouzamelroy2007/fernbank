package com.mel.fernbank.ledger.banking;

import com.mel.fernbank.ledger.audit.AuditLogger;
import com.mel.fernbank.ledger.idempotency.IdempotencyGuard;
import com.mel.fernbank.ledger.observability.AppMetrics;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class TransferService {

	private static final Logger log = LoggerFactory.getLogger(TransferService.class);

	private final TransferExecutor executor;
	private final OptimisticRetryTemplate retryTemplate;
	private final IdempotencyGuard idempotencyGuard;
	private final AuditLogger auditLogger;
	private final AppMetrics appMetrics;

	public TransferService(
			TransferExecutor executor,
			OptimisticRetryTemplate retryTemplate,
			IdempotencyGuard idempotencyGuard,
			AuditLogger auditLogger,
			AppMetrics appMetrics) {
		this.executor = executor;
		this.retryTemplate = retryTemplate;
		this.idempotencyGuard = idempotencyGuard;
		this.auditLogger = auditLogger;
		this.appMetrics = appMetrics;
	}

	public TransferResult transfer(TransferCommand command) {
		log.debug(
				"Processing transfer: sourceAccountId={}, destinationAccountId={}, userId={}, amountMinorUnits={}",
				command.sourceAccountId(),
				command.destinationAccountId(),
				command.initiatingUserId(),
				command.amount().minorUnits());
		return idempotencyGuard.execute(
				command.initiatingUserId(),
				command.idempotencyKey(),
				command,
				TransferResult.class,
				() -> {
					TransferResult result;
					try {
						result = retryTemplate.execute(() -> executor.transfer(command));
					} catch (RuntimeException e) {
						log.warn(
								"Transfer failed: sourceAccountId={}, destinationAccountId={}, userId={}, cause={}",
								command.sourceAccountId(),
								command.destinationAccountId(),
								command.initiatingUserId(),
								e.getClass().getSimpleName());
						appMetrics.recordTransfer(e.getClass().getSimpleName());
						throw e;
					}
					appMetrics.recordTransfer("success");
					auditLogger.record(
							command.initiatingUserId(),
							"account.transfer",
							Map.of(
									"sourceAccountId", command.sourceAccountId().toString(),
									"destinationAccountId", command.destinationAccountId().toString(),
									"amountMinorUnits", String.valueOf(command.amount().minorUnits())));
					log.info(
							"Transfer completed: transactionId={}, sourceAccountId={}, destinationAccountId={}",
							result.transactionId(),
							result.sourceAccountId(),
							result.destinationAccountId());
					return result;
				});
	}
}
