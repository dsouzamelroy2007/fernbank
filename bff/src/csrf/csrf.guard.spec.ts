import { ForbiddenException, type ExecutionContext } from '@nestjs/common';
import { CsrfGuard } from './csrf.guard';
import { CSRF_COOKIE_NAME, CSRF_HEADER_NAME } from '../common/csrf-cookie';

function contextFor(
  req: Partial<Request> & Record<string, unknown>,
): ExecutionContext {
  return {
    switchToHttp: () => ({
      getRequest: () => req,
    }),
  } as unknown as ExecutionContext;
}

describe('CsrfGuard', () => {
  const guard = new CsrfGuard();

  it('allows GET requests regardless of session or tokens', () => {
    const context = contextFor({
      method: 'GET',
      fernbankSession: { sessionId: 's', refreshToken: 'r' },
    });
    expect(guard.canActivate(context)).toBe(true);
  });

  it('allows a non-GET request with no session yet (login/register)', () => {
    const context = contextFor({
      method: 'POST',
      fernbankSession: null,
      cookies: {},
      headers: {},
    });
    expect(guard.canActivate(context)).toBe(true);
  });

  it('allows a non-GET request when the cookie and header tokens match', () => {
    const context = contextFor({
      method: 'POST',
      fernbankSession: { sessionId: 's', refreshToken: 'r' },
      cookies: { [CSRF_COOKIE_NAME]: 'token-123' },
      headers: { [CSRF_HEADER_NAME]: 'token-123' },
    });
    expect(guard.canActivate(context)).toBe(true);
  });

  it('rejects a non-GET request with a session but no CSRF header', () => {
    const context = contextFor({
      method: 'POST',
      fernbankSession: { sessionId: 's', refreshToken: 'r' },
      cookies: { [CSRF_COOKIE_NAME]: 'token-123' },
      headers: {},
    });
    expect(() => guard.canActivate(context)).toThrow(ForbiddenException);
  });

  it('rejects a non-GET request when the header token does not match the cookie', () => {
    const context = contextFor({
      method: 'POST',
      fernbankSession: { sessionId: 's', refreshToken: 'r' },
      cookies: { [CSRF_COOKIE_NAME]: 'token-123' },
      headers: { [CSRF_HEADER_NAME]: 'wrong-token' },
    });
    expect(() => guard.canActivate(context)).toThrow(ForbiddenException);
  });
});
