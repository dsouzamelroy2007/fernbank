/** Same shape as frontend/src/lib/api/errors.ts's ProblemDetailBody and the backend's
 * error.ApiExceptionHandler output (RFC 9457) — the BFF passes backend errors through
 * verbatim and shapes its own native errors (CSRF, dead session) identically. */
export interface ProblemDetailBody {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  correlationId?: string;
  errors?: string[];
}

export const PROBLEM_TYPE_BASE = 'https://fernbank.dev/problems/';
