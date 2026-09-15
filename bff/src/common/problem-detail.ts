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
