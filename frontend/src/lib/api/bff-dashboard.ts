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
