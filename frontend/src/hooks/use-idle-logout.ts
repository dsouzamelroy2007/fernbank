'use client';

import { useEffect, useRef } from 'react';
import { useRouter } from 'next/navigation';
import { toast } from 'sonner';
import { useAuth } from '@/hooks/use-auth';

const IDLE_TIMEOUT_MS = 5 * 60 * 1000;
const WARNING_BEFORE_MS = 60 * 1000;
// Avoids clearing/resetting two timers on every single mousemove event during active
// use - only counts as "new activity" if this long has passed since the last reset.
const ACTIVITY_THROTTLE_MS = 2_000;

const ACTIVITY_EVENTS = ['mousedown', 'mousemove', 'keydown', 'touchstart', 'wheel'] as const;

/**
 * Auto-logs-out an idle session after IDLE_TIMEOUT_MS of no real user interaction - a
 * real gap this app had: without it, an inactive tab stays "logged in" for as long as
 * the refresh token lives (30 days), since the BFF refreshes the access token lazily
 * on whatever request eventually comes in, not on a fixed schedule. 5 minutes is
 * tighter than typical OWASP/PCI session-timeout guidance (~15 min) - a deliberate,
 * more conservative choice for this app, not a default. Deliberately counts real
 * interaction only (mouse/keyboard/touch), not background polling or the SSE
 * notification stream, and keeps counting even while the tab is hidden - switching
 * away and forgetting about it should still time out.
 */
export function useIdleLogout(enabled: boolean) {
  const { logout } = useAuth();
  const router = useRouter();
  const lastActivityRef = useRef(0);
  const warningTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const logoutTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const toastIdRef = useRef<string | number | null>(null);

  useEffect(() => {
    if (!enabled) return;

    function clearTimers() {
      if (warningTimerRef.current) clearTimeout(warningTimerRef.current);
      if (logoutTimerRef.current) clearTimeout(logoutTimerRef.current);
    }

    function scheduleTimers() {
      clearTimers();
      warningTimerRef.current = setTimeout(() => {
        toastIdRef.current = toast.warning('You will be signed out soon due to inactivity.', {
          duration: WARNING_BEFORE_MS,
          action: {
            label: 'Stay signed in',
            onClick: () => registerActivity(true),
          },
        });
      }, IDLE_TIMEOUT_MS - WARNING_BEFORE_MS);

      logoutTimerRef.current = setTimeout(() => {
        // Navigate first, then clear the session: AuthGate's own "not authenticated"
        // effect also calls router.replace('/login') (no query string) the moment
        // isAuthenticated flips false from inside logout() - if that ran first, it
        // would win the race and silently drop ?reason=idle. Navigating away unmounts
        // AuthGate before that effect gets a chance to fire.
        router.replace('/login?reason=idle');
        void logout();
      }, IDLE_TIMEOUT_MS);
    }

    function registerActivity(force = false) {
      const now = Date.now();
      if (!force && now - lastActivityRef.current < ACTIVITY_THROTTLE_MS) return;
      lastActivityRef.current = now;
      if (toastIdRef.current !== null) {
        toast.dismiss(toastIdRef.current);
        toastIdRef.current = null;
      }
      scheduleTimers();
    }

    function handleActivity() {
      registerActivity();
    }

    scheduleTimers();
    for (const event of ACTIVITY_EVENTS) {
      window.addEventListener(event, handleActivity, { passive: true });
    }

    return () => {
      clearTimers();
      for (const event of ACTIVITY_EVENTS) {
        window.removeEventListener(event, handleActivity);
      }
    };
  }, [enabled, logout, router]);
}
