import {
  type MiddlewareConsumer,
  Module,
  type NestModule,
} from '@nestjs/common';
import { APP_FILTER, APP_GUARD, APP_INTERCEPTOR } from '@nestjs/core';
import { HttpModule } from '@nestjs/axios';
import { ThrottlerGuard, ThrottlerModule } from '@nestjs/throttler';

import { HealthController } from './common/health.controller';
import { ProblemDetailFilter } from './common/problem-detail.filter';

import { CsrfGuard } from './csrf/csrf.guard';
import { throttlerOptions } from './rate-limit/throttler-options';

import { SessionContextMiddleware } from './session/session-context.middleware';
import { SessionCookieSyncInterceptor } from './session/session-cookie-sync.interceptor';
import { SessionCryptoService } from './session/session-crypto.service';
import { SessionService } from './session/session.service';
import { AccessTokenCacheService } from './session/access-token-cache.service';

import { BackendClientService } from './backend-client/backend-client.service';

import { AuthBackendService } from './auth/auth-backend.service';
import { AuthController } from './auth/auth.controller';

import { WarmupController } from './warmup/warmup.controller';

import { ProxyController } from './proxy/proxy.controller';

import { DashboardController } from './dashboard/dashboard.controller';
import { DashboardService } from './dashboard/dashboard.service';

import { NotificationsController } from './notifications/notifications.controller';
import { NotificationPollerService } from './notifications/notification-poller.service';

@Module({
  imports: [HttpModule, ThrottlerModule.forRoot(throttlerOptions)],
  // AuthController first: its literal /api/v1/auth/** routes must never be shadowed by
  // ProxyController's routes — see proxy.controller.ts's own doc comment for why those
  // are registered as literal allowlisted paths rather than a `:resource` wildcard that
  // could otherwise create exactly this ordering risk.
  controllers: [
    HealthController,
    AuthController,
    WarmupController,
    ProxyController,
    DashboardController,
    NotificationsController,
  ],
  providers: [
    SessionCryptoService,
    SessionService,
    AccessTokenCacheService,
    BackendClientService,
    AuthBackendService,
    DashboardService,
    NotificationPollerService,
    { provide: APP_FILTER, useClass: ProblemDetailFilter },
    { provide: APP_GUARD, useClass: ThrottlerGuard },
    { provide: APP_GUARD, useClass: CsrfGuard },
    { provide: APP_INTERCEPTOR, useClass: SessionCookieSyncInterceptor },
  ],
})
export class AppModule implements NestModule {
  configure(consumer: MiddlewareConsumer): void {
    consumer.apply(SessionContextMiddleware).forRoutes('*');
  }
}
