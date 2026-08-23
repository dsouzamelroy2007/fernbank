import { of } from 'rxjs';
import type { HttpService } from '@nestjs/axios';
import { AccessTokenCacheService } from './access-token-cache.service';

function jwtWithExpiry(expiresInSeconds: number): string {
  const header = Buffer.from(JSON.stringify({ alg: 'none' })).toString(
    'base64url',
  );
  const payload = Buffer.from(
    JSON.stringify({ exp: Math.floor(Date.now() / 1000) + expiresInSeconds }),
  ).toString('base64url');
  return `${header}.${payload}.signature`;
}

function makeHttpServiceMock(
  responder: () => { accessToken: string; refreshToken: string },
) {
  const post = jest.fn().mockImplementation(() => of({ data: responder() }));
  const http = { post } as unknown as HttpService;
  return { http, post };
}

describe('AccessTokenCacheService', () => {
  it('single-flights concurrent refreshes for the same session', async () => {
    let callCount = 0;
    const { http, post } = makeHttpServiceMock(() => {
      callCount++;
      return {
        accessToken: jwtWithExpiry(600),
        refreshToken: `refresh-${callCount}`,
      };
    });
    const cache = new AccessTokenCacheService(http);

    const results = await Promise.all([
      cache.resolveAccessToken('session-1', 'initial-refresh-token'),
      cache.resolveAccessToken('session-1', 'initial-refresh-token'),
      cache.resolveAccessToken('session-1', 'initial-refresh-token'),
      cache.resolveAccessToken('session-1', 'initial-refresh-token'),
    ]);

    expect(post).toHaveBeenCalledTimes(1);
    expect(new Set(results).size).toBe(1);
  });

  it('does not call the backend again while the cached token is still valid', async () => {
    const { http, post } = makeHttpServiceMock(() => ({
      accessToken: jwtWithExpiry(600),
      refreshToken: 'r1',
    }));
    const cache = new AccessTokenCacheService(http);

    await cache.resolveAccessToken('session-1', 'initial');
    await cache.resolveAccessToken('session-1', 'initial');
    await cache.resolveAccessToken('session-1', 'initial');

    expect(post).toHaveBeenCalledTimes(1);
  });

  it('refreshes again once the cached token is within the skew window of expiring', async () => {
    let call = 0;
    const { http, post } = makeHttpServiceMock(() => {
      call++;
      // First token already within the default 30s skew window, second is fresh.
      return {
        accessToken: jwtWithExpiry(call === 1 ? 5 : 600),
        refreshToken: `r${call}`,
      };
    });
    const cache = new AccessTokenCacheService(http);

    await cache.resolveAccessToken('session-1', 'initial');
    await cache.resolveAccessToken('session-1', 'initial');

    expect(post).toHaveBeenCalledTimes(2);
  });

  it('forceRefresh always calls the backend even with a fresh cached token', async () => {
    const { http, post } = makeHttpServiceMock(() => ({
      accessToken: jwtWithExpiry(600),
      refreshToken: 'r1',
    }));
    const cache = new AccessTokenCacheService(http);

    await cache.resolveAccessToken('session-1', 'initial');
    await cache.forceRefresh('session-1', 'initial');

    expect(post).toHaveBeenCalledTimes(2);
  });

  it('overwriteElevated replaces the access token without touching the refresh token', async () => {
    const { http, post } = makeHttpServiceMock(() => ({
      accessToken: jwtWithExpiry(600),
      refreshToken: 'r1',
    }));
    const cache = new AccessTokenCacheService(http);

    await cache.resolveAccessToken('session-1', 'initial');
    cache.overwriteElevated(
      'session-1',
      'elevated-token',
      Date.now() + 300_000,
    );

    expect(cache.getCurrentRefreshToken('session-1')).toBe('r1');
    // A subsequent resolve should return the elevated token without another backend call.
    const token = await cache.resolveAccessToken('session-1', 'initial');
    expect(token).toBe('elevated-token');
    expect(post).toHaveBeenCalledTimes(1);
  });

  it('overwriteElevated throws if no cache entry exists yet for the session', () => {
    const { http } = makeHttpServiceMock(() => ({
      accessToken: jwtWithExpiry(600),
      refreshToken: 'r1',
    }));
    const cache = new AccessTokenCacheService(http);
    expect(() =>
      cache.overwriteElevated('never-seen', 'x', Date.now() + 1000),
    ).toThrow();
  });

  it('seed populates the cache directly without a backend call', async () => {
    const { http, post } = makeHttpServiceMock(() => ({
      accessToken: jwtWithExpiry(600),
      refreshToken: 'should-not-be-used',
    }));
    const cache = new AccessTokenCacheService(http);

    cache.seed('session-1', jwtWithExpiry(600), 'seeded-refresh-token');
    const token = await cache.resolveAccessToken('session-1', 'irrelevant');

    expect(post).not.toHaveBeenCalled();
    expect(cache.getCurrentRefreshToken('session-1')).toBe(
      'seeded-refresh-token',
    );
    expect(token).toBeDefined();
  });

  it('invalidate clears both the cache entry and any lock', async () => {
    const { http, post } = makeHttpServiceMock(() => ({
      accessToken: jwtWithExpiry(600),
      refreshToken: 'r1',
    }));
    const cache = new AccessTokenCacheService(http);

    await cache.resolveAccessToken('session-1', 'initial');
    cache.invalidate('session-1');
    expect(cache.getCurrentRefreshToken('session-1')).toBeUndefined();

    await cache.resolveAccessToken('session-1', 'initial-again');
    expect(post).toHaveBeenCalledTimes(2);
  });
});
