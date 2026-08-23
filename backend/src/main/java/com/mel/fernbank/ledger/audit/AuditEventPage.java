package com.mel.fernbank.ledger.audit;

import com.mel.fernbank.ledger.domain.AuditEvent;
import java.util.List;

public record AuditEventPage(List<AuditEvent> events, String nextCursor, boolean hasNext) {}
