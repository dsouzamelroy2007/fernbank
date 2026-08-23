import { randomBytes } from 'node:crypto';

function required(name: string, value: string | undefined): string {
  if (!value) {
    throw new Error(`Missing required environment variable: ${name}`);
  }
  return value;
}

function optionalInt(value: string | undefined, fallback: number): number {
  if (!value) return fallback;
  const parsed = Number.parseInt(value, 10);
  if (Number.isNaN(parsed)) {
    throw new Error(`Expected an integer, got: ${value}`);
  }
  return parsed;
}

/** Matches the backend's own MfaSecretConverter/JwtKeys ephemeral-key-with-warning
 * fallback exactly, so `docker compose up` on a fresh clone works with no .env at all —
 * sessions encrypted this run won't decrypt after a restart if the env var was unset. */
function loadOrGenerateBase64Key(
  name: string,
  value: string | undefined,
): string {
  if (value) {
    return value;
  }
  const key = randomBytes(32).toString('base64');
  console.warn(
    `WARNING: ${name} not set — generated an ephemeral key. Sessions encrypted this run will not decrypt after a restart.`,
  );
  return key;
}

/**
 * Typed, fail-fast env loader — mirrors frontend/src/lib/env.ts's required() pattern.
 * Computed once at import time, not re-read per request.
 */
export const config = {
  port: optionalInt(process.env.PORT, 4000),

  /** Compose service DNS (e.g. http://backend:8080) — this process is Spring Boot's only caller. */
  backendInternalBaseUrl: required(
    'BACKEND_INTERNAL_BASE_URL',
    process.env.BACKEND_INTERNAL_BASE_URL,
  ),

  /** Exact browser origin(s) allowed to send credentialed (cookie-bearing) requests. */
  corsAllowedOrigins: required(
    'BFF_CORS_ALLOWED_ORIGINS',
    process.env.BFF_CORS_ALLOWED_ORIGINS,
  )
    .split(',')
    .map((origin) => origin.trim())
    .filter(Boolean),

  /** base64-encoded 32 random bytes — AES-256-GCM key for the session cookie payload. */
  sessionEncryptionKey: loadOrGenerateBase64Key(
    'BFF_SESSION_ENCRYPTION_KEY',
    process.env.BFF_SESSION_ENCRYPTION_KEY,
  ),

  /** Unset in dev (localhost is always same-site regardless of port) — see docs/adr for the
   * production cross-subdomain cookie decision. */
  sessionCookieDomain: process.env.BFF_SESSION_COOKIE_DOMAIN || undefined,

  accessTokenRefreshSkewMs: optionalInt(
    process.env.BFF_ACCESS_TOKEN_REFRESH_SKEW_MS,
    30_000,
  ),

  notificationPollIntervalMs: optionalInt(
    process.env.BFF_NOTIFICATION_POLL_INTERVAL_MS,
    5_000,
  ),

  /** Shared secret with the backend (same value on both services' env) - proves to the
   * backend that a forwarded X-Forwarded-For header on an /auth/login call actually
   * came from this bff, not an arbitrary caller of the backend's own public URL.
   * Unset (default) means the login-IP-forwarding headers are simply not sent - safe,
   * same as today's behavior, not a startup requirement. */
  internalServiceKey: process.env.INTERNAL_SERVICE_KEY || undefined,
};
