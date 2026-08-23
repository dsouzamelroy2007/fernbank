package com.mel.fernbank.ledger.domain;

public enum AccountType {
	CHECKING,
	SAVINGS,
	/** Internal counterparty for deposits/withdrawals - never customer-facing. */
	SYSTEM
}
