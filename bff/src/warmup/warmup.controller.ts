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

/** Confirmed live (2026-09-11): a direct browser request to the backend's public URL
 * woke it normally with no 429, while the real Vercel -> bff -> backend path (through
 * this exact call) failed the same way it always had. Axios's default User-Agent
 * (`axios/x.x.x`) is a well-known non-browser signature; Cloudflare sits in front of
 * Render (see the `server: cloudflare` header on every captured 429) and commonly
 * applies stricter bot-heuristics to traffic that doesn't look like a real browser.
 * Presenting as one is a real, testable fix for a false-positive block on our own
 * server calling our own backend - not evasion of anything adversarial. */
const BROWSER_LIKE_HEADERS = {
  'User-Agent':
    'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 ' +
    '(KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36',
  Accept: 'application/json, text/plain, */*',
};

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
 * control. Slowing the frontend's poll interval down (see use-backend-warmup.ts), and
 * later dropping the auto-retry loop entirely for a single attempt + manual retry,
 * didn't clear it either - the identical 429 persisted across days and multiple
 * request-pattern changes, including single requests minutes to a day apart, ruling
 * out request frequency as the cause. Render support confirmed the 429 is decided at
 * their edge before the request reaches the app or logs a resume event. Switching
 * Render's own healthCheckPath off a DB-dependent endpoint (render.yaml) didn't clear
 * it either.
 *
 * Confirmed live (2026-09-11): a direct external (browser) request to the backend's
 * public URL woke it normally with no 429, while the real Vercel -> bff -> backend path
 * failed the same way it always had, in the same test session - strong evidence the
 * block is specific to requests that look like they come from this bff, not the URL
 * being blocked for everyone. Added BROWSER_LIKE_HEADERS as the next concrete thing to
 * test, on the theory that axios's default User-Agent reads as an obvious non-browser
 * script to Cloudflare's bot-heuristics (Render sits behind Cloudflare - see the
 * `server: cloudflare` header on every captured 429).
 */
@Controller('api/v1/warmup')
@SkipThrottle()
export class WarmupController {
  private readonly logger = new Logger(WarmupController.name);
  private readonly targetUrl = `${config.backendInternalBaseUrl}/actuator/health`;
  // Process-lifetime counter, not persisted - purely so consecutive log lines are easy
  // to eyeball for real interval-between-attempts, since the previous logging only
  // recorded outcomes, not attempts, making the actual call frequency hard to verify
  // independently of what any party (this app's own code, or Render support) claims it
  // is. Confirmed live (2026-09-11): Render support stated the bff calls this every
  // 25s - the deployed code at that point had already dropped the auto-retry interval
  // entirely (single attempt + manual/refocus retry only), so this line exists to get
  // real, independently-checkable evidence rather than trusting either side's claim.
  private attemptCount = 0;

  constructor(private readonly http: HttpService) {}

  @Get()
  @HttpCode(200)
  async check(@Req() req: Request): Promise<{ status: 'UP' }> {
    this.attemptCount += 1;
    this.logger.log(
      `Backend health check attempt #${this.attemptCount} -> ${this.targetUrl}`,
    );
    try {
      const response = await firstValueFrom(
        this.http.request<{ status?: string }>({
          method: 'GET',
          url: this.targetUrl,
          timeout: BACKEND_HEALTH_TIMEOUT_MS,
          validateStatus: () => true,
          headers: {
            ...BROWSER_LIKE_HEADERS,
            [CORRELATION_ID_HEADER]: extractCorrelationId(req),
          },
        }),
      );
      if (response.status === 200 && response.data?.status === 'UP') {
        this.logger.log(
          `Backend health check attempt #${this.attemptCount} succeeded - UP`,
        );
        return { status: 'UP' };
      }
      // Reached the backend but got something other than a healthy 200 - worth
      // knowing exactly what, since "still starting up" isn't the only way to land here.
      // Headers included specifically to catch a Retry-After on a 429 like the one
      // confirmed live (2026-08-30): Render's own infra rate-limits repeated wake-up
      // requests to a sleeping service with a plain-text 429, independent of anything
      // this app does - see this class's doc comment for the fix that followed.
      this.logger.warn(
        `Backend health check attempt #${this.attemptCount} reached ${this.targetUrl} but ` +
          `returned status=${response.status} headers=${JSON.stringify(response.headers)} ` +
          `body=${JSON.stringify(response.data)}`,
      );
    } catch (error) {
      // Logged at warn, not error: a cold backend timing out here is expected, routine
      // traffic, not an incident - but the exact failure (DNS, connection refused,
      // timeout, ...) is exactly what's needed to tell "still booting" apart from
      // "misconfigured target" without guessing.
      const detail = error instanceof Error ? error.message : String(error);
      this.logger.warn(
        `Backend health check attempt #${this.attemptCount} to ${this.targetUrl} failed: ${detail}`,
      );
    }
    throw new ServiceUnavailableException({
      type: PROBLEM_TYPE_BASE + 'service-unavailable',
      title: 'Backend is still starting up',
    });
  }
}
