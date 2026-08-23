package com.mel.fernbank.ledger.idempotency;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mel.fernbank.ledger.domain.IdempotencyRecord;
import com.mel.fernbank.ledger.observability.AppMetrics;
import com.mel.fernbank.ledger.repository.IdempotencyRecordRepository;
import com.mel.fernbank.ledger.security.TokenHasher;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * Reusable Idempotency-Key enforcement for any write operation, not just banking's -
 * hence its own top-level package rather than living inside {@code banking}.
 *
 * <p>Boot 4's Jackson auto-configuration only produces the new Jackson 3 {@code
 * tools.jackson} {@code JsonMapper}, not a classic {@code com.fasterxml.jackson}
 * {@code ObjectMapper} bean (verified while building this) - a local {@code
 * ObjectMapper} is constructed here rather than adopting the new API for one small
 * internal utility.
 */
@Component
public class IdempotencyGuard {

	private static final Duration RECORD_TTL = Duration.ofHours(24);

	private final IdempotencyRecordRepository idempotencyRecordRepository;
	private final AppMetrics appMetrics;
	// Money (and similar hand-written value objects) expose record-style accessors like
	// minorUnits() rather than getMinorUnits(), which Jackson's default getter-based
	// introspection won't see - read/write fields directly instead.
	private final ObjectMapper objectMapper = new ObjectMapper()
			.registerModule(new JavaTimeModule())
			.setVisibility(PropertyAccessor.FIELD, Visibility.ANY)
			.setVisibility(PropertyAccessor.GETTER, Visibility.NONE)
			.setVisibility(PropertyAccessor.IS_GETTER, Visibility.NONE);

	public IdempotencyGuard(IdempotencyRecordRepository idempotencyRecordRepository, AppMetrics appMetrics) {
		this.idempotencyRecordRepository = idempotencyRecordRepository;
		this.appMetrics = appMetrics;
	}

	public <T> T execute(
			UUID userId, String idempotencyKey, Object requestPayload, Class<T> resultType, Supplier<T> operation) {
		String requestHash = TokenHasher.sha256Hex(canonicalize(requestPayload));

		Optional<IdempotencyRecord> existing =
				idempotencyRecordRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
		if (existing.isPresent() && existing.get().getExpiresAt().isAfter(Instant.now())) {
			IdempotencyRecord record = existing.get();
			if (!record.getRequestHash().equals(requestHash)) {
				throw new IdempotencyConflictException(idempotencyKey);
			}
			appMetrics.recordIdempotentReplay(resultType.getSimpleName());
			return deserialize(record.getResponseBody(), resultType);
		}

		T result = operation.get();
		IdempotencyRecord record =
				new IdempotencyRecord(userId, idempotencyKey, requestHash, Instant.now().plus(RECORD_TTL));
		record.complete(200, serialize(result));
		idempotencyRecordRepository.save(record);
		return result;
	}

	private String canonicalize(Object payload) {
		try {
			return objectMapper.writeValueAsString(payload);
		} catch (Exception e) {
			throw new IllegalStateException("Failed to canonicalize idempotency request payload", e);
		}
	}

	private String serialize(Object result) {
		try {
			return objectMapper.writeValueAsString(result);
		} catch (Exception e) {
			throw new IllegalStateException("Failed to serialize idempotency result", e);
		}
	}

	private <T> T deserialize(String json, Class<T> type) {
		try {
			return objectMapper.readValue(json, type);
		} catch (Exception e) {
			throw new IllegalStateException("Failed to deserialize stored idempotency result", e);
		}
	}
}
