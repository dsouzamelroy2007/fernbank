package com.mel.fernbank.ledger.repository;

import com.mel.fernbank.ledger.domain.AuditEvent;
import java.time.Instant;
import java.util.Collection;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.domain.Window;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {

	Window<AuditEvent> findByCreatedAtBetweenOrderByCreatedAtDescIdDesc(
			Instant from, Instant to, Limit limit, ScrollPosition position);

	Window<AuditEvent> findByActorUserIdAndEventTypeInAndCreatedAtBetweenOrderByCreatedAtDescIdDesc(
			UUID actorUserId, Collection<String> eventTypes, Instant from, Instant to, Limit limit, ScrollPosition position);
}
