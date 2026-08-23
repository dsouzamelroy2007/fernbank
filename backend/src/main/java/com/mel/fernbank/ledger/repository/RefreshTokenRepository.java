package com.mel.fernbank.ledger.repository;

import com.mel.fernbank.ledger.domain.RefreshToken;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

	Optional<RefreshToken> findByTokenHash(String tokenHash);

	List<RefreshToken> findByFamilyIdAndRevokedAtIsNull(UUID familyId);

	List<RefreshToken> findByUserIdAndRevokedAtIsNullOrderByIssuedAtDesc(UUID userId);

	Optional<RefreshToken> findByIdAndUserId(UUID id, UUID userId);
}
