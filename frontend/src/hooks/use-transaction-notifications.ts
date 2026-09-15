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
