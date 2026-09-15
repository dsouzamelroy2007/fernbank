'use client';

import { useEffect, useRef } from 'react';
import { useRouter } from 'next/navigation';
import { toast } from 'sonner';
import { useAuth } from '@/hooks/use-auth';

const IDLE_TIMEOUT_MS = 5 * 60 * 1000;
const WARNING_BEFORE_MS = 60 * 1000;
const ACTIVITY_THROTTLE_MS = 2_000;

const ACTIVITY_EVENTS = ['mousedown', 'mousemove', 'keydown', 'touchstart', 'wheel'] as const;

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
