'use client';

import { useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { useAuth } from '@/hooks/use-auth';
import { useTransactionNotifications } from '@/hooks/use-transaction-notifications';
import { useIdleLogout } from '@/hooks/use-idle-logout';
import { Skeleton } from '@/components/ui/skeleton';

/**
 * Client-side fallback behind proxy.ts's cookie-presence check: covers the brief window
 * on every mount where the BFF session cookie exists but this component hasn't
 * confirmed it yet (the bootstrap GET /me call in auth-context.tsx hasn't resolved),
 * and the rarer case where the cookie turns out to be stale (e.g. a revoked refresh
 * token) and that call fails after proxy.ts already let the request through.
 */
export function AuthGate({ children }: { children: React.ReactNode }) {
  const { isAuthenticated, isBootstrapping } = useAuth();
  const router = useRouter();

  useTransactionNotifications(isAuthenticated);
  useIdleLogout(isAuthenticated);

  useEffect(() => {
    if (!isBootstrapping && !isAuthenticated) {
      router.replace('/login');
    }
  }, [isBootstrapping, isAuthenticated, router]);

  if (isBootstrapping || !isAuthenticated) {
    return (
      <div className="flex flex-1 flex-col gap-4">
        <Skeleton className="h-8 w-48" />
        <Skeleton className="h-32 w-full" />
        <Skeleton className="h-32 w-full" />
      </div>
    );
  }

  return <>{children}</>;
}
