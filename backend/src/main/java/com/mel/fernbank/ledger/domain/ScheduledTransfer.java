package com.mel.fernbank.ledger.domain;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "scheduled_transfer")
public class ScheduledTransfer {

	@Id
	@GeneratedValue
	@UuidGenerator
	@Column(nullable = false, updatable = false)
	private UUID id;

	@Column(name = "source_account_id", nullable = false, updatable = false)
	private UUID sourceAccountId;

	@Column(name = "destination_account_id", nullable = false, updatable = false)
	private UUID destinationAccountId;

	@Embedded
	@AttributeOverrides({
		@AttributeOverride(name = "minorUnits", column = @Column(name = "amount_minor_units", nullable = false)),
		@AttributeOverride(name = "currencyCode", column = @Column(name = "currency", nullable = false, length = 3))
	})
	private Money amount;

	@Column(name = "description")
	private String description;

	@Column(name = "scheduled_for", nullable = false)
	private Instant scheduledFor;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	private ScheduledTransferStatus status;

	@Column(name = "created_by_user_id", nullable = false, updatable = false)
	private UUID createdByUserId;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "executed_at")
	private Instant executedAt;

	@Column(name = "failure_reason")
	private String failureReason;

	@Column(name = "attempt_count", nullable = false)
	private int attemptCount = 0;

	protected ScheduledTransfer() {}

	public ScheduledTransfer(
			UUID sourceAccountId,
			UUID destinationAccountId,
			Money amount,
			String description,
			Instant scheduledFor,
			UUID createdByUserId) {
		this.sourceAccountId = sourceAccountId;
		this.destinationAccountId = destinationAccountId;
		this.amount = amount;
		this.description = description;
		this.scheduledFor = scheduledFor;
		this.createdByUserId = createdByUserId;
		this.status = ScheduledTransferStatus.PENDING;
	}

	public UUID getId() {
		return id;
	}

	public UUID getSourceAccountId() {
		return sourceAccountId;
	}

	public UUID getDestinationAccountId() {
		return destinationAccountId;
	}

	public Money getAmount() {
		return amount;
	}

	public String getDescription() {
		return description;
	}

	public Instant getScheduledFor() {
		return scheduledFor;
	}

	public ScheduledTransferStatus getStatus() {
		return status;
	}

	public UUID getCreatedByUserId() {
		return createdByUserId;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getExecutedAt() {
		return executedAt;
	}

	public String getFailureReason() {
		return failureReason;
	}

	public int getAttemptCount() {
		return attemptCount;
	}

	public void markExecuted() {
		this.status = ScheduledTransferStatus.EXECUTED;
		this.executedAt = Instant.now();
	}

	/** Records a failed execution attempt without changing status - stays PENDING so
	 * {@code ScheduledTransferRunner}'s own due-transfer query picks it back up on the
	 * next run. The caller (which owns the retry-limit policy) calls {@link
	 * #markFailed} separately once attempts are exhausted. */
	public void recordFailedAttempt(String reason) {
		this.attemptCount++;
		this.failureReason = reason;
	}

	public void markFailed(String reason) {
		this.status = ScheduledTransferStatus.FAILED;
		this.executedAt = Instant.now();
		this.failureReason = reason;
	}
}
