'use client';

import { useQuery } from '@tanstack/react-query';
import { apiFetch } from '@/lib/api/client';

export function useAccounts() {
  return useQuery({
    queryKey: ['accounts'],
    queryFn: ({ signal }) => apiFetch('get', '/api/v1/accounts', { signal }),
  });
}

export function useAccount(accountId: string) {
  return useQuery({
    queryKey: ['accounts', accountId],
    queryFn: ({ signal }) =>
      apiFetch('get', '/api/v1/accounts/{accountId}', { params: { path: { accountId } }, signal }),
    enabled: !!accountId,
  });
}
