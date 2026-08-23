'use client';

import { useMutation, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from '@/lib/api/client';

export function useDeletePayee() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payeeId: string) =>
      apiFetch('delete', '/api/v1/payees/{payeeId}', { params: { path: { payeeId } } }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['payees'] }),
  });
}
