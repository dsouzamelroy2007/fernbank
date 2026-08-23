'use client';

import { useQuery } from '@tanstack/react-query';
import { apiFetch } from '@/lib/api/client';

export function useSessions() {
  return useQuery({
    queryKey: ['sessions'],
    queryFn: ({ signal }) => apiFetch('get', '/api/v1/me/sessions', { signal }),
  });
}
