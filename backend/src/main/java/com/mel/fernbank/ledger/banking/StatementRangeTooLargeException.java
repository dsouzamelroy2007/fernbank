package com.mel.fernbank.ledger.banking;

public class StatementRangeTooLargeException extends RuntimeException {

	public StatementRangeTooLargeException(int rowCount, int maxRows) {
		super("Statement range has %d entries, exceeding the export cap of %d - narrow the date range"
				.formatted(rowCount, maxRows));
	}
}
