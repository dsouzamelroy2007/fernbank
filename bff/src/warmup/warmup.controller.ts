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

const BACKEND_HEALTH_TIMEOUT_MS = 170_000;

const BROWSER_LIKE_HEADERS = {
  'User-Agent':
    'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 ' +
    '(KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36',
  Accept: 'application/json, text/plain, */*',
};

@Controller('api/v1/warmup')
@SkipThrottle()
export class WarmupController {
  private readonly logger = new Logger(WarmupController.name);
  private readonly targetUrl = `${config.backendInternalBaseUrl}/actuator/health`;
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
      this.logger.warn(
        `Backend health check attempt #${this.attemptCount} reached ${this.targetUrl} but ` +
          `returned status=${response.status} headers=${JSON.stringify(response.headers)} ` +
          `body=${JSON.stringify(response.data)}`,
      );
    } catch (error) {
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
