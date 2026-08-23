'use client';

import { use, useEffect, useMemo, useRef, useState } from 'react';
import { useVirtualizer } from '@tanstack/react-virtual';
import { toast } from 'sonner';
import { Landmark } from 'lucide-react';
import { useAccount } from '@/hooks/queries/use-accounts';
import { useStatement } from '@/hooks/queries/use-statement';
import { formatMoney, isDebit } from '@/lib/format/money';
import { downloadFile } from '@/lib/api/download';
import { ApiError } from '@/lib/api/errors';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import { Field, FieldLabel } from '@/components/ui/field';
import { Skeleton } from '@/components/ui/skeleton';

const ROW_HEIGHT = 64;

export default function AccountDetailPage({ params }: { params: Promise<{ accountId: string }> }) {
  const { accountId } = use(params);

  const { data: account, isPending: isAccountPending } = useAccount(accountId);
  const [fromDate, setFromDate] = useState('');
  const [toDate, setToDate] = useState('');
  const [search, setSearch] = useState('');

  const filters = useMemo(
    () => ({
      from: fromDate ? `${fromDate}T00:00:00Z` : undefined,
      to: toDate ? `${toDate}T23:59:59Z` : undefined,
    }),
    [fromDate, toDate],
  );

  const {
    data,
    isPending: isStatementPending,
    fetchNextPage,
    hasNextPage,
    isFetchingNextPage,
  } = useStatement(accountId, filters);

  const allEntries = useMemo(() => data?.pages.flatMap((page) => page.data ?? []) ?? [], [data]);
  const entries = useMemo(() => {
    if (!search.trim()) return allEntries;
    const needle = search.trim().toLowerCase();
    return allEntries.filter((entry) => (entry.description ?? '').toLowerCase().includes(needle));
  }, [allEntries, search]);

  const parentRef = useRef<HTMLDivElement>(null);
  const rowVirtualizer = useVirtualizer({
    count: hasNextPage ? entries.length + 1 : entries.length,
    getScrollElement: () => parentRef.current,
    estimateSize: () => ROW_HEIGHT,
    overscan: 10,
  });

  const virtualItems = rowVirtualizer.getVirtualItems();
  useEffect(() => {
    const lastItem = virtualItems.at(-1);
    if (!lastItem) return;
    if (lastItem.index >= entries.length - 1 && hasNextPage && !isFetchingNextPage) {
      void fetchNextPage();
    }
  }, [virtualItems, entries.length, hasNextPage, isFetchingNextPage, fetchNextPage]);

  async function handleExport(format: 'csv' | 'pdf') {
    if (!account?.accountNumber) return;
    try {
      const query = new URLSearchParams({ format });
      if (filters.from) query.set('from', filters.from);
      if (filters.to) query.set('to', filters.to);
      await downloadFile(
        `/api/v1/accounts/${accountId}/statement/export?${query.toString()}`,
        `statement-${account.accountNumber}.${format}`,
      );
    } catch (error) {
      const message =
        error instanceof ApiError ? (error.detail ?? error.title ?? error.message) : 'Export failed';
      toast.error(message);
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center gap-4">
        <div className="icon-badge bg-accent text-accent-foreground size-12 rounded-full">
          <Landmark className="size-5" />
        </div>
        {isAccountPending ? (
          <Skeleton className="h-8 w-64" />
        ) : (
          <div>
            <h1 className="text-3xl font-bold tracking-tight">
              {account?.type} · {account?.accountNumber}
            </h1>
            <p className="text-muted-foreground text-sm">
              {formatMoney(account?.balance)} · {account?.status === 'FROZEN' ? 'Frozen' : 'Active'}
            </p>
          </div>
        )}
      </div>

      <Card className="rounded-3xl">
        <CardHeader>
          <CardTitle>Transactions</CardTitle>
        </CardHeader>
        <CardContent className="flex flex-col gap-4">
          <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
            <div className="flex flex-wrap gap-3">
              <Field className="w-auto">
                <FieldLabel htmlFor="from-date">From</FieldLabel>
                <Input
                  id="from-date"
                  type="date"
                  value={fromDate}
                  onChange={(e) => setFromDate(e.target.value)}
                />
              </Field>
              <Field className="w-auto">
                <FieldLabel htmlFor="to-date">To</FieldLabel>
                <Input id="to-date" type="date" value={toDate} onChange={(e) => setToDate(e.target.value)} />
              </Field>
              <Field className="w-auto">
                <FieldLabel htmlFor="search">Search</FieldLabel>
                <Input
                  id="search"
                  type="search"
                  placeholder="Description"
                  value={search}
                  onChange={(e) => setSearch(e.target.value)}
                />
              </Field>
            </div>
            <div className="flex gap-2">
              <Button variant="outline" onClick={() => handleExport('csv')}>
                Export CSV
              </Button>
              <Button variant="outline" onClick={() => handleExport('pdf')}>
                Export PDF
              </Button>
            </div>
          </div>
          {search && (
            <p className="text-muted-foreground text-xs">
              Search only covers transactions already loaded below — scroll to load more, or
              narrow the date range.
            </p>
          )}

          {isStatementPending ? (
            <div className="flex flex-col gap-2">
              {[1, 2, 3, 4, 5].map((i) => (
                <Skeleton key={i} className="h-14 w-full" />
              ))}
            </div>
          ) : entries.length === 0 ? (
            <p className="text-muted-foreground py-12 text-center text-sm">
              No transactions {search || fromDate || toDate ? 'match these filters' : 'yet'}.
            </p>
          ) : (
            <div ref={parentRef} className="h-[480px] overflow-auto rounded-md border">
              <div style={{ height: rowVirtualizer.getTotalSize(), position: 'relative', width: '100%' }}>
                {virtualItems.map((virtualRow) => {
                  const entry = entries[virtualRow.index];
                  if (!entry) {
                    return (
                      <div
                        key={virtualRow.key}
                        style={{
                          position: 'absolute',
                          top: 0,
                          left: 0,
                          width: '100%',
                          height: virtualRow.size,
                          transform: `translateY(${virtualRow.start}px)`,
                        }}
                        className="text-muted-foreground flex items-center justify-center text-xs"
                      >
                        Loading more…
                      </div>
                    );
                  }
                  return (
                    <div
                      key={virtualRow.key}
                      style={{
                        position: 'absolute',
                        top: 0,
                        left: 0,
                        width: '100%',
                        height: virtualRow.size,
                        transform: `translateY(${virtualRow.start}px)`,
                      }}
                      className="flex items-center gap-3 border-b px-4"
                    >
                      <span
                        className={
                          isDebit(entry.amount)
                            ? 'icon-badge bg-destructive/10 text-destructive size-9 rounded-full text-xs font-semibold'
                            : 'icon-badge size-9 rounded-full bg-emerald-500/10 text-xs font-semibold text-emerald-600 dark:text-emerald-400'
                        }
                      >
                        {isDebit(entry.amount) ? '↑' : '↓'}
                      </span>
                      <div className="min-w-0 flex-1">
                        <p className="truncate text-sm">{entry.description || 'Transaction'}</p>
                        <p className="text-muted-foreground text-xs">
                          {entry.createdAt ? new Date(entry.createdAt).toLocaleString() : ''}
                        </p>
                      </div>
                      <span
                        className={
                          isDebit(entry.amount)
                            ? 'text-destructive shrink-0 text-sm font-medium'
                            : 'shrink-0 text-sm font-medium text-emerald-600 dark:text-emerald-400'
                        }
                      >
                        {formatMoney(entry.amount)}
                      </span>
                    </div>
                  );
                })}
              </div>
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
