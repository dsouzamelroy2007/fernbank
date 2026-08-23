import {
  CanActivate,
  type ExecutionContext,
  ForbiddenException,
  Injectable,
} from '@nestjs/common';
import type { Request } from 'express';
import { CSRF_COOKIE_NAME, CSRF_HEADER_NAME } from '../common/csrf-cookie';
import { PROBLEM_TYPE_BASE } from '../common/problem-detail';

const SAFE_METHODS = new Set(['GET', 'HEAD', 'OPTIONS']);

/**
 * Double-submit CSRF check. No-ops for safe methods and for any request with no
 * session cookie yet — login/register aren't ambient-credential-riding targets (an
 * attacker forging a login request can't make the victim's browser authenticate as the
 * attacker in a way that harms the victim), so there's nothing to protect until a
 * session actually exists. EventSource (SSE) is GET-only with no custom-header API, so
 * it's naturally exempt via the safe-method check, not a special case.
 */
@Injectable()
export class CsrfGuard implements CanActivate {
  canActivate(context: ExecutionContext): boolean {
    const req = context.switchToHttp().getRequest<Request>();

    if (SAFE_METHODS.has(req.method) || !req.fernbankSession) {
      return true;
    }

    const cookieToken = req.cookies?.[CSRF_COOKIE_NAME] as string | undefined;
    const headerTokenRaw = req.headers[CSRF_HEADER_NAME];
    const headerToken = Array.isArray(headerTokenRaw)
      ? headerTokenRaw[0]
      : headerTokenRaw;

    if (!cookieToken || !headerToken || cookieToken !== headerToken) {
      throw new ForbiddenException({
        type: PROBLEM_TYPE_BASE + 'csrf-token-mismatch',
        title: 'Missing or invalid CSRF token',
      });
    }
    return true;
  }
}
