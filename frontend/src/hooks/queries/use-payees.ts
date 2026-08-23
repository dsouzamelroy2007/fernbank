'use client';

import { useQuery } from '@tanstack/react-query';
import { apiFetch } from '@/lib/api/client';

export function usePayees() {
  return useQuery({
    queryKey: ['payees'],
    queryFn: ({ signal }) => apiFetch('get', '/api/v1/payees', { signal }),
  });
}
