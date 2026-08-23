package com.mel.fernbank.ledger.banking;

/**
 * Thrown by {@link GetStatementService#getFullStatement} when an export's date range
 * would return more rows than the export cap allows - not part of the sealed
 * {@link DomainException} hierarchy since it's a query-safety guard, not a
 * money-movement business rule.
 */
public class StatementRangeTooLargeException extends RuntimeException {

	public StatementRangeTooLargeException(int rowCount, int maxRows) {
		super("Statement range has %d entries, exceeding the export cap of %d - narrow the date range"
				.formatted(rowCount, maxRows));
	}
}
