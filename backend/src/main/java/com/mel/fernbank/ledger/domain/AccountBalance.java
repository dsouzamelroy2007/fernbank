package com.mel.fernbank.ledger.domain;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "account_balance")
public class AccountBalance {

	@Id
	@Column(name = "account_id", nullable = false, updatable = false)
	private UUID accountId;

	@Embedded
	@AttributeOverrides({
		@AttributeOverride(name = "minorUnits", column = @Column(name = "balance_minor_units", nullable = false)),
		@AttributeOverride(name = "currencyCode", column = @Column(name = "currency", nullable = false, length = 3))
	})
	private Money balance;

	@Version
	@Column(name = "version", nullable = false)
	private long version;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected AccountBalance() {
	}

	public AccountBalance(UUID accountId, Money balance) {
		this.accountId = accountId;
		this.balance = balance;
	}

	public UUID getAccountId() {
		return accountId;
	}

	public Money getBalance() {
		return balance;
	}

	public void setBalance(Money balance) {
		this.balance = balance;
	}

	public long getVersion() {
		return version;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
