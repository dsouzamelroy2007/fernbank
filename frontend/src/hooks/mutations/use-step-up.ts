'use client';

import { useMutation } from '@tanstack/react-query';
import { apiFetch } from '@/lib/api/client';

export function useStepUp() {
  return useMutation({
    mutationFn: (code: string) => apiFetch('post', '/api/v1/auth/step-up', { body: { code } }),
  });
}
