package com.mel.fernbank.ledger.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

	private static final String BEARER_SCHEME = "bearerAuth";
	private static final String PROBLEM_DETAIL_SCHEMA = "ProblemDetail";

	@Bean
	public OpenAPI fernbankOpenApi() {
		return new OpenAPI()
				.info(new Info()
						.title("fernbank ledger API")
						.version("v1")
						.description(
								"Portfolio-quality banking/ledger API. Educational project - play money "
										+ "only, not a real financial service.")
						.license(new License().name("MIT").url("https://opensource.org/licenses/MIT")))
				.servers(List.of(new Server().url("http://localhost:8080").description("Local development")))
				.tags(List.of(
						new Tag().name("Accounts").description("Open, list, and read accounts; deposits, withdrawals, statements."),
						new Tag().name("Transfers").description("Internal transfers, including scheduled transfers."),
						new Tag().name("Payees").description("Saved transfer recipients."),
						new Tag().name("Admin").description("ROLE_ADMIN-only: reconciliation, account freeze, audit log."),
						new Tag().name("Auth").description("Registration, login, token refresh, logout, step-up elevation."),
						new Tag().name("MFA").description("TOTP enrollment and verification."),
						new Tag().name("Profile").description("The authenticated user's own identity."),
						new Tag().name("JWKS").description("Public JSON Web Key Set for verifying access tokens.")))
				.addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
				.components(new Components()
						.addSecuritySchemes(
								BEARER_SCHEME,
								new SecurityScheme()
										.name(BEARER_SCHEME)
										.type(SecurityScheme.Type.HTTP)
										.scheme("bearer")
										.bearerFormat("JWT"))
						.addSchemas(PROBLEM_DETAIL_SCHEMA, problemDetailSchema())
						.addResponses("BadRequest", problemResponse("Malformed request - bad input shape, missing header, or a business rule that isn't a field-level validation failure."))
						.addResponses("ValidationFailed", problemResponse("Request failed Bean Validation; the response's `errors` extension lists each field error."))
						.addResponses("Unauthorized", problemResponse("Missing, expired, tampered, or otherwise invalid credentials."))
						.addResponses("Forbidden", problemResponse("Authenticated, but not permitted to perform this action (wrong role, or step-up elevation required)."))
						.addResponses("NotFound", problemResponse("The resource doesn't exist, or exists but isn't owned by the authenticated customer - identical response either way, so existence is never leaked."))
						.addResponses("Conflict", problemResponse("The resource is not in a state that allows this operation, or the same Idempotency-Key was reused with a different request body."))
						.addResponses("UnprocessableContent", problemResponse("The request is well-formed but violates a domain rule (e.g. insufficient funds, currency mismatch)."))
						.addResponses("TooManyRequests", problemResponse("Rate limit exceeded."))
						.addResponses("InternalError", problemResponse("An unexpected server error. Never includes a stack trace or SQL - correlate via `correlationId`.")));
	}

	private ApiResponse problemResponse(String description) {
		return new ApiResponse()
				.description(description)
				.content(new Content()
						.addMediaType(
								"application/problem+json",
								new MediaType().schema(new Schema<>().$ref("#/components/schemas/" + PROBLEM_DETAIL_SCHEMA))));
	}

	private Schema<?> problemDetailSchema() {
		return new Schema<>()
				.type("object")
				.description("RFC 9457 Problem Details, as produced by every error response in this API.")
				.addProperty("type", new Schema<>().type("string").format("uri").example("https://fernbank.dev/problems/insufficient-funds"))
				.addProperty("title", new Schema<>().type("string").example("Insufficient Funds"))
				.addProperty("status", new Schema<>().type("integer").format("int32").example(422))
				.addProperty("detail", new Schema<>().type("string").example("Account 3c1f2e2a-... has insufficient funds"))
				.addProperty("instance", new Schema<>().type("string").format("uri"))
				.addProperty("correlationId", new Schema<>().type("string").format("uuid").description("Echoes the X-Correlation-Id request header; auto-assigned if absent."));
	}
}
