export const CORRELATION_ID_HEADER = 'X-Correlation-Id';

export function newCorrelationId(): string {
  return crypto.randomUUID();
}
