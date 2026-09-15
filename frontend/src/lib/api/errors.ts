export interface ProblemDetailBody {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  correlationId?: string;
  errors?: string[];
}

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
