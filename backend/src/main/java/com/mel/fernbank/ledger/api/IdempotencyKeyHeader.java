package com.mel.fernbank.ledger.api;

import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@io.swagger.v3.oas.annotations.Parameter(
		name = "Idempotency-Key",
		in = ParameterIn.HEADER,
		required = true,
		description = "Client-generated UUID. Replaying the same key with the same request body "
				+ "returns the original response unchanged; the same key with a different body is a 409.",
		schema = @Schema(type = "string", format = "uuid"),
		example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
public @interface IdempotencyKeyHeader {}
