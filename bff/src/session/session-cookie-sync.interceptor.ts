import {
  Injectable,
  type CallHandler,
  type ExecutionContext,
  type NestInterceptor,
} from '@nestjs/common';
import type { Request, Response } from 'express';
import { Observable, tap } from 'rxjs';
import { AccessTokenCacheService } from './access-token-cache.service';
import { SessionService } from './session.service';

/**
 * Decouples "a refresh rotated the token" from "the cookie gets rewritten": whoever
 * resolves an access token during request handling (the proxy, dashboard, mfa/step-up
 * handlers) just updates AccessTokenCacheService — this interceptor, running once after
 * the handler completes, compares the cache's current refresh token against the one the
 * request came in with and re-writes the session cookie only if it actually rotated.
 * Guarded by `headersSent` so it's a harmless no-op on an already-streaming SSE
 * response, where headers were flushed at subscribe time.
 */
@Injectable()
export class SessionCookieSyncInterceptor implements NestInterceptor {
  constructor(
    private readonly tokenCache: AccessTokenCacheService,
    private readonly sessionService: SessionService,
  ) {}

  intercept(context: ExecutionContext, next: CallHandler): Observable<unknown> {
    const req = context.switchToHttp().getRequest<Request>();
    const res = context.switchToHttp().getResponse<Response>();
    const session = req.fernbankSession;

    return next.handle().pipe(
      tap(() => {
        if (!session || res.headersSent) {
          return;
        }
        const currentRefreshToken = this.tokenCache.getCurrentRefreshToken(
          session.sessionId,
        );
        if (
          currentRefreshToken &&
          currentRefreshToken !== session.refreshToken
        ) {
          this.sessionService.rotateSession(
            res,
            session.sessionId,
            currentRefreshToken,
          );
        }
      }),
    );
  }
}
