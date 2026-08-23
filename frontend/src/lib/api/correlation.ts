export const CORRELATION_ID_HEADER = 'X-Correlation-Id';

/** One place to generate correlation ids, shared by the browser client and every Route Handler. */
export function newCorrelationId(): string {
  return crypto.randomUUID();
}
