import { randomUUID } from 'node:crypto';
import type { Request } from 'express';

export const CORRELATION_ID_HEADER = 'X-Correlation-Id';

export function newCorrelationId(): string {
  return randomUUID();
}

export function extractCorrelationId(req: Request): string {
  const header = req.headers['x-correlation-id'];
  const value = Array.isArray(header) ? header[0] : header;
  return value && value.length > 0 ? value : newCorrelationId();
}
