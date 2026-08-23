package com.mel.fernbank.ledger.error;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Assigns every request a correlation id (reused from {@code X-Correlation-Id} if the
 * caller sent one) so {@link ApiExceptionHandler} can attach it to every Problem Detail
 * response. This MDC key is also picked up automatically by structured JSON logging
 * (the {@code docker} Spring profile, see {@code application-docker.yml}) - every log
 * line emitted during a request carries the same correlation id as its HTTP response.
 * Log aggregation/visualization of this is still later-phase work (see
 * docs/PHASE_PLAN.md's observability notes) - this filter is the plumbing underneath it.
 */
@Component
public class CorrelationIdFilter extends OncePerRequestFilter {

	public static final String HEADER = "X-Correlation-Id";
	public static final String MDC_KEY = "correlationId";
	public static final String REQUEST_ATTRIBUTE = "correlationId";

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String correlationId = request.getHeader(HEADER);
		if (correlationId == null || correlationId.isBlank()) {
			correlationId = UUID.randomUUID().toString();
		}
		request.setAttribute(REQUEST_ATTRIBUTE, correlationId);
		response.setHeader(HEADER, correlationId);
		MDC.put(MDC_KEY, correlationId);
		try {
			chain.doFilter(request, response);
		} finally {
			MDC.remove(MDC_KEY);
		}
	}
}
