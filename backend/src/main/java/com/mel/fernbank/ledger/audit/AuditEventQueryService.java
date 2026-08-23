package com.mel.fernbank.ledger.audit;

import com.mel.fernbank.ledger.domain.AuditEvent;
import com.mel.fernbank.ledger.repository.AuditEventRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.domain.Window;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cursor-paginated audit-event reads via Spring Data's keyset {@link Window}/
 * {@link ScrollPosition} API, mirroring {@code banking.GetStatementService}'s pattern.
 * Controllers must not touch {@link AuditEventRepository} directly (ArchUnit-enforced).
 */
@Service
public class AuditEventQueryService {

	/** Auth-relevant event types surfaced by the self-service login-history endpoint. */
	private static final Set<String> LOGIN_HISTORY_EVENT_TYPES = Set.of(
			"auth.login_success",
			"auth.login_failure",
			"auth.mfa_verify_success",
			"auth.mfa_verify_failure",
			"auth.password_change_success",
			"auth.session_revoked");

	private final AuditEventRepository auditEventRepository;

	public AuditEventQueryService(AuditEventRepository auditEventRepository) {
		this.auditEventRepository = auditEventRepository;
	}

	@Transactional(readOnly = true)
	public AuditEventPage page(Instant from, Instant to, String cursor, int pageSize) {
		ScrollPosition position = decodeCursor(cursor);
		Window<AuditEvent> window = auditEventRepository.findByCreatedAtBetweenOrderByCreatedAtDescIdDesc(
				from == null ? Instant.EPOCH : from, to == null ? Instant.now() : to, Limit.of(pageSize), position);

		List<AuditEvent> events = window.getContent();
		String nextCursor =
				window.hasNext() && !events.isEmpty() ? encodeCursor(events.get(events.size() - 1)) : null;
		return new AuditEventPage(events, nextCursor, window.hasNext());
	}

	/** Self-service counterpart to {@link #page} - scoped to the caller's own login-related events. */
	@Transactional(readOnly = true)
	public AuditEventPage pageForUser(UUID actorUserId, Instant from, Instant to, String cursor, int pageSize) {
		ScrollPosition position = decodeCursor(cursor);
		Window<AuditEvent> window = auditEventRepository.findByActorUserIdAndEventTypeInAndCreatedAtBetweenOrderByCreatedAtDescIdDesc(
				actorUserId,
				LOGIN_HISTORY_EVENT_TYPES,
				from == null ? Instant.EPOCH : from,
				to == null ? Instant.now() : to,
				Limit.of(pageSize),
				position);

		List<AuditEvent> events = window.getContent();
		String nextCursor =
				window.hasNext() && !events.isEmpty() ? encodeCursor(events.get(events.size() - 1)) : null;
		return new AuditEventPage(events, nextCursor, window.hasNext());
	}

	private ScrollPosition decodeCursor(String cursor) {
		if (cursor == null || cursor.isBlank()) {
			return ScrollPosition.keyset();
		}
		String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
		String[] parts = decoded.split("\\|", 2);
		Instant createdAt = Instant.parse(parts[0]);
		UUID id = UUID.fromString(parts[1]);
		return ScrollPosition.forward(Map.of("createdAt", createdAt, "id", id));
	}

	private String encodeCursor(AuditEvent lastEvent) {
		String raw = lastEvent.getCreatedAt() + "|" + lastEvent.getId();
		return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
	}
}
