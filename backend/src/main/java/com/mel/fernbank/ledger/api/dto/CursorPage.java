package com.mel.fernbank.ledger.api.dto;

import java.util.List;

public record CursorPage<T>(List<T> data, String nextCursor, boolean hasNext) {}
