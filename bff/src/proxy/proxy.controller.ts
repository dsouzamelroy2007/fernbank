import {
  All,
  Controller,
  NotFoundException,
  Param,
  Query,
  Req,
  Res,
} from '@nestjs/common';
import type { Request, Response } from 'express';
import type { Method } from 'axios';
import { BackendClientService } from '../backend-client/backend-client.service';
import { requireSession } from '../session/require-session';
import { extractCorrelationId } from '../common/correlation';
import { ALLOWED_PROXY_RESOURCES } from './proxy-allowlist';
import { PROBLEM_TYPE_BASE } from '../common/problem-detail';

const RESOURCES = [...ALLOWED_PROXY_RESOURCES];

/** Headers the browser sets that must ride through to the backend unchanged. */
const FORWARDED_HEADERS = ['idempotency-key'];

/**
 * Routes are the allowlist: registered as literal paths derived from
 * ALLOWED_PROXY_RESOURCES (`accounts`, `accounts/*path`, `payees`, ...), never a bare
 * `:resource` wildcard param — that would structurally risk shadowing or being shadowed
 * by AuthController's `api/v1/auth/**` routes depending on registration order. This way
 * `/api/v1/admin/**` (or anything else) simply has no matching route at all; Nest's own
 * 404 handles it before this controller's code ever runs. The runtime check in
 * `forward()` is defense-in-depth against a future refactor accidentally widening the
 * route list, not the primary guard.
 */
@Controller('api/v1')
export class ProxyController {
  constructor(private readonly backendClient: BackendClientService) {}

  @All(RESOURCES)
  proxyResource(
    @Req() req: Request,
    @Query() query: Record<string, unknown>,
    @Res() res: Response,
  ): Promise<void> {
    return this.forward(resourceFromPath(req.path), '', query, req, res);
  }

  @All(RESOURCES.map((resource) => `${resource}/*path`))
  proxySubPath(
    @Param('path') path: string | string[],
    @Req() req: Request,
    @Query() query: Record<string, unknown>,
    @Res() res: Response,
  ): Promise<void> {
    const suffix = Array.isArray(path) ? path.join('/') : path;
    return this.forward(
      resourceFromPath(req.path),
      `/${suffix}`,
      query,
      req,
      res,
    );
  }

  private async forward(
    resource: string,
    suffix: string,
    query: Record<string, unknown>,
    req: Request,
    res: Response,
  ): Promise<void> {
    if (!ALLOWED_PROXY_RESOURCES.has(resource)) {
      throw new NotFoundException({
        type: PROBLEM_TYPE_BASE + 'not-found',
        title: 'Not found',
      });
    }
    const session = requireSession(req);

    const extraHeaders: Record<string, string> = {};
    for (const headerName of FORWARDED_HEADERS) {
      const value = req.headers[headerName];
      if (typeof value === 'string') {
        extraHeaders[headerName] = value;
      }
    }

    const hasBody =
      req.method !== 'GET' &&
      req.method !== 'HEAD' &&
      req.body !== undefined &&
      Object.keys(req.body as object).length > 0;

    const upstream = await this.backendClient.requestRaw({
      session,
      method: req.method as Method,
      path: `/api/v1/${resource}${suffix}`,
      query,
      body: hasBody ? req.body : undefined,
      extraHeaders,
      correlationId: extractCorrelationId(req),
    });

    res.status(upstream.status);
    for (const header of ['content-type', 'content-disposition', 'etag']) {
      const value = upstream.headers[header];
      if (value) {
        res.setHeader(header, value);
      }
    }
    res.send(upstream.data);
  }
}

function resourceFromPath(path: string): string {
  return path.replace(/^\/api\/v1\//, '').split('/')[0];
}
