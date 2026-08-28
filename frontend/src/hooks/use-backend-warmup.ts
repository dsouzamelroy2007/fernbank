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
 *
 * Re-checks immediately on tab refocus rather than relying solely on the interval
 * timer: browsers throttle setTimeout heavily in backgrounded tabs (Chrome can drop to
 * once a minute or less after ~5 min backgrounded), so a user who tabs away to check
 * something else while waiting would otherwise see this appear stuck long after the
 * backend actually became ready.
 */
export function useBackendWarmup(): boolean {
  const [ready, setReady] = useState(false);

  useEffect(() => {
    if (ready) return;
    let cancelled = false;
    let scheduled: ReturnType<typeof setTimeout> | undefined;

    async function attempt() {
      const controller = new AbortController();
      const abortTimer = setTimeout(() => controller.abort(), POLL_TIMEOUT_MS);
      const ok = await checkWarmup(controller.signal);
      clearTimeout(abortTimer);
      if (cancelled) return;
      if (ok) {
        setReady(true);
        return;
      }
      scheduled = setTimeout(runAttempt, POLL_INTERVAL_MS);
    }

    function runAttempt() {
      void attempt();
    }

    function handleVisibility() {
      if (document.visibilityState !== 'visible' || cancelled) return;
      if (scheduled) clearTimeout(scheduled);
      runAttempt();
    }

    document.addEventListener('visibilitychange', handleVisibility);
    runAttempt();

    return () => {
      cancelled = true;
      if (scheduled) clearTimeout(scheduled);
      document.removeEventListener('visibilitychange', handleVisibility);
    };
  }, [ready]);

  return ready;
}
