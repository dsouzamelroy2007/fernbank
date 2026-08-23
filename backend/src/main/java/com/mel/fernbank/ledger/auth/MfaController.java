package com.mel.fernbank.ledger.auth;

import com.mel.fernbank.ledger.auth.dto.LoginResponse;
import com.mel.fernbank.ledger.auth.dto.MfaEnrollConfirmRequest;
import com.mel.fernbank.ledger.auth.dto.MfaEnrollConfirmResponse;
import com.mel.fernbank.ledger.auth.dto.MfaEnrollResponse;
import com.mel.fernbank.ledger.auth.dto.MfaVerifyRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth/mfa")
@Tag(name = "MFA")
public class MfaController {

	private final MfaService mfaService;

	public MfaController(MfaService mfaService) {
		this.mfaService = mfaService;
	}

	@Operation(
			summary = "Begin TOTP enrollment",
			description = "Returns a new secret and otpauth:// URI (render as a QR code); not yet active "
					+ "until confirmed via POST .../enroll/confirm.")
	@ApiResponses({@ApiResponse(responseCode = "200", description = "TOTP secret and otpauth URI")})
	@PostMapping("/enroll")
	public MfaEnrollResponse enroll(JwtAuthenticationToken authentication) {
		MfaService.EnrollmentSecret secret = mfaService.enroll(userId(authentication));
		return new MfaEnrollResponse(secret.secret(), secret.otpAuthUri());
	}

	@Operation(
			summary = "Confirm TOTP enrollment with a code from the authenticator app",
			description = "Activates MFA and returns one-time recovery codes - shown to the user exactly once.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "MFA activated; recovery codes"),
		@ApiResponse(responseCode = "400", ref = "#/components/responses/ValidationFailed"),
		@ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized")
	})
	@PostMapping("/enroll/confirm")
	public MfaEnrollConfirmResponse confirmEnroll(
			@Valid @RequestBody MfaEnrollConfirmRequest request, JwtAuthenticationToken authentication) {
		return new MfaEnrollConfirmResponse(mfaService.confirmEnrollment(userId(authentication), request.code()));
	}

	@Operation(
			summary = "Complete login for an MFA-enrolled user",
			description = "Exchanges the mfaToken challenge from POST /api/v1/auth/login plus a TOTP or "
					+ "single-use recovery code for real access/refresh tokens.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "Authenticated"),
		@ApiResponse(responseCode = "400", ref = "#/components/responses/ValidationFailed"),
		@ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized")
	})
	@PostMapping("/verify")
	public LoginResponse verify(@Valid @RequestBody MfaVerifyRequest request) {
		return mfaService.verify(request.mfaToken(), request.code());
	}

	private UUID userId(JwtAuthenticationToken authentication) {
		Jwt jwt = authentication.getToken();
		return UUID.fromString(jwt.getSubject());
	}
}
