import { config } from '../config/configuration';

/** Non-httpOnly by design — the frontend reads this value and echoes it as the
 * X-CSRF-Token header on every mutating request (double-submit pattern). */
export const CSRF_COOKIE_NAME = 'fernbank_bff_csrf';
export const CSRF_HEADER_NAME = 'x-csrf-token';

const REFRESH_TOKEN_TTL_SECONDS = 60 * 60 * 24 * 30;

export function csrfCookieOptions() {
  return {
    httpOnly: false,
    secure: process.env.NODE_ENV === 'production',
    sameSite: 'lax' as const,
    path: '/',
    domain: config.sessionCookieDomain,
    maxAge: REFRESH_TOKEN_TTL_SECONDS * 1000,
  };
}
