'use client';

import { useMutation } from '@tanstack/react-query';
import { apiFetch } from '@/lib/api/client';

/** Elevates the current access token via a fresh MFA code — required before a transfer
 * at or above the step-up threshold is accepted (security.StepUpRequiredException). */
export function useStepUp() {
  return useMutation({
    mutationFn: (code: string) => apiFetch('post', '/api/v1/auth/step-up', { body: { code } }),
  });
}
