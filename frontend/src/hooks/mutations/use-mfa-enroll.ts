'use client';

import { useMutation } from '@tanstack/react-query';
import { apiFetch } from '@/lib/api/client';

export function useMfaEnroll() {
  return useMutation({
    mutationFn: () => apiFetch('post', '/api/v1/auth/mfa/enroll'),
  });
}

export function useMfaEnrollConfirm() {
  return useMutation({
    mutationFn: (code: string) =>
      apiFetch('post', '/api/v1/auth/mfa/enroll/confirm', { body: { code } }),
  });
}
