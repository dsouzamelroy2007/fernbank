package com.mel.fernbank.ledger.auth;

import com.mel.fernbank.ledger.audit.AuditLogger;
import com.mel.fernbank.ledger.auth.dto.LoginResponse;
import com.mel.fernbank.ledger.auth.dto.RegisterRequest;
import com.mel.fernbank.ledger.domain.Customer;
import com.mel.fernbank.ledger.domain.RefreshToken;
import com.mel.fernbank.ledger.domain.User;
import com.mel.fernbank.ledger.domain.UserStatus;
import com.mel.fernbank.ledger.observability.AppMetrics;
import com.mel.fernbank.ledger.repository.CustomerRepository;
import com.mel.fernbank.ledger.repository.RefreshTokenRepository;
import com.mel.fernbank.ledger.repository.UserRepository;
import com.mel.fernbank.ledger.security.LoginRateLimiter;
import com.mel.fernbank.ledger.security.TokenHasher;
import com.mel.fernbank.ledger.security.TotpService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthenticationService {

	private static final Logger log = LoggerFactory.getLogger(AuthenticationService.class);

	private final CustomerRepository customerRepository;
	private final UserRepository userRepository;
	private final RefreshTokenRepository refreshTokenRepository;
	private final PasswordEncoder passwordEncoder;
	private final TokenIssuer tokenIssuer;
	private final TotpService totpService;
	private final LoginRateLimiter loginRateLimiter;
	private final AuditLogger auditLogger;
	private final AppMetrics appMetrics;

	public AuthenticationService(
			CustomerRepository customerRepository,
			UserRepository userRepository,
			RefreshTokenRepository refreshTokenRepository,
			PasswordEncoder passwordEncoder,
			TokenIssuer tokenIssuer,
			TotpService totpService,
			LoginRateLimiter loginRateLimiter,
			AuditLogger auditLogger,
			AppMetrics appMetrics) {
		this.customerRepository = customerRepository;
		this.userRepository = userRepository;
		this.refreshTokenRepository = refreshTokenRepository;
		this.passwordEncoder = passwordEncoder;
		this.tokenIssuer = tokenIssuer;
		this.totpService = totpService;
		this.loginRateLimiter = loginRateLimiter;
		this.auditLogger = auditLogger;
		this.appMetrics = appMetrics;
	}

	@Transactional
	public User register(RegisterRequest request) {
		if (userRepository.findByEmail(request.email()).isPresent()) {
			throw new EmailAlreadyRegisteredException();
		}
		Customer customer = customerRepository.save(new Customer(request.fullName()));
		User user = new User(customer.getId(), request.email(), passwordEncoder.encode(request.password()));
		user = userRepository.save(user);
		auditLogger.record(user.getId(), "auth.register");
		return user;
	}

	@Transactional
	public LoginResponse login(String email, String password, String ipAddress) {
		if (!loginRateLimiter.tryConsume(ipAddress, email)) {
			appMetrics.recordFailedLogin("rate_limited");
			throw new RateLimitExceededException();
		}

		User user = userRepository.findByEmail(email).orElse(null);
		if (user == null) {
			auditLogger.record(null, "auth.login_failure", Map.of("reason", "unknown_email", "email", email));
			appMetrics.recordFailedLogin("unknown_email");
			throw new InvalidCredentialsException();
		}
		if (user.isLocked()) {
			auditLogger.record(user.getId(), "auth.login_failure", Map.of("reason", "locked"));
			appMetrics.recordFailedLogin("locked");
			throw new InvalidCredentialsException();
		}
		if (user.getStatus() != UserStatus.ACTIVE) {
			auditLogger.record(user.getId(), "auth.login_failure", Map.of("reason", "disabled"));
			appMetrics.recordFailedLogin("disabled");
			throw new InvalidCredentialsException();
		}
		if (!passwordEncoder.matches(password, user.getPasswordHash())) {
			user.recordFailedLogin();
			userRepository.save(user);
			auditLogger.record(user.getId(), "auth.login_failure", Map.of("reason", "bad_password"));
			appMetrics.recordFailedLogin("bad_password");
			throw new InvalidCredentialsException();
		}

		user.recordSuccessfulLogin();
		userRepository.save(user);

		if (user.isMfaEnabled()) {
			auditLogger.record(user.getId(), "auth.login_mfa_challenge_issued");
			return LoginResponse.mfaRequired(tokenIssuer.issueMfaChallengeToken(user));
		}

		auditLogger.record(user.getId(), "auth.login_success");
		TokenIssuer.TokenPair tokens = tokenIssuer.issueTokenPair(user);
		return LoginResponse.authenticated(tokens.accessToken(), tokens.refreshToken());
	}

	@Transactional
	public TokenIssuer.TokenPair refresh(String rawRefreshToken) {
		String hash = TokenHasher.sha256Hex(rawRefreshToken);
		RefreshToken existing = refreshTokenRepository.findByTokenHash(hash).orElseThrow(InvalidRefreshTokenException::new);

		if (existing.getRevokedAt() != null) {
			revokeFamily(existing.getFamilyId());
			auditLogger.record(
					existing.getUserId(),
					"auth.refresh_token_reuse_detected",
					Map.of("familyId", existing.getFamilyId().toString()));
			log.warn(
					"Refresh token reuse detected for userId={} familyId={} - whole token family revoked",
					existing.getUserId(),
					existing.getFamilyId());
			throw new InvalidRefreshTokenException();
		}
		if (existing.getExpiresAt().isBefore(Instant.now())) {
			throw new InvalidRefreshTokenException();
		}

		User user = userRepository.findById(existing.getUserId()).orElseThrow(InvalidRefreshTokenException::new);
		TokenIssuer.IssuedRefreshToken issued = tokenIssuer.issueRefreshToken(user, existing.getFamilyId());
		existing.revoke(issued.entity().getId());
		refreshTokenRepository.save(existing);

		String access = tokenIssuer.issueAccessToken(user, null);
		auditLogger.record(user.getId(), "auth.refresh");
		return new TokenIssuer.TokenPair(access, issued.rawToken());
	}

	@Transactional
	public void logout(String rawRefreshToken) {
		String hash = TokenHasher.sha256Hex(rawRefreshToken);
		refreshTokenRepository.findByTokenHash(hash).ifPresent(token -> {
			if (token.getRevokedAt() == null) {
				token.revoke(null);
				refreshTokenRepository.save(token);
				auditLogger.record(token.getUserId(), "auth.logout");
			}
		});
	}

	@Transactional(readOnly = true)
	public String stepUp(UUID userId, String code) {
		User user = userRepository.findById(userId).orElseThrow(InvalidCredentialsException::new);
		if (!user.isMfaEnabled() || user.getMfaSecret() == null || !totpService.verifyCode(user.getMfaSecret(), code)) {
			auditLogger.record(userId, "auth.step_up_failure");
			throw new InvalidMfaChallengeException();
		}
		auditLogger.record(userId, "auth.step_up_success");
		return tokenIssuer.issueElevatedAccessToken(user);
	}

	@Transactional
	public void changePassword(UUID userId, String currentPassword, String newPassword) {
		User user = userRepository.findById(userId).orElseThrow(InvalidCredentialsException::new);
		if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
			auditLogger.record(userId, "auth.password_change_failure");
			throw new WrongPasswordException();
		}
		user.setPasswordHash(passwordEncoder.encode(newPassword));
		userRepository.save(user);
		auditLogger.record(userId, "auth.password_change_success");
	}

	private void revokeFamily(UUID familyId) {
		List<RefreshToken> active = refreshTokenRepository.findByFamilyIdAndRevokedAtIsNull(familyId);
		for (RefreshToken token : active) {
			token.revoke(null);
		}
		refreshTokenRepository.saveAll(active);
	}
}
