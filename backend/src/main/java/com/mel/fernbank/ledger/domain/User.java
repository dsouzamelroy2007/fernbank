package com.mel.fernbank.ledger.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(
		name = "app_user",
		uniqueConstraints = {
			@UniqueConstraint(name = "uk_app_user_customer_id", columnNames = "customer_id"),
			@UniqueConstraint(name = "uk_app_user_email", columnNames = "email")
		})
public class User {

	private static final int LOCKOUT_THRESHOLD = 5;
	private static final int LOCKOUT_BASE_MINUTES = 1;
	private static final int LOCKOUT_MAX_MINUTES = 60;

	@Id
	@GeneratedValue
	@UuidGenerator
	@Column(nullable = false, updatable = false)
	private UUID id;

	@Column(name = "customer_id", nullable = false, updatable = false)
	private UUID customerId;

	@Column(name = "email", nullable = false)
	private String email;

	@Column(name = "password_hash", nullable = false)
	private String passwordHash;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	private UserStatus status;

	@ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(name = "user_role", joinColumns = @JoinColumn(name = "user_id"))
	@Enumerated(EnumType.STRING)
	@Column(name = "role", nullable = false, length = 20)
	private Set<Role> roles = new HashSet<>();

	@Convert(converter = MfaSecretConverter.class)
	@Column(name = "mfa_secret")
	private String mfaSecret;

	@Column(name = "mfa_enabled", nullable = false)
	private boolean mfaEnabled;

	@Column(name = "failed_login_attempts", nullable = false)
	private int failedLoginAttempts;

	@Column(name = "locked_until")
	private Instant lockedUntil;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected User() {
	}

	public User(UUID customerId, String email, String passwordHash) {
		this.customerId = customerId;
		this.email = email;
		this.passwordHash = passwordHash;
		this.status = UserStatus.ACTIVE;
		this.roles = new HashSet<>(Set.of(Role.CUSTOMER));
	}

	public UUID getId() {
		return id;
	}

	public UUID getCustomerId() {
		return customerId;
	}

	public String getEmail() {
		return email;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	public void setPasswordHash(String passwordHash) {
		this.passwordHash = passwordHash;
	}

	public UserStatus getStatus() {
		return status;
	}

	public void setStatus(UserStatus status) {
		this.status = status;
	}

	public Set<Role> getRoles() {
		return Set.copyOf(roles);
	}

	public void grantRole(Role role) {
		roles.add(role);
	}

	public String getMfaSecret() {
		return mfaSecret;
	}

	public void setMfaSecret(String mfaSecret) {
		this.mfaSecret = mfaSecret;
	}

	public boolean isMfaEnabled() {
		return mfaEnabled;
	}

	public void enableMfa() {
		this.mfaEnabled = true;
	}

	public int getFailedLoginAttempts() {
		return failedLoginAttempts;
	}

	public Instant getLockedUntil() {
		return lockedUntil;
	}

	public boolean isLocked() {
		return lockedUntil != null && lockedUntil.isAfter(Instant.now());
	}

	public void recordFailedLogin() {
		failedLoginAttempts++;
		if (failedLoginAttempts >= LOCKOUT_THRESHOLD) {
			int overBy = failedLoginAttempts - LOCKOUT_THRESHOLD;
			long minutes = Math.min(LOCKOUT_MAX_MINUTES, LOCKOUT_BASE_MINUTES * (1L << Math.min(overBy, 10)));
			lockedUntil = Instant.now().plus(Duration.ofMinutes(minutes));
		}
	}

	public void recordSuccessfulLogin() {
		failedLoginAttempts = 0;
		lockedUntil = null;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
