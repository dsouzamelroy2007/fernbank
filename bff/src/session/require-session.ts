import { UnauthorizedException } from '@nestjs/common';
import type { Request } from 'express';
import type { SessionPayload } from './session-crypto.service';
import { PROBLEM_TYPE_BASE } from '../common/problem-detail';

/** Every proxy/dashboard/mfa/step-up handler needs a real session — this is the one
 * place that check and its error shape live. */
export function requireSession(req: Request): SessionPayload {
  if (!req.fernbankSession) {
    throw new UnauthorizedException({
      type: PROBLEM_TYPE_BASE + 'session-required',
      title: 'No active session',
    });
  }
  return req.fernbankSession;
}
