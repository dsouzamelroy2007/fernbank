package com.mel.fernbank.ledger.banking;

import java.util.List;

public record StatementPage(List<StatementEntry> entries, String nextCursor, boolean hasNext) {}
