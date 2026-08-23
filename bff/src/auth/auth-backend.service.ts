import { Injectable } from '@nestjs/common';
import { HttpService } from '@nestjs/axios';
import { firstValueFrom } from 'rxjs';
import type { AxiosRequestConfig, AxiosResponse } from 'axios';
import { config } from '../config/configuration';
import { UpstreamHttpException } from '../common/upstream-http-exception';
import {
  PROBLEM_TYPE_BASE,
  type ProblemDetailBody,
} from '../common/problem-detail';
import { CORRELATION_ID_HEADER, newCorrelationId } from '../common/correlation';

/** Raw, token-less calls to the backend's auth endpoints (register/login/mfa-verify/
 * logout) — these precede having a session, or only need a refreshToken body param,
 * not a cached Bearer access token. Mirrors what
 * frontend/src/lib/api/server-fetch.ts used to do from Next.js Route Handlers. */
@Injectable()
export class AuthBackendService {
  constructor(private readonly http: HttpService) {}

  async post<T>(
    path: string,
    body: unknown,
    correlationId?: string,
    clientIp?: string,
  ): Promise<T> {
    const headers: Record<string, string> = {
      'Content-Type': 'application/json',
      [CORRELATION_ID_HEADER]: correlationId ?? newCorrelationId(),
    };
    // Only sent when both sides are configured to understand it - see
    // config.internalServiceKey's comment and AuthController.resolveClientIp on the
    // backend for why an unpaired X-Forwarded-For is never trusted on its own.
    if (clientIp && config.internalServiceKey) {
      headers['X-Forwarded-For'] = clientIp;
      headers['X-Internal-Service-Key'] = config.internalServiceKey;
    }
    const requestConfig: AxiosRequestConfig = {
      method: 'POST',
      url: `${config.backendInternalBaseUrl}${path}`,
      data: body,
      validateStatus: () => true,
      headers,
    };

    let response: AxiosResponse<T | ProblemDetailBody>;
    try {
      response = await firstValueFrom(
        this.http.request<T | ProblemDetailBody>(requestConfig),
      );
    } catch {
      throw new UpstreamHttpException(502, {
        type: PROBLEM_TYPE_BASE + 'bad-gateway',
        title: 'Could not reach the backend',
      });
    }

    if (response.status >= 400) {
      throw new UpstreamHttpException(
        response.status,
        response.data as ProblemDetailBody,
      );
    }
    return response.data as T;
  }
}
