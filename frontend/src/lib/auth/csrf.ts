/**
 * Must match bff/src/common/csrf-cookie.ts's CSRF_COOKIE_NAME/CSRF_HEADER_NAME
 * exactly — the BFF sets this non-httpOnly cookie alongside the session cookie on
 * login/register/mfa-verify; the frontend reads its value here and echoes it back as a
 * header on every mutating request (double-submit CSRF pattern).
 */
const CSRF_COOKIE_NAME = 'fernbank_bff_csrf';
export const CSRF_HEADER_NAME = 'X-CSRF-Token';

export function readCsrfToken(): string | undefined {
  if (typeof document === 'undefined') {
    return undefined;
  }
  const match = document.cookie
    .split('; ')
    .find((entry) => entry.startsWith(`${CSRF_COOKIE_NAME}=`));
  return match?.slice(CSRF_COOKIE_NAME.length + 1);
}
