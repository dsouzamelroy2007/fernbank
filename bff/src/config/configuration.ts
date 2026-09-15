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

export const config = {
  port: optionalInt(process.env.PORT, 4000),

  backendInternalBaseUrl: required(
    'BACKEND_INTERNAL_BASE_URL',
    process.env.BACKEND_INTERNAL_BASE_URL,
  ),

  corsAllowedOrigins: required(
    'BFF_CORS_ALLOWED_ORIGINS',
    process.env.BFF_CORS_ALLOWED_ORIGINS,
  )
    .split(',')
    .map((origin) => origin.trim())
    .filter(Boolean),

  sessionEncryptionKey: loadOrGenerateBase64Key(
    'BFF_SESSION_ENCRYPTION_KEY',
    process.env.BFF_SESSION_ENCRYPTION_KEY,
  ),

  sessionCookieDomain: process.env.BFF_SESSION_COOKIE_DOMAIN || undefined,

  accessTokenRefreshSkewMs: optionalInt(
    process.env.BFF_ACCESS_TOKEN_REFRESH_SKEW_MS,
    30_000,
  ),

  notificationPollIntervalMs: optionalInt(
    process.env.BFF_NOTIFICATION_POLL_INTERVAL_MS,
    5_000,
  ),

  internalServiceKey: process.env.INTERNAL_SERVICE_KEY || undefined,
};
