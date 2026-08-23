import { Injectable } from '@nestjs/common';
import { randomBytes, randomUUID } from 'node:crypto';
import type { Response } from 'express';
import { SessionCryptoService } from './session-crypto.service';
import {
  SESSION_COOKIE_NAME,
  sessionCookieOptions,
} from '../common/session-cookie';
import { CSRF_COOKIE_NAME, csrfCookieOptions } from '../common/csrf-cookie';

@Injectable()
export class SessionService {
  constructor(private readonly crypto: SessionCryptoService) {}

  /** Mints a fresh sessionId and writes both cookies — called only on a true
   * AUTHENTICATED login/register/mfa-verify result, never on MFA_REQUIRED. */
  startSession(response: Response, refreshToken: string): string {
    const sessionId = randomUUID();
    this.writeSessionCookie(response, sessionId, refreshToken);
    this.writeCsrfCookie(response);
    return sessionId;
  }

  /** Re-sets the session cookie with a rotated refresh token, same sessionId. */
  rotateSession(
    response: Response,
    sessionId: string,
    refreshToken: string,
  ): void {
    this.writeSessionCookie(response, sessionId, refreshToken);
  }

  clearSession(response: Response): void {
    response.clearCookie(SESSION_COOKIE_NAME, sessionCookieOptions());
    response.clearCookie(CSRF_COOKIE_NAME, csrfCookieOptions());
  }

  private writeSessionCookie(
    response: Response,
    sessionId: string,
    refreshToken: string,
  ): void {
    const encrypted = this.crypto.encrypt({ sessionId, refreshToken });
    response.cookie(SESSION_COOKIE_NAME, encrypted, sessionCookieOptions());
  }

  private writeCsrfCookie(response: Response): void {
    const csrfToken = randomBytes(32).toString('base64url');
    response.cookie(CSRF_COOKIE_NAME, csrfToken, csrfCookieOptions());
  }
}
