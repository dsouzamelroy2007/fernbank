package com.mel.fernbank.ledger.banking;

import com.mel.fernbank.ledger.domain.ScheduledTransfer;
import com.mel.fernbank.ledger.domain.ScheduledTransferStatus;
import com.mel.fernbank.ledger.repository.ScheduledTransferRepository;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Executes due scheduled transfers. {@link ScheduledTransferRepository#
 * findByStatusAndScheduledForLessThanEqual} takes a {@code PESSIMISTIC_WRITE} row lock
 * ("a DB lock", per the plan) for the duration of this method's transaction; each
 * individual transfer still gets its own fresh transaction via {@link TransferExecutor}'s
 * {@code REQUIRES_NEW}, so one failing transfer doesn't roll back the batch or the lock
 * holder's view of the others. Not safe against a second app instance racing this one
 * (would need {@code SKIP LOCKED}) - out of scope, this app runs as one instance.
 *
 * <p>{@code run()} calls {@code executeDueTransfers} through a self-injected proxy
 * ({@code self}), not {@code this} - a plain {@code this.executeDueTransfers(...)} call
 * is a self-invocation that bypasses Spring's transactional proxy entirely, silently
 * running the {@code @Lock}-annotated query with no active transaction. Direct test
 * calls to {@code executeDueTransfers} (a different caller, not a self-invocation)
 * don't hit this - which is exactly why it only surfaced running the real app, not in
 * the test suite.
 */
@Component
public class ScheduledTransferRunner {

	private static final Logger log = LoggerFactory.getLogger(ScheduledTransferRunner.class);
	private static final int MAX_ATTEMPTS = 3;

	private final ScheduledTransferRepository scheduledTransferRepository;
	private final TransferService transferService;
	private final ScheduledTransferRunner self;

	public ScheduledTransferRunner(
			ScheduledTransferRepository scheduledTransferRepository,
			TransferService transferService,
			@Lazy ScheduledTransferRunner self) {
		this.scheduledTransferRepository = scheduledTransferRepository;
		this.transferService = transferService;
		this.self = self;
	}

	@Scheduled(fixedDelay = 60_000)
	public void run() {
		self.executeDueTransfers(Instant.now());
	}

	@Transactional
	public void executeDueTransfers(Instant asOf) {
		List<ScheduledTransfer> due =
				scheduledTransferRepository.findByStatusAndScheduledForLessThanEqual(ScheduledTransferStatus.PENDING, asOf);
		for (ScheduledTransfer scheduled : due) {
			try {
				transferService.transfer(new TransferCommand(
						scheduled.getSourceAccountId(),
						scheduled.getDestinationAccountId(),
						scheduled.getAmount(),
						scheduled.getDescription(),
						scheduled.getCreatedByUserId(),
						"scheduled-transfer-" + scheduled.getId(),
						false));
				scheduled.markExecuted();
			} catch (RuntimeException e) {
				scheduled.recordFailedAttempt(e.getMessage());
				if (scheduled.getAttemptCount() >= MAX_ATTEMPTS) {
					scheduled.markFailed(e.getMessage());
					log.warn(
							"Scheduled transfer {} (source={}, destination={}) permanently failed after {} attempts: {}",
							scheduled.getId(),
							scheduled.getSourceAccountId(),
							scheduled.getDestinationAccountId(),
							scheduled.getAttemptCount(),
							e.getMessage());
				} else {
					// Left PENDING on purpose - findByStatusAndScheduledForLessThanEqual
					// above already selects it again on the next tick since scheduledFor
					// stays in the past, no extra rescheduling logic needed.
					log.warn(
							"Scheduled transfer {} (source={}, destination={}) failed, attempt {}/{}, will retry: {}",
							scheduled.getId(),
							scheduled.getSourceAccountId(),
							scheduled.getDestinationAccountId(),
							scheduled.getAttemptCount(),
							MAX_ATTEMPTS,
							e.getMessage());
				}
			}
		}
		scheduledTransferRepository.saveAll(due);
	}
}
