package com.mel.fernbank.ledger.security;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

public class JwtRolesConverter implements Converter<Jwt, AbstractAuthenticationToken> {

	static final String TOKEN_USE_CLAIM = "token_use";
	static final String ACCESS_TOKEN_USE = "access";
	static final String ROLES_CLAIM = "roles";

	@Override
	public AbstractAuthenticationToken convert(Jwt jwt) {
		if (!ACCESS_TOKEN_USE.equals(jwt.getClaimAsString(TOKEN_USE_CLAIM))) {
			throw new InvalidBearerTokenException("Token is not usable as an access token");
		}
		return new JwtAuthenticationToken(jwt, extractAuthorities(jwt));
	}

	private Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
		List<String> roles = jwt.getClaimAsStringList(ROLES_CLAIM);
		if (roles == null) {
			return List.of();
		}
		return roles.stream().map(SimpleGrantedAuthority::new).collect(Collectors.toList());
	}
}
