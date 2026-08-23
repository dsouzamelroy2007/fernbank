package com.mel.fernbank.ledger.security;

import com.mel.fernbank.ledger.error.CorrelationIdFilter;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ContentSecurityPolicyHeaderWriter;
import org.springframework.security.web.header.writers.DelegatingRequestMatcherHeaderWriter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(FernbankProperties.class)
public class SecurityConfig {

	private static final String[] PUBLIC_ENDPOINTS = {
		"/api/v1/auth/register",
		"/api/v1/auth/login",
		"/api/v1/auth/refresh",
		"/api/v1/auth/mfa/verify",
		"/oauth2/jwks",
		"/v3/api-docs/**",
		"/swagger-ui/**",
		"/swagger-ui.html"
	};

	@Bean
	@Order(Ordered.HIGHEST_PRECEDENCE)
	public SecurityFilterChain actuatorSecurityFilterChain(HttpSecurity http) throws Exception {
		http.securityMatcher("/actuator/**")
				.csrf(csrf -> csrf.disable())
				.sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> auth.requestMatchers("/actuator/health", "/actuator/health/**")
						.permitAll()
						// Prometheus has no OAuth2/JWT client-credentials flow to authenticate with, so
						// this one path accepts HTTP Basic instead of (not in addition to relaxing)
						// the ADMIN-JWT gate every other /actuator/** path keeps below.
						.requestMatchers("/actuator/prometheus")
						.hasRole("PROMETHEUS")
						.anyRequest()
						.hasRole("ADMIN"))
				.httpBasic(basic -> {})
				.oauth2ResourceServer(
						oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(new JwtRolesConverter())));
		return http.build();
	}

	@Bean
	public UserDetailsService actuatorUserDetailsService(
			FernbankProperties properties, PasswordEncoder passwordEncoder) {
		return new InMemoryUserDetailsManager(User.withUsername(properties.metrics().prometheusUser())
				.password(passwordEncoder.encode(properties.metrics().prometheusPassword()))
				.roles("PROMETHEUS")
				.build());
	}

	@Bean
	@Order(Ordered.HIGHEST_PRECEDENCE + 1)
	public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http, FernbankProperties properties)
			throws Exception {
		http.csrf(csrf -> csrf.disable())
				.sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.cors(cors -> cors.configurationSource(corsConfigurationSource(properties)))
				.authorizeHttpRequests(auth -> auth.requestMatchers(PUBLIC_ENDPOINTS)
						.permitAll()
						.anyRequest()
						.authenticated())
				.oauth2ResourceServer(
						oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(new JwtRolesConverter())))
				.headers(headers -> headers.httpStrictTransportSecurity(
								hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31_536_000))
						.referrerPolicy(rp -> rp.policy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
						.addHeaderWriter(new DelegatingRequestMatcherHeaderWriter(
								PathPatternRequestMatcher.pathPattern("/api/v1/**"),
								new ContentSecurityPolicyHeaderWriter(
										"default-src 'self'; frame-ancestors 'none'"))));
		return http.build();
	}

	private CorsConfigurationSource corsConfigurationSource(FernbankProperties properties) {
		CorsConfiguration configuration = new CorsConfiguration();
		configuration.setAllowedOrigins(properties.cors().allowedOrigins());
		configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		configuration.setAllowedHeaders(
			List.of("Authorization", "Content-Type", "Idempotency-Key", CorrelationIdFilter.HEADER));
		configuration.setAllowCredentials(false);
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", configuration);
		return source;
	}
}
