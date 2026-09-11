'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import { checkWarmup, pingBackendDirectly } from '@/lib/api/warmup';

// Generous on purpose - a genuinely cold backend + Neon has taken 90-180s+ to respond
// in practice. See warmup.controller.ts's doc comment for the incident that set this.
const ATTEMPT_TIMEOUT_MS = 175_000;

export type WarmupState = 'checking' | 'ready' | 'failed';

/**
 * Makes a single attempt to confirm the backend (and, transitively, Neon) is
 * responsive via the bff's warmup probe, instead of polling on a fixed interval.
 * Render's free tier sleeps the bff and backend independently after ~15 min idle; a
 * cold first login otherwise races Vercel's shorter rewrite timeout, fails silently on
 * the client, and still burns a LoginRateLimiter attempt on the server even though the
 * user never saw success. Gating the real login submission on this instead avoids that
 * entirely - see warmup.controller.ts's doc comment.
 *
 * Confirmed live (2026-08-30): this used to auto-retry on a fixed interval (4s, then
 * 25s after the first fix attempt). Both got rejected by Render's own infrastructure -
 * `x-render-routing: hibernate-rate-limited` - which treats repeated requests to a
 * hibernating service as abuse and blocks them with a 429, regardless of how long the
 * interval was tuned to. Render's own docs describe showing a "loading page" to
 * browsers during spin-up, implying the platform expects one request that waits it
 * out, not a client polling on any schedule. There is no interval that's guaranteed
 * safe against a platform-level anti-abuse mechanism, so this makes exactly one
 * attempt and surfaces failure as a retryable state instead of auto-looping - a human
 * clicking "Try again" (or switching back to this tab) is sparse enough to not look
 * like the same abuse pattern.
 *
 * Confirmed live (2026-09-11): none of the above actually cleared it - the identical
 * 429 persisted across every request-pattern change tried, including a browser-like
 * User-Agent on the bff's own call and a completely fresh bff process's very first
 * attempt. The same health check from a real browser, directly, has consistently woken
 * the backend with no block. That points at the bff's own Render-assigned origin being
 * the thing rate-limited, not request timing or headers - so this now also fires
 * pingBackendDirectly() from the browser itself alongside the bff-routed check, to get
 * the wake-up request to Render from a path that's actually proven to work.
 *
 * Confirmed live (2026-09-12): this fixed it. A genuinely cold stack (both services
 * slept, no manual pre-warming) woke and completed login successfully on a normal
 * visit to the Vercel URL. Still a sidestep, not a root-cause fix - Render support
 * never identified why the bff's own origin gets blocked - so if this stops working,
 * read this file's and warmup.controller.ts's full incident history before trying
 * another timing/header variation; several were already tried and confirmed useless.
 */
export function useBackendWarmup(): { state: WarmupState; retry: () => void } {
  const [state, setState] = useState<WarmupState>('checking');
  const inFlight = useRef(false);
  const attemptRef = useRef<() => void>(() => {});

  useEffect(() => {
    let cancelled = false;

    async function attempt() {
      if (inFlight.current) return;
      inFlight.current = true;
      setState('checking');
      // Best-effort, fire-and-forget - see pingBackendDirectly's doc comment for why
      // this exists alongside checkWarmup rather than replacing it: a request that
      // looks like it comes from the bff has been rate-limited waking the backend from
      // Render's free-tier hibernation, while the same request from a browser hasn't.
      pingBackendDirectly();
      const controller = new AbortController();
      const abortTimer = setTimeout(() => controller.abort(), ATTEMPT_TIMEOUT_MS);
      const ok = await checkWarmup(controller.signal);
      clearTimeout(abortTimer);
      inFlight.current = false;
      if (cancelled) return;
      setState(ok ? 'ready' : 'failed');
    }

    attemptRef.current = () => void attempt();

    function handleVisibility() {
      if (document.visibilityState !== 'visible' || cancelled) return;
      if (inFlight.current) return;
      setState((current) => {
        if (current === 'failed') attemptRef.current();
        return current;
      });
    }

    document.addEventListener('visibilitychange', handleVisibility);
    attemptRef.current();

    return () => {
      cancelled = true;
      document.removeEventListener('visibilitychange', handleVisibility);
    };
  }, []);

  const retry = useCallback(() => {
    if (!inFlight.current) attemptRef.current();
  }, []);

  return { state, retry };
}
