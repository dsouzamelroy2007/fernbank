'use client';

import { useInfiniteQuery } from '@tanstack/react-query';
import { apiFetch } from '@/lib/api/client';

export interface StatementFilters {
  from?: string;
  to?: string;
}

export function useStatement(accountId: string, filters: StatementFilters = {}) {
  return useInfiniteQuery({
    queryKey: ['accounts', accountId, 'statement', filters],
    queryFn: ({ pageParam, signal }) =>
      apiFetch('get', '/api/v1/accounts/{accountId}/statement', {
        params: {
          path: { accountId },
          query: { from: filters.from, to: filters.to, cursor: pageParam, pageSize: 50 },
        },
        signal,
      }),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (lastPage) => (lastPage.hasNext ? lastPage.nextCursor : undefined),
    enabled: !!accountId,
  });
}
