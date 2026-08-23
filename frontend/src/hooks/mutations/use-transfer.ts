'use client';

import { useMutation } from '@tanstack/react-query';
import { apiFetch } from '@/lib/api/client';
import type { TransferInput } from '@/lib/validation/transfer.schemas';

export function useTransfer() {
  return useMutation({
    mutationFn: (body: TransferInput) => apiFetch('post', '/api/v1/transfers', { body }),
  });
}
