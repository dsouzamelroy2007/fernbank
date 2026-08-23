import { Controller, type MessageEvent, Req, Sse } from '@nestjs/common';
import { SkipThrottle } from '@nestjs/throttler';
import type { Request } from 'express';
import { map, type Observable } from 'rxjs';
import { NotificationPollerService } from './notification-poller.service';
import { requireSession } from '../session/require-session';

@Controller('bff/notifications')
export class NotificationsController {
  constructor(private readonly poller: NotificationPollerService) {}

  /** One long-lived connection, not many short requests — excluded from the request-
   * count throttle. EventSource is GET-only with no custom-header API, so CsrfGuard's
   * safe-method check already exempts it too. */
  @Sse()
  @SkipThrottle()
  stream(@Req() req: Request): Observable<MessageEvent> {
    const session = requireSession(req);
    return this.poller
      .subscribe(session)
      .pipe(map((event) => ({ type: 'transaction', data: event })));
  }
}
