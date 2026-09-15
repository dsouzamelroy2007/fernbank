import { HttpException } from '@nestjs/common';
import type { ProblemDetailBody } from './problem-detail';

export class UpstreamHttpException extends HttpException {
  constructor(status: number, body: ProblemDetailBody | undefined) {
    super({ status, ...(body ?? { title: 'Upstream error' }) }, status);
  }
}
