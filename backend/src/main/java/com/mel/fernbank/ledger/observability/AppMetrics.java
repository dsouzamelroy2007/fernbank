package com.mel.fernbank.ledger.observability;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

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
