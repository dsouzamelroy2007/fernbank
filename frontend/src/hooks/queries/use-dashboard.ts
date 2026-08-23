'use client';

import { useQuery } from '@tanstack/react-query';
import { getDashboard } from '@/lib/api/bff-dashboard';

export function useDashboard() {
  return useQuery({
    queryKey: ['bff-dashboard'],
    queryFn: ({ signal }) => getDashboard(signal),
  });
}
