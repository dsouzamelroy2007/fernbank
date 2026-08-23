package com.mel.fernbank.ledger.auth;

import com.mel.fernbank.ledger.audit.AuditLogger;
import com.mel.fernbank.ledger.auth.dto.LoginResponse;
import com.mel.fernbank.ledger.domain.RecoveryCode;
import com.mel.fernbank.ledger.domain.User;
import com.mel.fernbank.ledger.repository.RecoveryCodeRepository;
import com.mel.fernbank.ledger.repository.UserRepository;
import com.mel.fernbank.ledger.security.FernbankProperties;
import com.mel.fernbank.ledger.security.TotpService;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MfaService {

	private static final String ISSUER_NAME = "fernbank";
	private static final String RECOVERY_CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
	private static final int RECOVERY_CODE_LENGTH = 10;

	private final UserRepository userRepository;
	private final RecoveryCodeRepository recoveryCodeRepository;
	private final TotpService totpService;
	private final PasswordEncoder passwordEncoder;
	private final TokenIssuer tokenIssuer;
	private final FernbankProperties properties;
	private final AuditLogger auditLogger;
	private final SecureRandom secureRandom = new SecureRandom();

	public MfaService(
			UserRepository userRepository,
			RecoveryCodeRepository recoveryCodeRepository,
			TotpService totpService,
			PasswordEncoder passwordEncoder,
			TokenIssuer tokenIssuer,
			FernbankProperties properties,
			AuditLogger auditLogger) {
		this.userRepository = userRepository;
		this.recoveryCodeRepository = recoveryCodeRepository;
		this.totpService = totpService;
		this.passwordEncoder = passwordEncoder;
		this.tokenIssuer = tokenIssuer;
		this.properties = properties;
		this.auditLogger = auditLogger;
	}

	public record EnrollmentSecret(String secret, String otpAuthUri) {}

	@Transactional
	public EnrollmentSecret enroll(UUID userId) {
		User user = userRepository.findById(userId).orElseThrow(InvalidCredentialsException::new);
		String secret = totpService.generateSecret();
		user.setMfaSecret(secret);
		userRepository.save(user);
		return new EnrollmentSecret(secret, totpService.otpAuthUri(secret, user.getEmail(), ISSUER_NAME));
	}

	@Transactional
	public List<String> confirmEnrollment(UUID userId, String code) {
		User user = userRepository.findById(userId).orElseThrow(InvalidCredentialsException::new);
		if (user.getMfaSecret() == null || !totpService.verifyCode(user.getMfaSecret(), code)) {
			auditLogger.record(userId, "auth.mfa_enroll_failure");
			throw new InvalidMfaChallengeException();
		}
		user.enableMfa();
		userRepository.save(user);

		List<String> rawCodes = generateRecoveryCodes();
		for (String rawCode : rawCodes) {
			recoveryCodeRepository.save(new RecoveryCode(userId, passwordEncoder.encode(rawCode)));
		}
		auditLogger.record(userId, "auth.mfa_enrolled");
		return rawCodes;
	}

	@Transactional
	public LoginResponse verify(String mfaToken, String code) {
		UUID userId = tokenIssuer.validateMfaChallengeToken(mfaToken);
		User user = userRepository.findById(userId).orElseThrow(InvalidMfaChallengeException::new);

		boolean verified = user.getMfaSecret() != null && totpService.verifyCode(user.getMfaSecret(), code);
		if (!verified) {
			verified = tryConsumeRecoveryCode(userId, code);
		}
		if (!verified) {
			auditLogger.record(userId, "auth.mfa_verify_failure");
			throw new InvalidMfaChallengeException();
		}

		auditLogger.record(userId, "auth.mfa_verify_success");
		TokenIssuer.TokenPair tokens = tokenIssuer.issueTokenPair(user);
		return LoginResponse.authenticated(tokens.accessToken(), tokens.refreshToken());
	}

	private boolean tryConsumeRecoveryCode(UUID userId, String code) {
		for (RecoveryCode recoveryCode : recoveryCodeRepository.findByUserIdAndUsedAtIsNull(userId)) {
			if (passwordEncoder.matches(code, recoveryCode.getCodeHash())) {
				recoveryCode.markUsed();
				recoveryCodeRepository.save(recoveryCode);
				auditLogger.record(userId, "auth.mfa_recovery_code_used");
				return true;
			}
		}
		return false;
	}

	private List<String> generateRecoveryCodes() {
		List<String> codes = new ArrayList<>(properties.auth().recoveryCodeCount());
		for (int i = 0; i < properties.auth().recoveryCodeCount(); i++) {
			codes.add(generateRecoveryCode());
		}
		return codes;
	}

	private String generateRecoveryCode() {
		StringBuilder sb = new StringBuilder(RECOVERY_CODE_LENGTH);
		for (int i = 0; i < RECOVERY_CODE_LENGTH; i++) {
			sb.append(RECOVERY_CODE_ALPHABET.charAt(secureRandom.nextInt(RECOVERY_CODE_ALPHABET.length())));
		}
		return sb.toString();
	}
}
