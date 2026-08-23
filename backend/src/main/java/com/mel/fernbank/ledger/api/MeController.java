package com.mel.fernbank.ledger.api;

import com.mel.fernbank.ledger.api.dto.AuditEventResponse;
import com.mel.fernbank.ledger.api.dto.CursorPage;
import com.mel.fernbank.ledger.api.dto.NoContentResult;
import com.mel.fernbank.ledger.api.dto.SessionResponse;
import com.mel.fernbank.ledger.api.mapper.AuditEventMapper;
import com.mel.fernbank.ledger.audit.AuditEventPage;
import com.mel.fernbank.ledger.audit.AuditEventQueryService;
import com.mel.fernbank.ledger.auth.AuthenticationService;
import com.mel.fernbank.ledger.auth.SessionService;
import com.mel.fernbank.ledger.auth.dto.ChangePasswordRequest;
import com.mel.fernbank.ledger.domain.RefreshToken;
import com.mel.fernbank.ledger.idempotency.IdempotencyGuard;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
@Tag(name = "Profile")
public class MeController {

	private static final int MAX_PAGE_SIZE = 100;
	private static final int DEFAULT_PAGE_SIZE = 20;

	private final ProfileService profileService;
	private final AuthenticationService authenticationService;
	private final SessionService sessionService;
	private final AuditEventQueryService auditEventQueryService;
	private final AuditEventMapper auditEventMapper;
	private final IdempotencyGuard idempotencyGuard;

	public MeController(
			ProfileService profileService,
			AuthenticationService authenticationService,
			SessionService sessionService,
			AuditEventQueryService auditEventQueryService,
			AuditEventMapper auditEventMapper,
			IdempotencyGuard idempotencyGuard) {
		this.profileService = profileService;
		this.authenticationService = authenticationService;
		this.sessionService = sessionService;
		this.auditEventQueryService = auditEventQueryService;
		this.auditEventMapper = auditEventMapper;
		this.idempotencyGuard = idempotencyGuard;
	}

	@Operation(
			summary = "Get the authenticated user's own identity",
			description = "Resolved entirely from the JWT subject - never from a client-supplied id.")
	@ApiResponses({@ApiResponse(responseCode = "200", description = "The caller's own profile")})
	@GetMapping
	public MeResponse me(JwtAuthenticationToken authentication) {
		return profileService.getOwnProfile(userId(authentication));
	}

	@Operation(summary = "Change the authenticated user's own password")
	@ApiResponses({
		@ApiResponse(responseCode = "204", description = "Password changed"),
		@ApiResponse(responseCode = "400", ref = "#/components/responses/ValidationFailed"),
		@ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized")
	})
	@PostMapping("/password")
	public ResponseEntity<Void> changePassword(
			@Valid @RequestBody ChangePasswordRequest request,
			@IdempotencyKeyHeader @RequestHeader("Idempotency-Key") String idempotencyKey,
			JwtAuthenticationToken authentication) {
		UUID userId = userId(authentication);
		idempotencyGuard.execute(userId, idempotencyKey, request, NoContentResult.class, () -> {
			authenticationService.changePassword(userId, request.currentPassword(), request.newPassword());
			return new NoContentResult();
		});
		return ResponseEntity.noContent().build();
	}

	@Operation(
			summary = "List the authenticated user's own active sessions (refresh tokens)",
			description = "No device/IP metadata is stored, so a 'this device' flag can't be shown - "
					+ "only issued/expiry times.")
	@GetMapping("/sessions")
	public List<SessionResponse> listSessions(JwtAuthenticationToken authentication) {
		return sessionService.listActiveSessions(userId(authentication)).stream()
				.map(this::toSessionResponse)
				.toList();
	}

	@Operation(
			summary = "Revoke one of the authenticated user's own sessions",
			description = "Idempotent - an unknown or already-revoked session id still returns 204, same "
					+ "as logout. A session id belonging to another user returns 404, not 403 - "
					+ "existence is never leaked.")
	@ApiResponses({
		@ApiResponse(responseCode = "204", description = "Session revoked (or already wasn't active)"),
		@ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
	})
	@DeleteMapping("/sessions/{sessionId}")
	public ResponseEntity<Void> revokeSession(@PathVariable UUID sessionId, JwtAuthenticationToken authentication) {
		sessionService.revokeSession(userId(authentication), sessionId);
		return ResponseEntity.noContent().build();
	}

	@Operation(
			summary = "Cursor-paginated login history for the authenticated user",
			description = "Keyset pagination: pass the previous page's nextCursor to fetch the next page. "
					+ "Scoped to the caller's own auth-relevant events only.")
	@GetMapping("/login-history")
	public CursorPage<AuditEventResponse> loginHistory(
			@RequestParam(required = false) Instant from,
			@RequestParam(required = false) Instant to,
			@RequestParam(required = false) String cursor,
			@RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int pageSize,
			JwtAuthenticationToken authentication) {
		AuditEventPage page = auditEventQueryService.pageForUser(
				userId(authentication), from, to, cursor, Math.min(pageSize, MAX_PAGE_SIZE));
		List<AuditEventResponse> data =
				page.events().stream().map(auditEventMapper::toResponse).toList();
		return new CursorPage<>(data, page.nextCursor(), page.hasNext());
	}

	private SessionResponse toSessionResponse(RefreshToken token) {
		return new SessionResponse(token.getId(), token.getIssuedAt(), token.getExpiresAt());
	}

	private UUID userId(JwtAuthenticationToken authentication) {
		Jwt jwt = authentication.getToken();
		return UUID.fromString(jwt.getSubject());
	}
}
