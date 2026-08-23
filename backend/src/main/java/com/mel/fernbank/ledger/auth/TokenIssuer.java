package com.mel.fernbank.ledger.auth;

import com.mel.fernbank.ledger.domain.RefreshToken;
import com.mel.fernbank.ledger.domain.User;
import com.mel.fernbank.ledger.repository.RefreshTokenRepository;
import com.mel.fernbank.ledger.security.FernbankProperties;
import com.mel.fernbank.ledger.security.TokenHasher;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

/** Everything that creates or reads one of this app's JWTs or refresh tokens. */
@Component
public class TokenIssuer {

	private static final String CLAIM_TOKEN_USE = "token_use";
	private static final String CLAIM_CUSTOMER_ID = "customer_id";
	private static final String CLAIM_ROLES = "roles";
	private static final String CLAIM_ELEVATED_UNTIL = "elevated_until";
	private static final String TOKEN_USE_ACCESS = "access";
	private static final String TOKEN_USE_MFA_CHALLENGE = "mfa_challenge";

	private final JwtEncoder jwtEncoder;
	private final JwtDecoder jwtDecoder;
	private final FernbankProperties properties;
	private final RefreshTokenRepository refreshTokenRepository;

	public TokenIssuer(
			JwtEncoder jwtEncoder,
			JwtDecoder jwtDecoder,
			FernbankProperties properties,
			RefreshTokenRepository refreshTokenRepository) {
		this.jwtEncoder = jwtEncoder;
		this.jwtDecoder = jwtDecoder;
		this.properties = properties;
		this.refreshTokenRepository = refreshTokenRepository;
	}

	public record TokenPair(String accessToken, String refreshToken) {}

	public record IssuedRefreshToken(String rawToken, RefreshToken entity) {}

	public TokenPair issueTokenPair(User user) {
		String access = issueAccessToken(user, null);
		IssuedRefreshToken refresh = issueRefreshToken(user, UUID.randomUUID());
		return new TokenPair(access, refresh.rawToken());
	}

	public String issueAccessToken(User user, Instant elevatedUntil) {
		Instant now = Instant.now();
		List<String> roleClaims =
				user.getRoles().stream().map(role -> "ROLE_" + role.name()).toList();
		JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
				.issuer(properties.jwt().issuer())
				.issuedAt(now)
				.expiresAt(now.plus(properties.jwt().accessTokenTtl()))
				.subject(user.getId().toString())
				.claim(CLAIM_CUSTOMER_ID, user.getCustomerId().toString())
				.claim(CLAIM_ROLES, roleClaims)
				.claim(CLAIM_TOKEN_USE, TOKEN_USE_ACCESS);
		if (elevatedUntil != null) {
			claims.claim(CLAIM_ELEVATED_UNTIL, elevatedUntil.getEpochSecond());
		}
		return encode(claims.build());
	}

	public String issueElevatedAccessToken(User user) {
		Instant elevatedUntil = Instant.now().plus(properties.jwt().stepUpTtl());
		return issueAccessToken(user, elevatedUntil);
	}

	public String issueMfaChallengeToken(User user) {
		Instant now = Instant.now();
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuer(properties.jwt().issuer())
				.issuedAt(now)
				.expiresAt(now.plus(properties.jwt().mfaChallengeTtl()))
				.subject(user.getId().toString())
				.claim(CLAIM_TOKEN_USE, TOKEN_USE_MFA_CHALLENGE)
				.build();
		return encode(claims);
	}

	public UUID validateMfaChallengeToken(String token) {
		Jwt jwt;
		try {
			jwt = jwtDecoder.decode(token);
		} catch (JwtException e) {
			throw new InvalidMfaChallengeException();
		}
		if (!TOKEN_USE_MFA_CHALLENGE.equals(jwt.getClaimAsString(CLAIM_TOKEN_USE))) {
			throw new InvalidMfaChallengeException();
		}
		return UUID.fromString(jwt.getSubject());
	}

	public IssuedRefreshToken issueRefreshToken(User user, UUID familyId) {
		String raw = TokenHasher.generateOpaqueToken();
		RefreshToken entity = new RefreshToken(
				user.getId(),
				TokenHasher.sha256Hex(raw),
				familyId,
				Instant.now().plus(properties.jwt().refreshTokenTtl()));
		refreshTokenRepository.save(entity);
		return new IssuedRefreshToken(raw, entity);
	}

	private String encode(JwtClaimsSet claims) {
		return jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
	}
}
