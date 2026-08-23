package com.mel.fernbank.ledger.banking;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;

/**
 * Retries an operation that failed due to a stale {@code @Version} on an
 * {@code AccountBalance} row. Each attempt must run in its own fresh transaction (a JPA
 * persistence context is unusable after a flush failure) - the supplier passed in must
 * therefore call a {@code @Transactional} method on a *different* Spring bean than the
 * caller of {@link #execute}, so Spring's transactional proxy actually opens a new
 * transaction per attempt rather than reusing one that already failed.
 *
 * <p>{@code MAX_ATTEMPTS} and the backoff are sized for this app's own concurrency test
 * (20 threads hammering 2 rows) - a small random backoff between attempts matters more
 * than the raw attempt count for avoiding repeated immediate re-collisions.
 */
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
