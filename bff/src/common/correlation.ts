import { randomUUID } from 'node:crypto';
import type { Request } from 'express';

/** Matches error.CorrelationIdFilter.HEADER on the backend and
 * frontend/src/lib/api/correlation.ts's CORRELATION_ID_HEADER exactly. */
export const CORRELATION_ID_HEADER = 'X-Correlation-Id';

export function newCorrelationId(): string {
  return randomUUID();
}

/** Reads the browser-minted correlation id off an incoming request so a single
 * request can be traced through the bff and into the backend under one id, instead of
 * each hop minting its own. Falls back to minting one when the caller didn't send it
 * (e.g. EventSource can't set custom headers) — every request still gets exactly one
 * id, just not always the browser's. Express lowercases incoming header names. */
export function extractCorrelationId(req: Request): string {
  const header = req.headers['x-correlation-id'];
  const value = Array.isArray(header) ? header[0] : header;
  return value && value.length > 0 ? value : newCorrelationId();
}
