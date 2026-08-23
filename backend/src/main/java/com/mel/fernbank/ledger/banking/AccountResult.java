package com.mel.fernbank.ledger.banking;

import com.mel.fernbank.ledger.domain.AccountStatus;
import com.mel.fernbank.ledger.domain.AccountType;
import java.time.Instant;
import java.util.UUID;

public record AccountResult(
		UUID id, String accountNumber, AccountType type, String currency, AccountStatus status, Instant createdAt) {}
