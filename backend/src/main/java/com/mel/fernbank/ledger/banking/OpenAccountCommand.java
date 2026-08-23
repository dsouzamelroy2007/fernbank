package com.mel.fernbank.ledger.banking;

import com.mel.fernbank.ledger.domain.AccountType;
import java.util.UUID;

public record OpenAccountCommand(UUID customerId, AccountType type, String currency, UUID actingUserId) {}
