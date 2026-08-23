import { Injectable } from '@nestjs/common';
import { HttpService } from '@nestjs/axios';
import { firstValueFrom } from 'rxjs';
import type { AxiosRequestConfig, AxiosResponse, Method } from 'axios';
import { config } from '../config/configuration';
import { AccessTokenCacheService } from '../session/access-token-cache.service';
import type { SessionPayload } from '../session/session-crypto.service';
import { UpstreamHttpException } from '../common/upstream-http-exception';
import {
  PROBLEM_TYPE_BASE,
  type ProblemDetailBody,
} from '../common/problem-detail';
import { CORRELATION_ID_HEADER, newCorrelationId } from '../common/correlation';

export interface RawBackendResponse {
  status: number;
  headers: Record<string, string>;
  data: Buffer;
}

interface AuthenticatedRequestOptions {
  session: SessionPayload;
  method: Method;
  path: string;
  query?: Record<string, unknown>;
  body?: unknown;
  extraHeaders?: Record<string, string>;
  /** Propagates the browser's X-Correlation-Id through to the backend so one request
   * traces as one id across both processes. Falls back to minting one when the caller
   * has no single originating browser request (e.g. the notification poller). */
  correlationId?: string;
}

/**
 * The only thing in this process that calls Spring Boot on behalf of an authenticated
 * session. Binary-safe (arraybuffer throughout — CSV/PDF statement exports pass through
 * exactly like JSON, no format-specific branching) and owns the single-retry-on-401
 * pattern: resolve a cached token, call; if the call itself 401s despite a token that
 * looked valid (clock skew, out-of-band revocation), force a fresh refresh and retry
 * exactly once before giving up.
 */
@Injectable()
export class BackendClientService {
  constructor(
    private readonly http: HttpService,
    private readonly tokenCache: AccessTokenCacheService,
  ) {}

  async requestRaw(
    options: AuthenticatedRequestOptions,
  ): Promise<RawBackendResponse> {
    const accessToken = await this.tokenCache.resolveAccessToken(
      options.session.sessionId,
      options.session.refreshToken,
    );
    try {
      return await this.doRequest(options, accessToken);
    } catch (error) {
      if (error instanceof UpstreamHttpException && error.getStatus() === 401) {
        const refreshedToken = await this.tokenCache.forceRefresh(
          options.session.sessionId,
          options.session.refreshToken,
        );
        return this.doRequest(options, refreshedToken);
      }
      throw error;
    }
  }

  /** JSON-decoded variant for callers that need to inspect/reshape the response
   * (dashboard aggregation, mfa/step-up) rather than pass it straight through. */
  async requestJson<T>(options: AuthenticatedRequestOptions): Promise<T> {
    const raw = await this.requestRaw(options);
    return raw.data.length
      ? (JSON.parse(raw.data.toString('utf8')) as T)
      : (undefined as T);
  }

  private async doRequest(
    options: AuthenticatedRequestOptions,
    accessToken: string,
  ): Promise<RawBackendResponse> {
    const requestConfig: AxiosRequestConfig = {
      method: options.method,
      url: `${config.backendInternalBaseUrl}${options.path}`,
      params: options.query,
      data: options.body,
      responseType: 'arraybuffer',
      validateStatus: () => true,
      headers: {
        Authorization: `Bearer ${accessToken}`,
        [CORRELATION_ID_HEADER]: options.correlationId ?? newCorrelationId(),
        ...(options.body !== undefined
          ? { 'Content-Type': 'application/json' }
          : {}),
        ...options.extraHeaders,
      },
    };

    let response: AxiosResponse<ArrayBuffer>;
    try {
      response = await firstValueFrom(
        this.http.request<ArrayBuffer>(requestConfig),
      );
    } catch {
      throw new UpstreamHttpException(502, {
        type: PROBLEM_TYPE_BASE + 'bad-gateway',
        title: 'Could not reach the backend',
      });
    }

    const data = Buffer.from(response.data ?? new ArrayBuffer(0));
    if (response.status >= 400) {
      throw new UpstreamHttpException(
        response.status,
        safeParseJson<ProblemDetailBody>(data),
      );
    }

    return {
      status: response.status,
      headers: normalizeHeaders(response.headers),
      data,
    };
  }
}

function safeParseJson<T>(buffer: Buffer): T | undefined {
  try {
    return buffer.length
      ? (JSON.parse(buffer.toString('utf8')) as T)
      : undefined;
  } catch {
    return undefined;
  }
}

function normalizeHeaders(headers: unknown): Record<string, string> {
  const result: Record<string, string> = {};
  if (headers && typeof headers === 'object') {
    for (const [key, value] of Object.entries(
      headers as Record<string, unknown>,
    )) {
      if (typeof value === 'string') result[key] = value;
    }
  }
  return result;
}
