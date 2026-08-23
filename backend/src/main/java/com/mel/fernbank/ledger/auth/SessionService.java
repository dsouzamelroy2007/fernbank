package com.mel.fernbank.ledger.auth;

import com.mel.fernbank.ledger.audit.AuditLogger;
import com.mel.fernbank.ledger.domain.RefreshToken;
import com.mel.fernbank.ledger.repository.RefreshTokenRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Self-service session (refresh token) listing/revocation - the user-facing
 * counterpart to {@link AuthenticationService}'s reuse-detection-triggered family
 * revoke. There is no device/IP/user-agent column on {@link RefreshToken}, so a
 * session can't be flagged as "this device" - listing only exposes issued/expiry
 * times.
 */
@Service
public class SessionService {

	private final RefreshTokenRepository refreshTokenRepository;
	private final AuditLogger auditLogger;

	public SessionService(RefreshTokenRepository refreshTokenRepository, AuditLogger auditLogger) {
		this.refreshTokenRepository = refreshTokenRepository;
		this.auditLogger = auditLogger;
	}

	@Transactional(readOnly = true)
	public List<RefreshToken> listActiveSessions(UUID userId) {
		return refreshTokenRepository.findByUserIdAndRevokedAtIsNullOrderByIssuedAtDesc(userId);
	}

	/** Idempotent - revoking an already-revoked session is a no-op, matching {@code logout()}'s style. */
	@Transactional
	public void revokeSession(UUID userId, UUID sessionId) {
		RefreshToken token =
				refreshTokenRepository.findByIdAndUserId(sessionId, userId).orElseThrow(SessionNotFoundException::new);
		if (token.getRevokedAt() == null) {
			token.revoke(null);
			refreshTokenRepository.save(token);
			auditLogger.record(userId, "auth.session_revoked", Map.of("sessionId", sessionId.toString()));
		}
	}
}
