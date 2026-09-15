'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import { checkWarmup, pingBackendDirectly } from '@/lib/api/warmup';

const ATTEMPT_TIMEOUT_MS = 175_000;

export type WarmupState = 'checking' | 'ready' | 'failed';

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
