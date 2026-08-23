package com.mel.fernbank.ledger.banking;

import com.mel.fernbank.ledger.audit.AuditLogger;
import com.mel.fernbank.ledger.domain.AccountBalance;
import com.mel.fernbank.ledger.repository.AccountBalanceRepository;
import com.mel.fernbank.ledger.repository.LedgerEntryRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Two invariants that should always hold if the ledger is correct: every currency's
 * ledger entries sum to zero (summed separately per currency - summing raw minor units
 * across currencies would be meaningless even if it happened to net to zero), and every
 * account's stored balance matches the sum of its own ledger entries.
 */
@Service
public class ReconciliationService {

	private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);

	private final LedgerEntryRepository ledgerEntryRepository;
	private final AccountBalanceRepository accountBalanceRepository;
	private final AuditLogger auditLogger;
	private final ReconciliationService self;

	public ReconciliationService(
			LedgerEntryRepository ledgerEntryRepository,
			AccountBalanceRepository accountBalanceRepository,
			AuditLogger auditLogger,
			@Lazy ReconciliationService self) {
		this.ledgerEntryRepository = ledgerEntryRepository;
		this.accountBalanceRepository = accountBalanceRepository;
		this.auditLogger = auditLogger;
		this.self = self;
	}

	@Transactional(readOnly = true)
	public ReconciliationReport reconcile() {
		List<ReconciliationDiscrepancy> discrepancies = new ArrayList<>();

		for (Object[] row : ledgerEntryRepository.sumAmountsByCurrency()) {
			String currency = (String) row[0];
			long sum = (Long) row[1];
			if (sum != 0) {
				discrepancies.add(new ReconciliationDiscrepancy(
						"Currency %s ledger entries sum to %d, expected 0".formatted(currency, sum)));
			}
		}

		Map<UUID, Long> entrySumsByAccount = ledgerEntryRepository.sumAmountsByAccount().stream()
				.collect(Collectors.toMap(row -> (UUID) row[0], row -> (Long) row[1]));

		for (AccountBalance balance : accountBalanceRepository.findAll()) {
			long expected = entrySumsByAccount.getOrDefault(balance.getAccountId(), 0L);
			long actual = balance.getBalance().minorUnits();
			if (expected != actual) {
				discrepancies.add(new ReconciliationDiscrepancy(
						"Account %s balance is %d but ledger entries sum to %d"
								.formatted(balance.getAccountId(), actual, expected)));
			}
		}

		return new ReconciliationReport(discrepancies);
	}

	@Scheduled(cron = "0 0 * * * *")
	public void scheduledCheck() {
		// self.reconcile(), not this.reconcile() - a self-invocation would bypass the
		// @Transactional(readOnly=true) proxy (see ScheduledTransferRunner's javadoc for
		// the same pitfall, caught there by a live run rather than the test suite).
		ReconciliationReport report = self.reconcile();
		if (!report.isHealthy()) {
			auditLogger.record(
					null,
					"reconciliation.discrepancy_found",
					Map.of("count", String.valueOf(report.discrepancies().size())));
			log.warn(
					"Reconciliation found {} discrepancy/discrepancies - see reconciliation report for detail",
					report.discrepancies().size());
		}
	}
}
