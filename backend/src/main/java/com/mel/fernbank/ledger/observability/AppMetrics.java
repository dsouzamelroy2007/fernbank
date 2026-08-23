package com.mel.fernbank.ledger.observability;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Operational counters exposed at {@code /actuator/prometheus}, mirroring {@link
 * com.mel.fernbank.ledger.audit.AuditLogger}'s call-site pattern (injected wherever the
 * outcome happens) rather than deriving metrics from logs. Tag values are always a small,
 * fixed set of reason/outcome codes - never a customer id, email, or amount - so this
 * stays safe under CONTRIBUTING.md's "no identity in anything long-retention" rule even though
 * it isn't the logging pipeline.
 */
@Component
public class AppMetrics {

	private final MeterRegistry registry;

	public AppMetrics(MeterRegistry registry) {
		this.registry = registry;
	}

	public void recordTransfer(String outcome) {
		registry.counter("fernbank.transfers", "outcome", outcome).increment();
	}

	public void recordFailedLogin(String reason) {
		registry.counter("fernbank.logins.failed", "reason", reason).increment();
	}

	public void recordIdempotentReplay(String resource) {
		registry.counter("fernbank.idempotency.replays", "resource", resource).increment();
	}
}
