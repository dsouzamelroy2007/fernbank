import type { ThrottlerModuleOptions } from '@nestjs/throttler';
import type { Request } from 'express';

/**
 * Keyed by sessionId where one exists; pre-session endpoints (login/register) fall
 * back to IP — a second, coarser layer in front of (not a replacement for) the
 * backend's own (ip, email) login limiter. Thresholds hardcoded, matching the
 * backend's own precedent of hardcoded JWT TTLs, not new env surface.
 */
export const throttlerOptions: ThrottlerModuleOptions = {
  throttlers: [{ name: 'default', ttl: 60_000, limit: 120 }],
  getTracker: (req: Request) =>
    Promise.resolve(req.fernbankSession?.sessionId ?? req.ip ?? 'unknown'),
};
