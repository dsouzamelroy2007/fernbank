package com.mel.fernbank.ledger.error;

import com.mel.fernbank.ledger.auth.EmailAlreadyRegisteredException;
import com.mel.fernbank.ledger.auth.InvalidCredentialsException;
import com.mel.fernbank.ledger.auth.InvalidMfaChallengeException;
import com.mel.fernbank.ledger.auth.InvalidRefreshTokenException;
import com.mel.fernbank.ledger.auth.RateLimitExceededException;
import com.mel.fernbank.ledger.auth.SessionNotFoundException;
import com.mel.fernbank.ledger.auth.WrongPasswordException;
import com.mel.fernbank.ledger.banking.AccountNotActiveException;
import com.mel.fernbank.ledger.banking.AccountNotFoundException;
import com.mel.fernbank.ledger.banking.CurrencyMismatchException;
import com.mel.fernbank.ledger.banking.DomainException;
import com.mel.fernbank.ledger.banking.InsufficientFundsException;
import com.mel.fernbank.ledger.banking.InvalidAmountException;
import com.mel.fernbank.ledger.banking.SameAccountTransferException;
import com.mel.fernbank.ledger.banking.StatementRangeTooLargeException;
import com.mel.fernbank.ledger.idempotency.IdempotencyConflictException;
import com.mel.fernbank.ledger.security.StepUpRequiredException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Global RFC 9457 Problem Details error mapping for the whole API. Replaces Phase 2's
 * package-scoped {@code auth.AuthExceptionHandler}. Every response carries a
 * {@code correlationId} extension property (set by {@link CorrelationIdFilter}) and
 * never leaks a stack trace or SQL - unmapped exceptions are logged server-side and
 * returned as an opaque 500.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

	private static final String TYPE_BASE = "https://fernbank.dev/problems/";
	private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

	@ExceptionHandler(DomainException.class)
	public ProblemDetail handleDomainException(DomainException e, HttpServletRequest request) {
		HttpStatus status =
				switch (e) {
					case AccountNotFoundException ignored -> HttpStatus.NOT_FOUND;
					case AccountNotActiveException ignored -> HttpStatus.CONFLICT;
					case CurrencyMismatchException ignored -> HttpStatus.UNPROCESSABLE_CONTENT;
					case InsufficientFundsException ignored -> HttpStatus.UNPROCESSABLE_CONTENT;
					case InvalidAmountException ignored -> HttpStatus.BAD_REQUEST;
					case SameAccountTransferException ignored -> HttpStatus.UNPROCESSABLE_CONTENT;
				};
		return problem(status, kebabCase(e.getClass().getSimpleName()), e.getMessage(), request);
	}

	@ExceptionHandler(EmailAlreadyRegisteredException.class)
	public ProblemDetail handleEmailAlreadyRegistered(EmailAlreadyRegisteredException e, HttpServletRequest request) {
		return problem(HttpStatus.CONFLICT, "email-already-registered", e.getMessage(), request);
	}

	/**
	 * Backstop for a unique-constraint violation that races past a service-level
	 * check-then-act guard (e.g. two concurrent registrations with the same email) -
	 * the constraint is the real backstop, this just gives it a 409 instead of an
	 * opaque 500. Deliberately generic: doesn't attempt to name which constraint fired.
	 */
	@ExceptionHandler(DataIntegrityViolationException.class)
	public ProblemDetail handleDataIntegrityViolation(DataIntegrityViolationException e, HttpServletRequest request) {
		return problem(HttpStatus.CONFLICT, "conflict", "The request conflicts with existing data", request);
	}

	@ExceptionHandler(IdempotencyConflictException.class)
	public ProblemDetail handleIdempotencyConflict(IdempotencyConflictException e, HttpServletRequest request) {
		return problem(HttpStatus.CONFLICT, "idempotency-conflict", e.getMessage(), request);
	}

	@ExceptionHandler(StepUpRequiredException.class)
	public ProblemDetail handleStepUpRequired(StepUpRequiredException e, HttpServletRequest request) {
		return problem(HttpStatus.FORBIDDEN, "step-up-required", e.getMessage(), request);
	}

	@ExceptionHandler({InvalidCredentialsException.class, InvalidRefreshTokenException.class, InvalidMfaChallengeException.class
	})
	public ProblemDetail handleUnauthorized(RuntimeException e, HttpServletRequest request) {
		return problem(HttpStatus.UNAUTHORIZED, "unauthorized", e.getMessage(), request);
	}

	@ExceptionHandler(RateLimitExceededException.class)
	public ProblemDetail handleRateLimit(RateLimitExceededException e, HttpServletRequest request) {
		return problem(HttpStatus.TOO_MANY_REQUESTS, "rate-limit-exceeded", e.getMessage(), request);
	}

	@ExceptionHandler(WrongPasswordException.class)
	public ProblemDetail handleWrongPassword(WrongPasswordException e, HttpServletRequest request) {
		return problem(HttpStatus.UNAUTHORIZED, "wrong-password", e.getMessage(), request);
	}

	@ExceptionHandler(SessionNotFoundException.class)
	public ProblemDetail handleSessionNotFound(SessionNotFoundException e, HttpServletRequest request) {
		return problem(HttpStatus.NOT_FOUND, "session-not-found", e.getMessage(), request);
	}

	@ExceptionHandler(StatementRangeTooLargeException.class)
	public ProblemDetail handleStatementRangeTooLarge(StatementRangeTooLargeException e, HttpServletRequest request) {
		return problem(HttpStatus.BAD_REQUEST, "statement-range-too-large", e.getMessage(), request);
	}

	@ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class})
	public ProblemDetail handleValidation(Exception e, HttpServletRequest request) {
		ProblemDetail problem =
				problem(HttpStatus.BAD_REQUEST, "validation-failed", "Request failed validation", request);
		problem.setProperty("errors", fieldErrors(e));
		return problem;
	}

	@ExceptionHandler(MissingRequestHeaderException.class)
	public ProblemDetail handleMissingHeader(MissingRequestHeaderException e, HttpServletRequest request) {
		return problem(HttpStatus.BAD_REQUEST, "missing-header", e.getMessage(), request);
	}

	@ExceptionHandler(BadRequestException.class)
	public ProblemDetail handleBadRequest(BadRequestException e, HttpServletRequest request) {
		return problem(HttpStatus.BAD_REQUEST, "bad-request", e.getMessage(), request);
	}

	@ExceptionHandler(NoSuchElementException.class)
	public ProblemDetail handleNotFound(NoSuchElementException e, HttpServletRequest request) {
		return problem(HttpStatus.NOT_FOUND, "not-found", "The requested resource was not found", request);
	}

	@ExceptionHandler(AccessDeniedException.class)
	public ProblemDetail handleAccessDenied(AccessDeniedException e, HttpServletRequest request) {
		return problem(HttpStatus.FORBIDDEN, "access-denied", "You do not have access to this resource", request);
	}

	@ExceptionHandler(Exception.class)
	public ProblemDetail handleUnexpected(Exception e, HttpServletRequest request) {
		log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), e);
		return problem(HttpStatus.INTERNAL_SERVER_ERROR, "internal-error", "An unexpected error occurred", request);
	}

	private ProblemDetail problem(HttpStatus status, String type, String detail, HttpServletRequest request) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setType(URI.create(TYPE_BASE + type));
		problem.setProperty("correlationId", request.getAttribute(CorrelationIdFilter.REQUEST_ATTRIBUTE));
		return problem;
	}

	private List<String> fieldErrors(Exception e) {
		if (e instanceof MethodArgumentNotValidException manve) {
			return manve.getBindingResult().getFieldErrors().stream()
					.map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
					.collect(Collectors.toList());
		}
		if (e instanceof ConstraintViolationException cve) {
			return cve.getConstraintViolations().stream()
					.map(v -> v.getPropertyPath() + ": " + v.getMessage())
					.collect(Collectors.toList());
		}
		return List.of();
	}

	private String kebabCase(String simpleClassName) {
		String withoutSuffix = simpleClassName.replace("Exception", "");
		return withoutSuffix.replaceAll("([a-z])([A-Z])", "$1-$2").toLowerCase();
	}
}
