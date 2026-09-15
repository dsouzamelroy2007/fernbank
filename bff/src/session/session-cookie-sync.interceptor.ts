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
