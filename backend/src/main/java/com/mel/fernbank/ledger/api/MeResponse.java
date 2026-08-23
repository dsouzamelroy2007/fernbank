package com.mel.fernbank.ledger.api;

import java.util.UUID;

public record MeResponse(UUID userId, UUID customerId, String email, String fullName) {}
