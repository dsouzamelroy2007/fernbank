import type { ThrottlerModuleOptions } from '@nestjs/throttler';
import type { Request } from 'express';

export const throttlerOptions: ThrottlerModuleOptions = {
  throttlers: [{ name: 'default', ttl: 60_000, limit: 120 }],
  getTracker: (req: Request) =>
    Promise.resolve(req.fernbankSession?.sessionId ?? req.ip ?? 'unknown'),
};
