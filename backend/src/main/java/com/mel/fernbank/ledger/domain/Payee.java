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
@Table(name = "payee")
public class Payee {

	@Id
	@GeneratedValue
	@UuidGenerator
	@Column(nullable = false, updatable = false)
	private UUID id;

	@Column(name = "customer_id", nullable = false, updatable = false)
	private UUID customerId;

	@Column(name = "name", nullable = false)
	private String name;

	@Column(name = "target_account_number", nullable = false, length = 34)
	private String targetAccountNumber;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected Payee() {
	}

	public Payee(UUID customerId, String name, String targetAccountNumber) {
		this.customerId = customerId;
		this.name = name;
		this.targetAccountNumber = targetAccountNumber;
	}

	public UUID getId() {
		return id;
	}

	public UUID getCustomerId() {
		return customerId;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getTargetAccountNumber() {
		return targetAccountNumber;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
