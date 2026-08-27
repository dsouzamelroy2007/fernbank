import { resolveBffBase } from '@/lib/env';

/** Hits the bff's unauthenticated readiness probe (bff/src/warmup/warmup.controller.ts)
 * once. Not part of the generated OpenAPI client - this is diagnostic traffic, not a
 * backend business endpoint. `signal` bounds a single attempt so a cold/hanging call
 * doesn't block the next poll. */
export async function checkWarmup(signal: AbortSignal): Promise<boolean> {
  try {
    const response = await fetch(new URL('/api/v1/warmup', resolveBffBase()), { signal });
    return response.ok;
  } catch {
    return false;
  }
}
