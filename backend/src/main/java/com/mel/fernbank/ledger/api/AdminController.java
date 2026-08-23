package com.mel.fernbank.ledger.api;

import com.mel.fernbank.ledger.api.dto.AccountResponse;
import com.mel.fernbank.ledger.api.dto.AuditEventResponse;
import com.mel.fernbank.ledger.api.dto.CursorPage;
import com.mel.fernbank.ledger.api.dto.FreezeAccountRequest;
import com.mel.fernbank.ledger.api.mapper.AccountMapper;
import com.mel.fernbank.ledger.api.mapper.AuditEventMapper;
import com.mel.fernbank.ledger.audit.AuditEventPage;
import com.mel.fernbank.ledger.audit.AuditEventQueryService;
import com.mel.fernbank.ledger.banking.AccountQueryService;
import com.mel.fernbank.ledger.banking.AdminAccountService;
import com.mel.fernbank.ledger.banking.ReconciliationReport;
import com.mel.fernbank.ledger.banking.ReconciliationService;
import com.mel.fernbank.ledger.idempotency.IdempotencyGuard;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The admin surface: proves method security works end to end, plus freeze/audit-log access. */
@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Admin")
public class AdminController {

	private static final int MAX_PAGE_SIZE = 100;
	private static final int DEFAULT_PAGE_SIZE = 20;

	private final ReconciliationService reconciliationService;
	private final AdminAccountService adminAccountService;
	private final AccountQueryService accountQueryService;
	private final AuditEventQueryService auditEventQueryService;
	private final IdempotencyGuard idempotencyGuard;
	private final AccountMapper accountMapper;
	private final AuditEventMapper auditEventMapper;

	public AdminController(
			ReconciliationService reconciliationService,
			AdminAccountService adminAccountService,
			AccountQueryService accountQueryService,
			AuditEventQueryService auditEventQueryService,
			IdempotencyGuard idempotencyGuard,
			AccountMapper accountMapper,
			AuditEventMapper auditEventMapper) {
		this.reconciliationService = reconciliationService;
		this.adminAccountService = adminAccountService;
		this.accountQueryService = accountQueryService;
		this.auditEventQueryService = auditEventQueryService;
		this.idempotencyGuard = idempotencyGuard;
		this.accountMapper = accountMapper;
		this.auditEventMapper = auditEventMapper;
	}

	@Operation(summary = "Health-check-style ping proving RBAC works", description = "ROLE_ADMIN only.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "pong"),
		@ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden")
	})
	@GetMapping("/ping")
	@PreAuthorize("hasRole('ADMIN')")
	public Map<String, String> ping() {
		return Map.of("status", "pong");
	}

	@Operation(
			summary = "Run a full ledger reconciliation check",
			description = "Verifies every currency's ledger entries sum to zero and every account balance "
					+ "matches its ledger entries. Also runs hourly on a schedule.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "Reconciliation report, healthy or listing discrepancies"),
		@ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden")
	})
	@GetMapping("/reconciliation")
	@PreAuthorize("hasRole('ADMIN')")
	public ReconciliationReport reconciliation() {
		return reconciliationService.reconcile();
	}

	@Operation(
			summary = "Freeze an account",
			description = "Not scoped to any particular customer - an admin may freeze any account by id.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "Account frozen"),
		@ApiResponse(responseCode = "400", ref = "#/components/responses/ValidationFailed"),
		@ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
		@ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
		@ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict")
	})
	@PatchMapping("/accounts/{accountId}/freeze")
	@PreAuthorize("hasRole('ADMIN')")
	public AccountResponse freeze(
			@PathVariable UUID accountId,
			@Valid @RequestBody FreezeAccountRequest request,
			@IdempotencyKeyHeader @RequestHeader("Idempotency-Key") String idempotencyKey,
			JwtAuthenticationToken authentication) {
		UUID userId = userId(authentication);
		return idempotencyGuard.execute(userId, idempotencyKey, request, AccountResponse.class, () -> {
			adminAccountService.freeze(accountId, userId, request.reason());
			return accountMapper.toResponse(accountQueryService.getAccount(accountId));
		});
	}

	@Operation(
			summary = "Cursor-paginated audit event log",
			description = "Keyset pagination: pass the previous page's nextCursor to fetch the next page.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "A page of audit events"),
		@ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden")
	})
	@GetMapping("/audit-events")
	@PreAuthorize("hasRole('ADMIN')")
	public CursorPage<AuditEventResponse> auditEvents(
			@RequestParam(required = false) Instant from,
			@RequestParam(required = false) Instant to,
			@RequestParam(required = false) String cursor,
			@RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int pageSize) {
		AuditEventPage page = auditEventQueryService.page(from, to, cursor, Math.min(pageSize, MAX_PAGE_SIZE));
		List<AuditEventResponse> data =
				page.events().stream().map(auditEventMapper::toResponse).toList();
		return new CursorPage<>(data, page.nextCursor(), page.hasNext());
	}

	private UUID userId(JwtAuthenticationToken authentication) {
		Jwt jwt = authentication.getToken();
		return UUID.fromString(jwt.getSubject());
	}
}
