package com.mel.fernbank.ledger.audit;

import com.mel.fernbank.ledger.domain.AuditEvent;
import com.mel.fernbank.ledger.repository.AuditEventRepository;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class AuditLogger {

	private final AuditEventRepository auditEventRepository;

	public AuditLogger(AuditEventRepository auditEventRepository) {
		this.auditEventRepository = auditEventRepository;
	}

	public void record(UUID actorUserId, String eventType, Map<String, Object> metadata) {
		auditEventRepository.save(new AuditEvent(actorUserId, eventType, metadata));
	}

	public void record(UUID actorUserId, String eventType) {
		record(actorUserId, eventType, Map.of());
	}
}
