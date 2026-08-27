'use client';

import { useEffect, useState } from 'react';
import { checkWarmup } from '@/lib/api/warmup';

const POLL_INTERVAL_MS = 4_000;
const POLL_TIMEOUT_MS = 8_000;

/**
 * Polls the bff's warmup probe until the backend (and, transitively, Neon) is
 * confirmed responsive. Render's free tier sleeps the bff and backend independently
 * after ~15 min idle; a cold first login otherwise races Vercel's shorter rewrite
 * timeout, fails silently on the client, and still burns a LoginRateLimiter attempt on
 * the server even though the user never saw success. Gating the real login submission
 * on this instead avoids that entirely - see warmup.controller.ts's doc comment.
 */
export function useBackendWarmup(): boolean {
  const [ready, setReady] = useState(false);

  useEffect(() => {
    let cancelled = false;

    async function poll() {
      while (!cancelled) {
        const controller = new AbortController();
        const timeoutId = setTimeout(() => controller.abort(), POLL_TIMEOUT_MS);
        const ok = await checkWarmup(controller.signal);
        clearTimeout(timeoutId);
        if (ok) {
          if (!cancelled) setReady(true);
          return;
        }
        if (cancelled) return;
        await new Promise((resolve) => setTimeout(resolve, POLL_INTERVAL_MS));
      }
    }

    void poll();
    return () => {
      cancelled = true;
    };
  }, []);

  return ready;
}
