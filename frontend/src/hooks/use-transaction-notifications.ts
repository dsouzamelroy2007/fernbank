'use client';

import { useEffect } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { resolveBffBase } from '@/lib/env';
import { formatMoney } from '@/lib/format/money';
import { announceBalanceUpdate } from '@/lib/format/announce';
import type { components } from '@/lib/api/schema';

type MoneyDto = components['schemas']['MoneyDto'];

interface TransactionNotification {
  accountId: string;
  entry: {
    id?: string;
    transactionId?: string;
    createdAt?: string;
    amount?: MoneyDto;
    description?: string;
  };
}

/**
 * GET /bff/notifications (Phase 8) — real SSE push to the browser backed by the BFF
 * polling the backend on the session's behalf (no push mechanism exists backend-side).
 * `withCredentials: true` is required explicitly: cross-origin EventSource defaults to
 * not sending cookies, same as bare fetch, and the session cookie is how the BFF knows
 * whose accounts to poll. The connection is only opened while authenticated.
 */
export function useTransactionNotifications(enabled: boolean) {
  const queryClient = useQueryClient();

  useEffect(() => {
    if (!enabled) {
      return;
    }

    const source = new EventSource(new URL('/bff/notifications', resolveBffBase()), {
      withCredentials: true,
    });

    source.addEventListener('transaction', (event) => {
      let notification: TransactionNotification;
      try {
        notification = JSON.parse((event as MessageEvent<string>).data) as TransactionNotification;
      } catch {
        return;
      }

      const label = notification.entry.description || 'New transaction';
      const amountLabel = formatMoney(notification.entry.amount);
      toast(label, { description: amountLabel });
      announceBalanceUpdate(`${label}, ${amountLabel}`);

      void queryClient.invalidateQueries({ queryKey: ['bff-dashboard'] });
      void queryClient.invalidateQueries({
        queryKey: ['accounts', notification.accountId, 'statement'],
      });
    });

    return () => source.close();
  }, [enabled, queryClient]);
}
