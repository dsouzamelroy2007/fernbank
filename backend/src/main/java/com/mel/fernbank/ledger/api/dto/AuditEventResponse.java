package com.mel.fernbank.ledger.api.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AuditEventResponse(UUID id, UUID actorUserId, String eventType, Map<String, Object> metadata, Instant createdAt) {}
