'use client';

import { useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { useAuth } from '@/hooks/use-auth';
import { useTransactionNotifications } from '@/hooks/use-transaction-notifications';
import { useIdleLogout } from '@/hooks/use-idle-logout';
import { Skeleton } from '@/components/ui/skeleton';

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
