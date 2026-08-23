package com.mel.fernbank.ledger.banking;

import java.util.List;

public record ReconciliationReport(List<ReconciliationDiscrepancy> discrepancies) {

	public boolean isHealthy() {
		return discrepancies.isEmpty();
	}
}
