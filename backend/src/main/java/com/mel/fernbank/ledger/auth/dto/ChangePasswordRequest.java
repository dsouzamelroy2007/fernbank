package com.mel.fernbank.ledger.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
		@NotBlank String currentPassword,
		@Schema(description = "Minimum 8 characters.") @NotBlank @Size(min = 8) String newPassword) {}
