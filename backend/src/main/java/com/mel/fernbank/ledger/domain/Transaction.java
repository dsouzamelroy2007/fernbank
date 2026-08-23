package com.mel.fernbank.ledger.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "transaction")
public class Transaction {

	@Id
	@GeneratedValue
	@UuidGenerator
	@Column(nullable = false, updatable = false)
	private UUID id;

	@Column(name = "description")
	private String description;

	@Column(name = "created_by_user_id", updatable = false)
	private UUID createdByUserId;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected Transaction() {
	}

	public Transaction(String description, UUID createdByUserId) {
		this.description = description;
		this.createdByUserId = createdByUserId;
	}

	public UUID getId() {
		return id;
	}

	public String getDescription() {
		return description;
	}

	public UUID getCreatedByUserId() {
		return createdByUserId;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
