import {
  Controller,
  Get,
  HttpCode,
  Logger,
  Req,
  ServiceUnavailableException,
} from '@nestjs/common';
import { HttpService } from '@nestjs/axios';
import { firstValueFrom } from 'rxjs';
import type { Request } from 'express';
import { SkipThrottle } from '@nestjs/throttler';
import { config } from '../config/configuration';
import {
  CORRELATION_ID_HEADER,
  extractCorrelationId,
} from '../common/correlation';
import { PROBLEM_TYPE_BASE } from '../common/problem-detail';

/** Generous on purpose - a genuinely cold backend + Neon has taken 90-180s+ to
 * respond in practice (see this file's doc comment). A short timeout here doesn't just
 * make one poll fail faster - it tears down this exact outbound connection to the
 * backend before Render's wake-up can complete, so the backend never gets a fair,
 * uninterrupted shot at finishing its boot at all. */
const BACKEND_HEALTH_TIMEOUT_MS = 170_000;

/**
 * Unauthenticated readiness probe for the login page's "waking up" poll - lets the
 * frontend confirm the backend (and, via its own /actuator/health DB check, Neon) is
 * actually responsive before ever submitting real credentials to POST /auth/login.
 * Render's free tier sleeps this bff and the backend independently after ~15 min idle;
 * without this, the first real login attempt races a multi-minute cold start against
 * Vercel's shorter rewrite timeout, silently fails client-side, and each retry still
 * burns a LoginRateLimiter token server-side even though the user never sees a
 * successful response - the actual bug behind the 429s on a cold demo.
 *
 * Confirmed live (2026-08-28): an earlier version of this file used a 10s timeout here.
 * Live logs showed the bff itself waking on the first request, but the backend never
 * logged anything at all after 6+ minutes - each poll was aborting its connection to
 * the backend long before a cold boot (90-180s+) could finish, so the backend was
 * repeatedly interrupted before it ever got a real chance to come up.
 *
 * Confirmed live (2026-08-30): raising the timeout above didn't fix it - logs showed
 * this reaching the backend's URL immediately every time (sub-second, not timing out)
 * and getting back a plain-text `429 Too Many Requests`, repeated every ~4-10s. That's
 * Render's own infrastructure rate-limiting repeated wake requests to a sleeping
 * service, before the backend ever gets a chance to boot - not anything in this app's
 * control. The real fix was slowing the frontend's poll interval down (see
 * use-backend-warmup.ts) so requests hit this endpoint far less often.
 */
@Controller('api/v1/warmup')
@SkipThrottle()
export class WarmupController {
  private readonly logger = new Logger(WarmupController.name);
  private readonly targetUrl = `${config.backendInternalBaseUrl}/actuator/health`;

  constructor(private readonly http: HttpService) {}

  @Get()
  @HttpCode(200)
  async check(@Req() req: Request): Promise<{ status: 'UP' }> {
    try {
      const response = await firstValueFrom(
        this.http.request<{ status?: string }>({
          method: 'GET',
          url: this.targetUrl,
          timeout: BACKEND_HEALTH_TIMEOUT_MS,
          validateStatus: () => true,
          headers: { [CORRELATION_ID_HEADER]: extractCorrelationId(req) },
        }),
      );
      if (response.status === 200 && response.data?.status === 'UP') {
        return { status: 'UP' };
      }
      // Reached the backend but got something other than a healthy 200 - worth
      // knowing exactly what, since "still starting up" isn't the only way to land here.
      // Headers included specifically to catch a Retry-After on a 429 like the one
      // confirmed live (2026-08-30): Render's own infra rate-limits repeated wake-up
      // requests to a sleeping service with a plain-text 429, independent of anything
      // this app does - see this class's doc comment for the fix that followed.
      this.logger.warn(
        `Backend health check reached ${this.targetUrl} but returned ` +
          `status=${response.status} headers=${JSON.stringify(response.headers)} ` +
          `body=${JSON.stringify(response.data)}`,
      );
    } catch (error) {
      // Logged at warn, not error: a cold backend timing out here is expected, routine
      // traffic, not an incident - but the exact failure (DNS, connection refused,
      // timeout, ...) is exactly what's needed to tell "still booting" apart from
      // "misconfigured target" without guessing.
      const detail = error instanceof Error ? error.message : String(error);
      this.logger.warn(
        `Backend health check to ${this.targetUrl} failed: ${detail}`,
      );
    }
    throw new ServiceUnavailableException({
      type: PROBLEM_TYPE_BASE + 'service-unavailable',
      title: 'Backend is still starting up',
    });
  }
}
