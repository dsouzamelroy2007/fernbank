package com.mel.fernbank.ledger.auth.dto;

import java.util.UUID;

public record RegisterResponse(UUID userId, String email) {}
