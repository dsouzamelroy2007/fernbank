'use client';

import { useInfiniteQuery } from '@tanstack/react-query';
import { apiFetch } from '@/lib/api/client';

export function useLoginHistory() {
  return useInfiniteQuery({
    queryKey: ['login-history'],
    queryFn: ({ pageParam, signal }) =>
      apiFetch('get', '/api/v1/me/login-history', {
        params: { query: { cursor: pageParam, pageSize: 20 } },
        signal,
      }),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (lastPage) => (lastPage.hasNext ? lastPage.nextCursor : undefined),
  });
}
