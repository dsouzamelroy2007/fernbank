package com.mel.fernbank.ledger.banking;

import com.mel.fernbank.ledger.audit.AuditLogger;
import com.mel.fernbank.ledger.idempotency.IdempotencyGuard;
import com.mel.fernbank.ledger.observability.AppMetrics;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class TransferService {

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
		return idempotencyGuard.execute(
				command.initiatingUserId(),
				command.idempotencyKey(),
				command,
				TransferResult.class,
				() -> {
					// A retried optimistic-lock attempt inside retryTemplate isn't a distinct transfer
					// outcome - only the final result of the whole call (success or the exception that
					// ultimately escapes) is counted here, mirroring the idempotency boundary above.
					TransferResult result;
					try {
						result = retryTemplate.execute(() -> executor.transfer(command));
					} catch (RuntimeException e) {
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
					return result;
				});
	}
}
