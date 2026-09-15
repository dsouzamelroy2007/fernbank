import { Injectable, type NestMiddleware } from '@nestjs/common';
import type { NextFunction, Request, Response } from 'express';
import { SessionCryptoService } from './session-crypto.service';
import { SESSION_COOKIE_NAME } from '../common/session-cookie';

@Injectable()
export class SessionContextMiddleware implements NestMiddleware {
  constructor(private readonly crypto: SessionCryptoService) {}

  use(req: Request, _res: Response, next: NextFunction): void {
    const cookieValue = req.cookies?.[SESSION_COOKIE_NAME] as
      string | undefined;
    req.fernbankSession = cookieValue ? this.crypto.decrypt(cookieValue) : null;
    next();
  }
}
