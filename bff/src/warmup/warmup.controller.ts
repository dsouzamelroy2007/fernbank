import {
  Controller,
  Get,
  HttpCode,
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

/**
 * Unauthenticated readiness probe for the login page's "waking up" poll - lets the
 * frontend confirm the backend (and, via its own /actuator/health DB check, Neon) is
 * actually responsive before ever submitting real credentials to POST /auth/login.
 * Render's free tier sleeps this bff and the backend independently after ~15 min idle;
 * without this, the first real login attempt races a multi-minute cold start against
 * Vercel's shorter rewrite timeout, silently fails client-side, and each retry still
 * burns a LoginRateLimiter token server-side even though the user never sees a
 * successful response - the actual bug behind the 429s on a cold demo.
 */
@Controller('api/v1/warmup')
@SkipThrottle()
export class WarmupController {
  constructor(private readonly http: HttpService) {}

  @Get()
  @HttpCode(200)
  async check(@Req() req: Request): Promise<{ status: 'UP' }> {
    try {
      const response = await firstValueFrom(
        this.http.request<{ status?: string }>({
          method: 'GET',
          url: `${config.backendInternalBaseUrl}/actuator/health`,
          timeout: 10_000,
          validateStatus: () => true,
          headers: { [CORRELATION_ID_HEADER]: extractCorrelationId(req) },
        }),
      );
      if (response.status === 200 && response.data?.status === 'UP') {
        return { status: 'UP' };
      }
    } catch {
      // Unreachable, still waking, or timed out - fall through to the 503 below so the
      // frontend's poll loop just tries again shortly.
    }
    throw new ServiceUnavailableException({
      type: PROBLEM_TYPE_BASE + 'service-unavailable',
      title: 'Backend is still starting up',
    });
  }
}
