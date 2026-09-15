import { config } from '../config/configuration';

export const SESSION_COOKIE_NAME = 'fernbank_bff_session';

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
