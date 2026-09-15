package com.mel.fernbank.ledger.banking;

import com.mel.fernbank.ledger.domain.AccountStatus;
import com.mel.fernbank.ledger.domain.AccountType;
import com.mel.fernbank.ledger.domain.Money;
import java.time.Instant;
import java.util.UUID;

public record AccountDetail(
		UUID id,
		String accountNumber,
		AccountType type,
		AccountStatus status,
		Money balance,
		long accountVersion,
		long balanceVersion,
		Instant createdAt) {}
