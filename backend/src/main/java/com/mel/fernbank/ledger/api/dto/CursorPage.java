package com.mel.fernbank.ledger.api.dto;

import java.util.List;

/** Generic cursor-pagination envelope, shared by every keyset-paginated listing. */
public record CursorPage<T>(List<T> data, String nextCursor, boolean hasNext) {}
