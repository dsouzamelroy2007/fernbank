package com.mel.fernbank.ledger.banking;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;

@Component
public class OptimisticRetryTemplate {

	private static final int MAX_ATTEMPTS = 50;
	private static final int MAX_BACKOFF_MILLIS = 15;

	public <T> T execute(Supplier<T> attempt) {
		ObjectOptimisticLockingFailureException lastFailure = null;
		for (int i = 0; i < MAX_ATTEMPTS; i++) {
			try {
				return attempt.get();
			} catch (ObjectOptimisticLockingFailureException e) {
				lastFailure = e;
				backoff();
			}
		}
		throw lastFailure;
	}

	private void backoff() {
		try {
			Thread.sleep(ThreadLocalRandom.current().nextInt(1, MAX_BACKOFF_MILLIS));
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}
