package com.mel.fernbank.ledger.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "ledger_entry")
public class LedgerEntry {

	@Id
	@GeneratedValue
	@UuidGenerator
	@Column(nullable = false, updatable = false)
	private UUID id;

	@Column(name = "transaction_id", nullable = false, updatable = false)
	private UUID transactionId;

	@Column(name = "account_id", nullable = false, updatable = false)
	private UUID accountId;

	@Embedded
	private Money amount;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected LedgerEntry() {
	}

	public LedgerEntry(UUID transactionId, UUID accountId, Money amount) {
		this.transactionId = transactionId;
		this.accountId = accountId;
		this.amount = amount;
	}

	public UUID getId() {
		return id;
	}

	public UUID getTransactionId() {
		return transactionId;
	}

	public UUID getAccountId() {
		return accountId;
	}

	public Money getAmount() {
		return amount;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
