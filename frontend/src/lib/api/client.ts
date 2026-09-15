import { resolveBffBase } from '@/lib/env';
import { CORRELATION_ID_HEADER, newCorrelationId } from '@/lib/api/correlation';
import { CSRF_HEADER_NAME, readCsrfToken } from '@/lib/auth/csrf';
import { ApiError } from '@/lib/api/errors';
import type { paths } from '@/lib/api/schema';

type HttpMethod = 'get' | 'post' | 'put' | 'patch' | 'delete';

type Operation<P extends keyof paths, M extends HttpMethod> =
  paths[P] extends Record<M, infer Op> ? Op : never;

type RequestBodyOf<Op> = Op extends {
  requestBody: { content: { 'application/json': infer B } };
}
  ? B
  : never;

type PathParamsOf<Op> = Op extends { parameters: { path: infer P } } ? P : undefined;

type QueryParamsOf<Op> = Op extends { parameters: { query?: infer Q } } ? Q : undefined;

type SuccessBodyOf<Op> = Op extends { responses: infer R }
  ? {
      [K in Extract<keyof R, 200 | 201 | 204>]: R[K] extends { content: infer C }
        ? C extends Record<string, unknown>
          ? C[keyof C]
          : undefined
        : undefined;
    }[Extract<keyof R, 200 | 201 | 204>]
  : undefined;

interface ApiFetchOptions<Op> {
  params?: {
    path?: PathParamsOf<Op>;
    query?: QueryParamsOf<Op>;
  };
  body?: RequestBodyOf<Op>;
  signal?: AbortSignal;
}

function buildUrl(
  path: string,
  pathParams: Record<string, string | number> | undefined,
  queryParams: Record<string, string | number | boolean | undefined> | undefined,
): URL {
  let resolvedPath = path;
  if (pathParams) {
    for (const [key, value] of Object.entries(pathParams)) {
      resolvedPath = resolvedPath.replace(`{${key}}`, encodeURIComponent(String(value)));
    }
  }
  const url = new URL(resolvedPath, resolveBffBase());
  if (queryParams) {
    for (const [key, value] of Object.entries(queryParams)) {
      if (value !== undefined) url.searchParams.set(key, String(value));
    }
  }
  return url;
}

export async function apiFetch<P extends keyof paths, M extends HttpMethod>(
  method: M,
  path: P,
  options: ApiFetchOptions<Operation<P, M>> = {},
): Promise<SuccessBodyOf<Operation<P, M>>> {
  const url = buildUrl(
    path as string,
    options.params?.path as Record<string, string | number> | undefined,
    options.params?.query as Record<string, string | number | boolean | undefined> | undefined,
  );

  const headers = new Headers({ [CORRELATION_ID_HEADER]: newCorrelationId() });
  if (method !== 'get') {
    headers.set('Idempotency-Key', crypto.randomUUID());
    const csrfToken = readCsrfToken();
    if (csrfToken) headers.set(CSRF_HEADER_NAME, csrfToken);
  }
  if (options.body !== undefined) headers.set('Content-Type', 'application/json');

  const response = await fetch(url, {
    method: method.toUpperCase(),
    headers,
    credentials: 'include',
    body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
    signal: options.signal,
  });

  if (!response.ok) {
    throw await ApiError.fromResponse(response);
  }
  if (response.status === 204) {
    return undefined as SuccessBodyOf<Operation<P, M>>;
  }
  return (await response.json()) as SuccessBodyOf<Operation<P, M>>;
}
