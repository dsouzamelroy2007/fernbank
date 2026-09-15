import { resolveBffBase, BACKEND_HEALTH_URL } from '@/lib/env';

export async function checkWarmup(signal: AbortSignal): Promise<boolean> {
  try {
    const response = await fetch(new URL('/api/v1/warmup', resolveBffBase()), { signal });
    return response.ok;
  } catch {
    return false;
  }
}

export function pingBackendDirectly(): void {
  if (!BACKEND_HEALTH_URL) return;
  fetch(BACKEND_HEALTH_URL, { mode: 'no-cors', cache: 'no-store' }).catch(() => undefined);
}
