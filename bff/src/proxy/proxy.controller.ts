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

const FORWARDED_HEADERS = ['idempotency-key'];

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
