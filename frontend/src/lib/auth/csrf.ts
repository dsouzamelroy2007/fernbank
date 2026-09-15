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
