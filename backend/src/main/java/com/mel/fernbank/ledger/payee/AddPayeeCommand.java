package com.mel.fernbank.ledger.payee;

import java.util.UUID;

public record AddPayeeCommand(
		UUID customerId, String name, String targetAccountNumber, UUID actingUserId, String idempotencyKey) {}
