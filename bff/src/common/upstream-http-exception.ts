import { HttpException } from '@nestjs/common';
import type { ProblemDetailBody } from './problem-detail';

/** Wraps a non-2xx response already received from the backend, so ProblemDetailFilter
 * forwards its ProblemDetail body verbatim instead of reshaping it. */
export class UpstreamHttpException extends HttpException {
  constructor(status: number, body: ProblemDetailBody | undefined) {
    super({ status, ...(body ?? { title: 'Upstream error' }) }, status);
  }
}
