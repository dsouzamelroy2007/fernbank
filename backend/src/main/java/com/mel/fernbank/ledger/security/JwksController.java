package com.mel.fernbank.ledger.security;

import com.nimbusds.jose.jwk.JWK;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "JWKS")
public class JwksController {

	private final List<JWK> publicJwks;

	public JwksController(List<JWK> publicJwks) {
		this.publicJwks = publicJwks;
	}

	@Operation(
			summary = "Public JSON Web Key Set",
			description = "Public key material only - safe to expose without authentication, used by "
					+ "resource servers/clients to verify access-token signatures.")
	@ApiResponses({@ApiResponse(responseCode = "200", description = "The current public JWK set")})
	@GetMapping("/oauth2/jwks")
	public Map<String, Object> jwks() {
		return Map.of(
				"keys",
				publicJwks.stream().map(JWK::toJSONObject).collect(Collectors.toList()));
	}
}
