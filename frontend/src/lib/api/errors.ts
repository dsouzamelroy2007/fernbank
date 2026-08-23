/**
 * RFC 9457 Problem Details, as produced by every error response in the fernbank API
 * (error.ApiExceptionHandler on the backend). The OpenAPI-generated ProblemDetail schema
 * types these fields as `unknown` — springdoc doesn't declare a JSON `type` for them — so
 * this interface is hand-written from the backend's actual, verified response shape.
 */
export interface ProblemDetailBody {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  correlationId?: string;
  /** Present only on 400 validation failures; flat "field: message" strings, not objects. */
  errors?: string[];
}

/** Known ProblemDetail `type` slugs worth switching on, per error.ApiExceptionHandler. */
export const PROBLEM_TYPE = {
  emailAlreadyRegistered: 'email-already-registered',
  unauthorized: 'unauthorized',
  rateLimitExceeded: 'rate-limit-exceeded',
  stepUpRequired: 'step-up-required',
  validationFailed: 'validation-failed',
  missingHeader: 'missing-header',
  internalError: 'internal-error',
} as const;

export class ApiError extends Error {
  readonly status: number;
  readonly type?: string;
  readonly title?: string;
  readonly detail?: string;
  readonly correlationId?: string;
  readonly fieldErrors: string[];

  constructor(status: number, body: ProblemDetailBody | undefined) {
    super(body?.detail ?? body?.title ?? `Request failed with status ${status}`);
    this.name = 'ApiError';
    this.status = status;
    this.type = body?.type;
    this.title = body?.title;
    this.detail = body?.detail;
    this.correlationId = body?.correlationId;
    this.fieldErrors = body?.errors ?? [];
  }

  static async fromResponse(response: Response): Promise<ApiError> {
    try {
      const body = (await response.json()) as ProblemDetailBody;
      return new ApiError(response.status, body);
    } catch {
      return new ApiError(response.status, undefined);
    }
  }
}
