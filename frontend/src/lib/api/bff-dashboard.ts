import { resolveBffBase } from '@/lib/env';
import { CORRELATION_ID_HEADER, newCorrelationId } from '@/lib/api/correlation';
import { ApiError } from '@/lib/api/errors';
import type { components } from '@/lib/api/schema';

type MoneyDto = components['schemas']['MoneyDto'];

export interface DashboardStatementEntry {
  id?: string;
  transactionId?: string;
  createdAt?: string;
  amount?: MoneyDto;
  description?: string;
}

export interface DashboardAccount {
  id?: string;
  accountNumber?: string;
  type?: string;
  status?: string;
  balance?: MoneyDto;
  createdAt?: string;
  recentStatement: {
    entries: DashboardStatementEntry[];
    /** true if this account's statement fetch failed on the BFF side - the rest of the
     * dashboard still loads rather than the whole page failing over one account's blip. */
    degraded: boolean;
  };
}

export interface DashboardResponse {
  me: {
    userId?: string;
    customerId?: string;
    email?: string;
    fullName?: string;
  };
  accounts: DashboardAccount[];
}

/**
 * GET /bff/dashboard has no backend OpenAPI equivalent - it's a BFF-only aggregate
 * endpoint (Phase 8) replacing the old /me + /accounts + N×/accounts/{id}/statement
 * waterfall with one round trip - so its response shape is hand-written here rather
 * than generated, same precedent as lib/api/errors.ts's hand-written ProblemDetailBody.
 */
export async function getDashboard(signal?: AbortSignal): Promise<DashboardResponse> {
  const response = await fetch(new URL('/bff/dashboard', resolveBffBase()), {
    credentials: 'include',
    headers: { [CORRELATION_ID_HEADER]: newCorrelationId() },
    signal,
  });
  if (!response.ok) {
    throw await ApiError.fromResponse(response);
  }
  return (await response.json()) as DashboardResponse;
}
