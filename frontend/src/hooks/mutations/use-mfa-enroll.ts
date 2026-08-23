'use client';

import { useMutation } from '@tanstack/react-query';
import { apiFetch } from '@/lib/api/client';

/** The BFF mirrors these paths and resolves the session's access token itself (Phase 8)
 * — no bearer header to forward manually anymore, just ordinary apiFetch calls. */
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
