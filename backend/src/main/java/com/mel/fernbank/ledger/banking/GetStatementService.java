package com.mel.fernbank.ledger.banking;

import com.mel.fernbank.ledger.domain.LedgerEntry;
import com.mel.fernbank.ledger.domain.Transaction;
import com.mel.fernbank.ledger.repository.LedgerEntryRepository;
import com.mel.fernbank.ledger.repository.TransactionRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.domain.Window;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cursor pagination via Spring Data's keyset {@link Window}/{@link ScrollPosition} API
 * (current for this Spring Data generation) rather than OFFSET-based paging, which
 * degrades on large statements.
 */
@Service
public class GetStatementService {

	/** Row cap for the unpaginated CSV/PDF export path - narrow the date range past this. */
	private static final int MAX_EXPORT_ROWS = 10_000;

	private final LedgerEntryRepository ledgerEntryRepository;
	private final TransactionRepository transactionRepository;

	public GetStatementService(LedgerEntryRepository ledgerEntryRepository, TransactionRepository transactionRepository) {
		this.ledgerEntryRepository = ledgerEntryRepository;
		this.transactionRepository = transactionRepository;
	}

	@Transactional(readOnly = true)
	public StatementPage getStatement(StatementQuery query) {
		ScrollPosition position = decodeCursor(query.cursor());
		Window<LedgerEntry> window = ledgerEntryRepository.findByAccountIdAndCreatedAtBetweenOrderByCreatedAtDescIdDesc(
				query.accountId(), query.from(), query.to(), Limit.of(query.pageSize()), position);

		List<StatementEntry> entries = toStatementEntries(window.getContent());

		String nextCursor = window.hasNext() && !window.getContent().isEmpty()
				? encodeCursor(window.getContent().get(window.getContent().size() - 1))
				: null;

		return new StatementPage(entries, nextCursor, window.hasNext());
	}

	/**
	 * Unpaginated read of a full date range for CSV/PDF export. Capped at
	 * {@link #MAX_EXPORT_ROWS} to avoid an unbounded query - throws
	 * {@link StatementRangeTooLargeException} rather than silently truncating.
	 */
	@Transactional(readOnly = true)
	public List<StatementEntry> getFullStatement(UUID accountId, Instant from, Instant to) {
		List<LedgerEntry> entries =
				ledgerEntryRepository.findByAccountIdAndCreatedAtBetweenOrderByCreatedAtDescIdDesc(accountId, from, to);
		if (entries.size() > MAX_EXPORT_ROWS) {
			throw new StatementRangeTooLargeException(entries.size(), MAX_EXPORT_ROWS);
		}
		return toStatementEntries(entries);
	}

	private List<StatementEntry> toStatementEntries(List<LedgerEntry> ledgerEntries) {
		Map<UUID, String> descriptionsByTransactionId = transactionRepository
				.findAllById(ledgerEntries.stream()
						.map(LedgerEntry::getTransactionId)
						.distinct()
						.toList())
				.stream()
				.collect(Collectors.toMap(Transaction::getId, Transaction::getDescription));

		return ledgerEntries.stream()
				.map(e -> new StatementEntry(
						e.getId(),
						e.getTransactionId(),
						e.getCreatedAt(),
						e.getAmount(),
						descriptionsByTransactionId.get(e.getTransactionId())))
				.toList();
	}

	private ScrollPosition decodeCursor(String cursor) {
		if (cursor == null || cursor.isBlank()) {
			return ScrollPosition.keyset();
		}
		String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
		String[] parts = decoded.split("\\|", 2);
		Instant createdAt = Instant.parse(parts[0]);
		UUID id = UUID.fromString(parts[1]);
		return ScrollPosition.forward(Map.of("createdAt", createdAt, "id", id));
	}

	private String encodeCursor(LedgerEntry lastEntry) {
		String raw = lastEntry.getCreatedAt() + "|" + lastEntry.getId();
		return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
	}
}
