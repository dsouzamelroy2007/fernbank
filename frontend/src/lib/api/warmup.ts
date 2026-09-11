import { resolveBffBase, BACKEND_HEALTH_URL } from '@/lib/env';

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

/**
 * Fires a direct, fire-and-forget wake-up request straight from the browser to the
 * backend's own public URL - see env.ts's BACKEND_HEALTH_URL doc comment for why.
 * `mode: 'no-cors'` sends a real request without needing any backend CORS
 * configuration (the browser still delivers it; we just can't read anything back,
 * which is fine - this call's only job is to trigger Render waking the container, not
 * to report readiness). A no-op if BACKEND_HEALTH_URL isn't configured, and errors are
 * swallowed - this is a best-effort assist alongside the real readiness check
 * (checkWarmup, via the bff), never a dependency of it.
 */
export function pingBackendDirectly(): void {
  if (!BACKEND_HEALTH_URL) return;
  fetch(BACKEND_HEALTH_URL, { mode: 'no-cors', cache: 'no-store' }).catch(() => undefined);
}
