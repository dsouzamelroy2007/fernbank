import { Injectable, Logger } from '@nestjs/common';
import { HttpService } from '@nestjs/axios';
import { firstValueFrom } from 'rxjs';
import type { AxiosError } from 'axios';
import { config } from '../config/configuration';
import { UpstreamHttpException } from '../common/upstream-http-exception';
import type { ProblemDetailBody } from '../common/problem-detail';
import { CORRELATION_ID_HEADER, newCorrelationId } from '../common/correlation';
import { decodeJwtExpiryMs } from '../common/jwt';

interface CacheEntry {
  accessToken: string;
  refreshToken: string;
  expiresAt: number;
}

interface TokenPair {
  accessToken: string;
  refreshToken: string;
}

const SWEEP_INTERVAL_MS = 5 * 60 * 1000;
const STALE_AFTER_MS = 10 * 60 * 1000;

/**
 * Caches one access token per session, refreshed from the backend only when
 * near-expiry — never on every request. The backend's POST /auth/refresh ALWAYS
 * rotates the refresh token and revokes the previous one; two concurrent calls for the
 * same session would trip its reuse-detection and revoke the whole family, logging the
 * user out. `refreshLocks` single-flights concurrent callers onto one in-flight
 * refresh, the same guard frontend/src/lib/auth/token-store.ts already used
 * client-side, just relocated here now that the browser never holds a token at all.
 */
@Injectable()
export class AccessTokenCacheService {
  private readonly logger = new Logger(AccessTokenCacheService.name);
  private readonly cache = new Map<string, CacheEntry>();
  private readonly refreshLocks = new Map<string, Promise<CacheEntry>>();

  constructor(private readonly http: HttpService) {
    setInterval(() => this.sweep(), SWEEP_INTERVAL_MS).unref();
  }

  async resolveAccessToken(
    sessionId: string,
    cookieRefreshToken: string,
  ): Promise<string> {
    const entry = this.cache.get(sessionId);
    if (
      entry &&
      entry.expiresAt - config.accessTokenRefreshSkewMs > Date.now()
    ) {
      return entry.accessToken;
    }
    const refreshed = await this.refreshWithLock(
      sessionId,
      entry?.refreshToken ?? cookieRefreshToken,
    );
    return refreshed.accessToken;
  }

  /** Forces a fresh refresh regardless of cache state — used after a downstream 401 despite a
   * cached token that looked valid (clock skew, out-of-band revocation). */
  async forceRefresh(
    sessionId: string,
    cookieRefreshToken: string,
  ): Promise<string> {
    const current =
      this.cache.get(sessionId)?.refreshToken ?? cookieRefreshToken;
    this.cache.delete(sessionId);
    const refreshed = await this.refreshWithLock(sessionId, current);
    return refreshed.accessToken;
  }

  /** Step-up: overwrite the cached access token with an elevated one, leaving the
   * refresh token untouched — never returned to the browser. */
  overwriteElevated(
    sessionId: string,
    elevatedAccessToken: string,
    expiresAt: number,
  ): void {
    const existing = this.cache.get(sessionId);
    if (!existing) {
      throw new Error(
        `overwriteElevated called with no existing cache entry for session ${sessionId}`,
      );
    }
    this.cache.set(sessionId, {
      ...existing,
      accessToken: elevatedAccessToken,
      expiresAt,
    });
  }

  /** Seeds the cache with the token pair a fresh login/register/mfa-verify already
   * returned, so the very next request for this session doesn't pay for an extra
   * refresh round trip. */
  seed(sessionId: string, accessToken: string, refreshToken: string): void {
    this.cache.set(sessionId, {
      accessToken,
      refreshToken,
      expiresAt: decodeJwtExpiryMs(accessToken) ?? Date.now() + 60_000,
    });
  }

  /** Used by SessionCookieSyncInterceptor to detect rotation since the request started. */
  getCurrentRefreshToken(sessionId: string): string | undefined {
    return this.cache.get(sessionId)?.refreshToken;
  }

  invalidate(sessionId: string): void {
    this.cache.delete(sessionId);
    this.refreshLocks.delete(sessionId);
  }

  private refreshWithLock(
    sessionId: string,
    refreshToken: string,
  ): Promise<CacheEntry> {
    const inFlight = this.refreshLocks.get(sessionId);
    if (inFlight) {
      return inFlight;
    }

    const promise = this.callRefresh(refreshToken)
      .then((pair) => {
        const entry: CacheEntry = {
          accessToken: pair.accessToken,
          refreshToken: pair.refreshToken,
          expiresAt: decodeJwtExpiryMs(pair.accessToken) ?? Date.now() + 60_000,
        };
        this.cache.set(sessionId, entry);
        return entry;
      })
      .finally(() => this.refreshLocks.delete(sessionId));

    this.refreshLocks.set(sessionId, promise);
    return promise;
  }

  private async callRefresh(refreshToken: string): Promise<TokenPair> {
    try {
      const response = await firstValueFrom(
        this.http.post<TokenPair>(
          `${config.backendInternalBaseUrl}/api/v1/auth/refresh`,
          { refreshToken },
          { headers: { [CORRELATION_ID_HEADER]: newCorrelationId() } },
        ),
      );
      return response.data;
    } catch (error) {
      const axiosError = error as AxiosError<ProblemDetailBody>;
      throw new UpstreamHttpException(
        axiosError.response?.status ?? 401,
        axiosError.response?.data,
      );
    }
  }

  private sweep(): void {
    const now = Date.now();
    let evicted = 0;
    for (const [sessionId, entry] of this.cache.entries()) {
      if (
        now - entry.expiresAt > STALE_AFTER_MS &&
        !this.refreshLocks.has(sessionId)
      ) {
        this.cache.delete(sessionId);
        evicted++;
      }
    }
    if (evicted > 0) {
      this.logger.debug(`Swept ${evicted} stale session cache entries`);
    }
  }
}
