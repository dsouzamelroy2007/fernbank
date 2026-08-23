package com.mel.fernbank.ledger.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
		@NotBlank String fullName,
		@NotBlank @Email String email,
		@Schema(description = "Minimum 8 characters.") @NotBlank @Size(min = 8) String password) {}
