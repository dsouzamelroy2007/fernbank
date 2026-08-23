/**
 * Must match bff/src/common/session-cookie.ts's SESSION_COOKIE_NAME exactly — the BFF
 * owns and sets this cookie now (Phase 8); this file exists purely so proxy.ts can
 * check for its presence without duplicating the string literal inline. If the BFF's
 * cookie name ever changes, this constant must change with it.
 */
export const SESSION_COOKIE_NAME = 'fernbank_bff_session';
