package com.mel.fernbank.ledger.banking;

import com.mel.fernbank.ledger.domain.Money;
import java.util.UUID;

public record DepositWithdrawCommand(
		UUID accountId, Money amount, String description, UUID initiatingUserId, String idempotencyKey) {}
