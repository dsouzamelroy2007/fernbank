package com.mel.fernbank.ledger.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * In-memory Bucket4j rate limit of login attempts per (IP + email). Single-instance
 * only — a multi-instance deployment would need a distributed {@code ProxyManager}
 * (Redis/Hazelcast), out of scope here.
 */
@Component
public class LoginRateLimiter {

	private static final Logger log = LoggerFactory.getLogger(LoginRateLimiter.class);

	private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();
	private final FernbankProperties properties;

	public LoginRateLimiter(FernbankProperties properties) {
		this.properties = properties;
	}

	public boolean tryConsume(String ipAddress, String email) {
		String key = ipAddress + "|" + email.toLowerCase();
		Bucket bucket = buckets.computeIfAbsent(key, k -> newBucket());
		boolean consumed = bucket.tryConsume(1);
		if (!consumed) {
			log.warn("Login rate limit exceeded for email={} from ip={}", email.toLowerCase(), ipAddress);
		}
		return consumed;
	}

	private Bucket newBucket() {
		FernbankProperties.Auth auth = properties.auth();
		Duration window = auth.loginRateLimitWindow();
		Bandwidth limit = Bandwidth.builder()
				.capacity(auth.loginRateLimitAttempts())
				.refillIntervally(auth.loginRateLimitAttempts(), window)
				.build();
		return Bucket.builder().addLimit(limit).build();
	}
}
