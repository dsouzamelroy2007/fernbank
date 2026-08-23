package com.mel.fernbank.ledger.auth;

import com.mel.fernbank.ledger.auth.dto.LoginRequest;
import com.mel.fernbank.ledger.auth.dto.LoginResponse;
import com.mel.fernbank.ledger.auth.dto.RefreshRequest;
import com.mel.fernbank.ledger.auth.dto.RegisterRequest;
import com.mel.fernbank.ledger.auth.dto.RegisterResponse;
import com.mel.fernbank.ledger.auth.dto.StepUpRequest;
import com.mel.fernbank.ledger.auth.dto.StepUpResponse;
import com.mel.fernbank.ledger.auth.dto.TokenResponse;
import com.mel.fernbank.ledger.domain.User;
import com.mel.fernbank.ledger.security.FernbankProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth")
public class AuthController {

	private static final String INTERNAL_SERVICE_KEY_HEADER = "X-Internal-Service-Key";
	private static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";

	private final AuthenticationService authenticationService;
	private final String internalServiceKey;

	public AuthController(AuthenticationService authenticationService, FernbankProperties properties) {
		this.authenticationService = authenticationService;
		this.internalServiceKey = properties.auth().internalServiceKey();
	}

	@Operation(summary = "Register a new customer and user")
	@ApiResponses({
		@ApiResponse(responseCode = "201", description = "User registered"),
		@ApiResponse(responseCode = "400", ref = "#/components/responses/ValidationFailed"),
		@ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict")
	})
	@PostMapping("/register")
	public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
		User user = authenticationService.register(request);
		return ResponseEntity.status(HttpStatus.CREATED).body(new RegisterResponse(user.getId(), user.getEmail()));
	}

	@Operation(
			summary = "Log in with email and password",
			description = "Repeated failures trip a rate limit and lock the account (429/423-style "
					+ "lockout surfaced as 401 on subsequent attempts). If MFA is enabled, returns a "
					+ "challenge status instead of tokens - see LoginResponse.status.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "Authenticated, or an MFA challenge issued"),
		@ApiResponse(responseCode = "400", ref = "#/components/responses/ValidationFailed"),
		@ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
		@ApiResponse(responseCode = "429", ref = "#/components/responses/TooManyRequests")
	})
	@PostMapping("/login")
	public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
		return authenticationService.login(request.email(), request.password(), resolveClientIp(servletRequest));
	}

	/**
	 * The bff is this endpoint's only intended caller, but its public URL is directly
	 * reachable by anyone - so X-Forwarded-For is only trusted when paired with the
	 * shared internal-service-key header, never on its own. Otherwise any caller could
	 * forge a fresh IP per request and dodge LoginRateLimiter's per-(IP, email) lockout
	 * entirely.
	 */
	private String resolveClientIp(HttpServletRequest servletRequest) {
		String presentedKey = servletRequest.getHeader(INTERNAL_SERVICE_KEY_HEADER);
		String forwardedFor = servletRequest.getHeader(FORWARDED_FOR_HEADER);
		boolean trusted = internalServiceKey != null
				&& !internalServiceKey.isBlank()
				&& internalServiceKey.equals(presentedKey);
		if (trusted && forwardedFor != null && !forwardedFor.isBlank()) {
			return forwardedFor.split(",")[0].trim();
		}
		return servletRequest.getRemoteAddr();
	}

	@Operation(
			summary = "Rotate an access/refresh token pair",
			description = "Reusing an already-rotated refresh token is treated as theft: it revokes the "
					+ "entire token family, including the legitimately-rotated successor.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "A new access/refresh token pair"),
		@ApiResponse(responseCode = "400", ref = "#/components/responses/ValidationFailed"),
		@ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized")
	})
	@PostMapping("/refresh")
	public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
		TokenIssuer.TokenPair tokens = authenticationService.refresh(request.refreshToken());
		return new TokenResponse(tokens.accessToken(), tokens.refreshToken());
	}

	@Operation(
			summary = "Revoke a refresh token",
			description = "Idempotent - an unknown or already-revoked token still returns 204.")
	@ApiResponses({
		@ApiResponse(responseCode = "204", description = "Token revoked (or already wasn't valid)"),
		@ApiResponse(responseCode = "400", ref = "#/components/responses/ValidationFailed")
	})
	@PostMapping("/logout")
	public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request) {
		authenticationService.logout(request.refreshToken());
		return ResponseEntity.noContent().build();
	}

	@Operation(
			summary = "Elevate the current session with a fresh MFA code",
			description = "Required before a transfer at or above the step-up threshold will be accepted.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "Elevated access token"),
		@ApiResponse(responseCode = "400", ref = "#/components/responses/ValidationFailed"),
		@ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized")
	})
	@PostMapping("/step-up")
	public StepUpResponse stepUp(@Valid @RequestBody StepUpRequest request, JwtAuthenticationToken authentication) {
		Jwt jwt = authentication.getToken();
		UUID userId = UUID.fromString(jwt.getSubject());
		return new StepUpResponse(authenticationService.stepUp(userId, request.code()));
	}
}
