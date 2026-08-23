package com.mel.fernbank.ledger.repository;

import com.mel.fernbank.ledger.domain.ScheduledTransfer;
import com.mel.fernbank.ledger.domain.ScheduledTransferStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface ScheduledTransferRepository extends JpaRepository<ScheduledTransfer, UUID> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	List<ScheduledTransfer> findByStatusAndScheduledForLessThanEqual(
			ScheduledTransferStatus status, Instant scheduledFor);
}
