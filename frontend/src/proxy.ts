import { NextResponse } from 'next/server';
import type { NextRequest } from 'next/server';
import { SESSION_COOKIE_NAME } from '@/lib/auth/session-cookie-name';

const PUBLIC_PATHS = new Set(['/', '/login', '/register']);

/**
 * Protects the (app) route group. Presence-check only — the BFF owns this cookie
 * (Phase 8: encrypted, httpOnly, opaque to this process) and is the real authorization
 * boundary on every actual data call. /api/** and /bff/** are excluded via the matcher
 * below, not handled here: on Vercel they're rewritten (next.config.ts) to the bff,
 * which must see unauthenticated requests too (login/register have no session cookie
 * yet) — redirecting those to /login here breaks the login POST itself, since /login
 * has no POST handler and 405s. /api/health (the one real Route Handler left, Phase 9's
 * container healthcheck target) falls out of the same exclusion for the same reason.
 */
export function proxy(request: NextRequest) {
  const { pathname } = request.nextUrl;
  if (PUBLIC_PATHS.has(pathname)) {
    return NextResponse.next();
  }

  if (!request.cookies.has(SESSION_COOKIE_NAME)) {
    const loginUrl = new URL('/login', request.url);
    loginUrl.searchParams.set('from', pathname);
    return NextResponse.redirect(loginUrl);
  }

  return NextResponse.next();
}

export const config = {
  matcher: ['/((?!_next/static|_next/image|favicon.ico|api|bff).*)'],
};
