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
