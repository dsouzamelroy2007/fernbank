package com.mel.fernbank.ledger.repository;

import com.mel.fernbank.ledger.domain.LedgerEntry;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.domain.Window;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

	List<LedgerEntry> findByTransactionId(UUID transactionId);

	List<LedgerEntry> findByAccountIdOrderByCreatedAtDesc(UUID accountId);

	Window<LedgerEntry> findByAccountIdAndCreatedAtBetweenOrderByCreatedAtDescIdDesc(
			UUID accountId, Instant from, Instant to, Limit limit, ScrollPosition position);

	/** Unpaginated variant for CSV/PDF export - callers must cap the result size themselves. */
	List<LedgerEntry> findByAccountIdAndCreatedAtBetweenOrderByCreatedAtDescIdDesc(UUID accountId, Instant from, Instant to);

	@Query("SELECT le.amount.currencyCode, SUM(le.amount.minorUnits) FROM LedgerEntry le GROUP BY le.amount.currencyCode")
	List<Object[]> sumAmountsByCurrency();

	@Query("SELECT le.accountId, SUM(le.amount.minorUnits) FROM LedgerEntry le GROUP BY le.accountId")
	List<Object[]> sumAmountsByAccount();
}
