import { config } from '../config/configuration';

/**
 * Must match frontend/src/lib/auth/session-cookie-name.ts EXACTLY — proxy.ts's route
 * gate checks for this cookie's mere presence and has no other way to learn its name.
 * This is deliberate cross-project string coupling; if this ever changes, that file
 * must change with it.
 */
export const SESSION_COOKIE_NAME = 'fernbank_bff_session';

/** Matches the backend's own (hardcoded, non-env-overridable) refresh-token-ttl: 30d. */
const REFRESH_TOKEN_TTL_SECONDS = 60 * 60 * 24 * 30;

export function sessionCookieOptions() {
  return {
    httpOnly: true,
    secure: process.env.NODE_ENV === 'production',
    sameSite: 'lax' as const,
    path: '/',
    domain: config.sessionCookieDomain,
    maxAge: REFRESH_TOKEN_TTL_SECONDS * 1000,
  };
}
