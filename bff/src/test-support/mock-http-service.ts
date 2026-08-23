import { of, throwError } from 'rxjs';

interface MockResult {
  status: number;
  body: unknown;
}

type Handler = (config: {
  url: string;
  method?: string;
  data?: unknown;
  responseType?: string;
}) => MockResult;

function toAxiosLike(config: { responseType?: string }, result: MockResult) {
  if (config.responseType === 'arraybuffer') {
    const buf = Buffer.from(JSON.stringify(result.body ?? {}), 'utf8');
    return {
      data: buf.buffer.slice(buf.byteOffset, buf.byteOffset + buf.byteLength),
      status: result.status,
      headers: { 'content-type': 'application/json' },
    };
  }
  return { data: result.body, status: result.status, headers: {} };
}

/**
 * Stands in for @nestjs/axios's HttpService in tests that exercise real controller +
 * guard + interceptor wiring via a Nest TestingModule, without making real network
 * calls. `.request()` (used by AuthBackendService and BackendClientService, both of
 * which set validateStatus: () => true) always resolves — the caller's own status
 * check does the branching. `.post()` (used only by AccessTokenCacheService's raw
 * refresh call, which does NOT override validateStatus) throws for non-2xx, matching
 * axios's real default behavior.
 */
export function createMockHttpService(handle: Handler) {
  return {
    request: jest
      .fn()
      .mockImplementation((config: Parameters<Handler>[0]) =>
        of(toAxiosLike(config, handle(config))),
      ),
    post: jest
      .fn()
      .mockImplementation(
        (url: string, data: unknown, config?: Record<string, unknown>) => {
          const fullConfig = {
            ...config,
            url,
            method: 'POST',
            data,
          } as Parameters<Handler>[0];
          const result = handle(fullConfig);
          if (result.status >= 400) {
            const error = new Error('mock http error') as Error & {
              response?: unknown;
            };
            error.response = { status: result.status, data: result.body };
            return throwError(() => error);
          }
          return of(toAxiosLike(fullConfig, result));
        },
      ),
  };
}

export function fakeJwt(expiresInSeconds = 600): string {
  const header = Buffer.from(JSON.stringify({ alg: 'none' })).toString(
    'base64url',
  );
  const payload = Buffer.from(
    JSON.stringify({ exp: Math.floor(Date.now() / 1000) + expiresInSeconds }),
  ).toString('base64url');
  return `${header}.${payload}.signature`;
}

export function extractCookieValue(
  setCookieHeaders: string[] | undefined,
  name: string,
): string {
  const line = (setCookieHeaders ?? []).find((c) => c.startsWith(`${name}=`));
  if (!line) {
    throw new Error(`Cookie ${name} not found in Set-Cookie headers`);
  }
  return line.split(';')[0].split('=')[1];
}
