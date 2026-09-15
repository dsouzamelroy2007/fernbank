import { UnauthorizedException } from '@nestjs/common';
import type { Request } from 'express';
import type { SessionPayload } from './session-crypto.service';
import { PROBLEM_TYPE_BASE } from '../common/problem-detail';

export function requireSession(req: Request): SessionPayload {
  if (!req.fernbankSession) {
    throw new UnauthorizedException({
      type: PROBLEM_TYPE_BASE + 'session-required',
      title: 'No active session',
    });
  }
  return req.fernbankSession;
}
