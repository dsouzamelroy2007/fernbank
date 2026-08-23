import {
  ArgumentsHost,
  Catch,
  ExceptionFilter,
  HttpException,
  HttpStatus,
  Logger,
} from '@nestjs/common';
import type { Request, Response } from 'express';
import { CORRELATION_ID_HEADER } from './correlation';
import { PROBLEM_TYPE_BASE, type ProblemDetailBody } from './problem-detail';

/**
 * Global RFC 9457 Problem Details mapping, mirroring the backend's own
 * error.ApiExceptionHandler shape exactly. Backend errors forwarded via
 * UpstreamHttpException pass through verbatim; BFF-native exceptions (CSRF, dead
 * session) are expected to already carry a ProblemDetailBody-shaped response object.
 * Unmapped exceptions are logged server-side and returned as an opaque 500 — never a
 * stack trace to the client.
 */
@Catch()
export class ProblemDetailFilter implements ExceptionFilter {
  private readonly logger = new Logger(ProblemDetailFilter.name);

  catch(exception: unknown, host: ArgumentsHost): void {
    const ctx = host.switchToHttp();
    const response = ctx.getResponse<Response>();
    const request = ctx.getRequest<Request>();
    const correlationId = request.headers[
      CORRELATION_ID_HEADER.toLowerCase()
    ] as string | undefined;

    let status: number = HttpStatus.INTERNAL_SERVER_ERROR;
    let body: ProblemDetailBody;

    if (exception instanceof HttpException) {
      status = exception.getStatus();
      const exceptionResponse = exception.getResponse();
      if (typeof exceptionResponse === 'object' && exceptionResponse !== null) {
        body = { status, ...(exceptionResponse as Record<string, unknown>) };
      } else {
        body = {
          type: PROBLEM_TYPE_BASE + 'bff-error',
          title: String(exceptionResponse),
          status,
        };
      }
    } else {
      this.logger.error(
        'Unhandled exception',
        exception instanceof Error ? exception.stack : String(exception),
      );
      body = {
        type: PROBLEM_TYPE_BASE + 'internal-error',
        title: 'An unexpected error occurred',
        status,
      };
    }

    body.correlationId = correlationId;
    response.status(status).json(body);
  }
}
