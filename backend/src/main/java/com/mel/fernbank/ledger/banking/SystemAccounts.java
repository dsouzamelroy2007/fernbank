package com.mel.fernbank.ledger.banking;

import java.util.UUID;

/** The one well-known customer row seeded by {@code V3__system_accounts.sql}. */
final class SystemAccounts {

	static final UUID SYSTEM_CUSTOMER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

	private SystemAccounts() {}
}
