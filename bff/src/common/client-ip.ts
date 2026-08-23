import type { Request } from 'express';

/** Reads the real client IP off an incoming request. Express's req.ip is only the bff's
 * immediate socket peer - on Vercel that's the edge rewrite's connection, not the
 * browser, since neither hop is a shared connection. X-Forwarded-For's first entry is
 * the original client per Vercel's edge; falls back to req.ip when the header is absent
 * (local Docker Compose, or a direct call bypassing Vercel). Paired with
 * config.internalServiceKey on the outbound call to the backend - see
 * auth-backend.service.ts - so the backend only trusts this when it can verify the
 * call actually came from this bff. */
export function extractClientIp(req: Request): string {
  const header = req.headers['x-forwarded-for'];
  const value = Array.isArray(header) ? header[0] : header;
  const firstHop = value?.split(',')[0]?.trim();
  return firstHop && firstHop.length > 0 ? firstHop : (req.ip ?? '');
}
