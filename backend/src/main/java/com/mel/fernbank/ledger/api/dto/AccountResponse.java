package com.mel.fernbank.ledger.api.dto;

import com.mel.fernbank.ledger.domain.AccountStatus;
import com.mel.fernbank.ledger.domain.AccountType;
import java.time.Instant;
import java.util.UUID;

public record AccountResponse(
		UUID id, String accountNumber, AccountType type, AccountStatus status, MoneyDto balance, Instant createdAt) {}
