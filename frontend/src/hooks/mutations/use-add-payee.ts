'use client';

import { useMutation, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from '@/lib/api/client';
import type { PayeeInput } from '@/lib/validation/payee.schemas';

export function useAddPayee() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payee: PayeeInput) => apiFetch('post', '/api/v1/payees', { body: payee }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['payees'] }),
  });
}
