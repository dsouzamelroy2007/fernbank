package com.mel.fernbank.ledger.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "audit_event")
public class AuditEvent {

	@Id
	@GeneratedValue
	@UuidGenerator
	@Column(nullable = false, updatable = false)
	private UUID id;

	@Column(name = "actor_user_id", updatable = false)
	private UUID actorUserId;

	@Column(name = "event_type", nullable = false, updatable = false)
	private String eventType;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "metadata", updatable = false)
	private Map<String, Object> metadata;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected AuditEvent() {
	}

	public AuditEvent(UUID actorUserId, String eventType, Map<String, Object> metadata) {
		this.actorUserId = actorUserId;
		this.eventType = eventType;
		this.metadata = metadata;
	}

	public UUID getId() {
		return id;
	}

	public UUID getActorUserId() {
		return actorUserId;
	}

	public String getEventType() {
		return eventType;
	}

	public Map<String, Object> getMetadata() {
		return metadata;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
