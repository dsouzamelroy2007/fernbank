package com.mel.fernbank.ledger.api.dto;

/** Zero-field marker returned by idempotency-guarded operations whose HTTP response is 204 No Content. */
public record NoContentResult() {}
