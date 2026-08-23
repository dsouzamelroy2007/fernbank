package com.mel.fernbank.ledger.repository;

import com.mel.fernbank.ledger.domain.RecoveryCode;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecoveryCodeRepository extends JpaRepository<RecoveryCode, UUID> {

	List<RecoveryCode> findByUserIdAndUsedAtIsNull(UUID userId);
}
