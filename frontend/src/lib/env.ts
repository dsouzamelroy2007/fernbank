function required(name: string, value: string | undefined): string {
  if (value === undefined) {
    throw new Error(`Missing required environment variable: ${name}`);
  }
  return value;
}

/**
 * Browser-facing BFF origin, inlined into the client bundle at build time. The browser
 * talks to the NestJS BFF exclusively (Phase 8) — it never reaches the Spring Boot
 * backend directly, and holds no JWT of its own; the BFF owns the session cookie and
 * exchanges it for the backend's access token server-side.
 *
 * On Vercel this should resolve to an empty string: the frontend (Vercel) and bff
 * (Fly.io) sit on different registrable domains in production, and the session
 * cookie's SameSite=Lax would never be sent cross-site. next.config.ts's rewrites()
 * makes bff calls same-origin instead, so the browser should build relative URLs — use
 * {@link resolveBffBase} rather than this constant directly wherever a URL base is
 * needed, since `new URL(path, '')` throws.
 *
 * Deliberately does NOT require the caller to set NEXT_PUBLIC_BFF_BASE_URL to an
 * explicit empty string on Vercel - that depends on a Vercel env-var UI correctly
 * persisting an empty value, which is exactly the kind of thing that silently doesn't
 * happen and fails prerendering with a confusing "/_not-found" build error instead of
 * a clear message. `VERCEL=1` is auto-set by Vercel on every build with no
 * configuration needed, so an absent var there is unambiguously "intentionally
 * relative," while every other environment (Docker Compose, local `npm run build`)
 * still fails fast on a genuinely missing var.
 */
export const BFF_BASE_URL = process.env.VERCEL
  ? (process.env.NEXT_PUBLIC_BFF_BASE_URL ?? '')
  : required('NEXT_PUBLIC_BFF_BASE_URL', process.env.NEXT_PUBLIC_BFF_BASE_URL);

/** See {@link BFF_BASE_URL}'s doc comment. Client-only (reads `window.location`) —
 * every current caller is a 'use client' fetch/EventSource call site. */
export function resolveBffBase(): string {
  return BFF_BASE_URL || window.location.origin;
}

/**
 * Backend's own public health-check URL (e.g.
 * `https://fernbank-api.onrender.com/actuator/health`), used only to fire a direct
 * wake-up ping from the browser - see warmup.ts's pingBackendDirectly.
 * Optional and unset by default (a no-op then): local Docker Compose has no
 * hibernation to work around, and this is specifically a workaround for Render's free
 * tier, confirmed live (2026-09-11) to rate-limit wake-up requests that originate from
 * the bff's own Render-assigned origin (`x-render-routing: hibernate-rate-limited`)
 * while an otherwise-identical request from a real browser wakes the backend cleanly.
 * Firing this same kind of request from the browser directly sidesteps that.
 */
export const BACKEND_HEALTH_URL = process.env.NEXT_PUBLIC_BACKEND_HEALTH_URL || undefined;
