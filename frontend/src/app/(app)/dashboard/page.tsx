'use client';

import { useMemo } from 'react';
import Link from 'next/link';
import { Landmark } from 'lucide-react';
import { Area, AreaChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { useDashboard } from '@/hooks/queries/use-dashboard';
import { formatMoney, isDebit } from '@/lib/format/money';
import { mergeRecentActivity } from '@/lib/statement/activity';
import { dailySpend } from '@/lib/statement/spend';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';

const SPEND_WINDOW_DAYS = 30;
const RECENT_ACTIVITY_LIMIT = 10;

export default function DashboardPage() {
  const { data: dashboard, isPending } = useDashboard();
  const accounts = useMemo(() => dashboard?.accounts ?? [], [dashboard]);

  const entriesByAccount = useMemo(
    () =>
      accounts
        .filter((account): account is typeof account & { id: string } => !!account.id)
        .map((account) => ({ accountId: account.id, entries: account.recentStatement.entries })),
    [accounts],
  );
  const recentActivity = useMemo(
    () => mergeRecentActivity(entriesByAccount, RECENT_ACTIVITY_LIMIT),
    [entriesByAccount],
  );
  const spendData = useMemo(
    () => dailySpend(entriesByAccount.flatMap((a) => a.entries), SPEND_WINDOW_DAYS),
    [entriesByAccount],
  );
  const hasSpend = spendData.some((day) => day.amount > 0);
  const hasDegradedAccount = accounts.some((account) => account.recentStatement.degraded);

  return (
    <div className="flex flex-col gap-6">
      <div>
        {isPending ? (
          <Skeleton className="h-9 w-64" />
        ) : (
          <h1 className="text-3xl font-bold tracking-tight">
            Welcome back, {dashboard?.me.fullName?.split(' ')[0]}
          </h1>
        )}
        <p className="text-muted-foreground text-sm">{dashboard?.me.email}</p>
      </div>

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {isPending ? (
          [1, 2, 3].map((i) => (
            <Card key={i} className="rounded-3xl">
              <CardHeader>
                <CardTitle className="text-muted-foreground text-sm font-normal">Account</CardTitle>
                <Skeleton className="h-9 w-32" />
              </CardHeader>
            </Card>
          ))
        ) : accounts.length > 0 ? (
          accounts.map((account) => (
            <Link key={account.id} href={`/accounts/${account.id}`} className="group">
              <div className="brand-gradient-card relative flex h-full flex-col justify-between gap-6 rounded-3xl p-6 shadow-lg shadow-primary/15 transition-transform duration-200 group-hover:-translate-y-0.5 group-hover:shadow-xl group-hover:shadow-primary/25">
                <div className="flex items-start justify-between">
                  <div>
                    <p className="text-xs font-medium tracking-wide text-white/70 uppercase">
                      {account.type}
                    </p>
                    <p className="font-mono text-xs text-white/60">{account.accountNumber}</p>
                  </div>
                  <Landmark className="size-5 text-white/50" />
                </div>
                <div>
                  <p className="balance-figure text-4xl font-bold">{formatMoney(account.balance)}</p>
                  <p className="mt-1 text-xs text-white/70">
                    {account.status === 'FROZEN' ? 'Frozen' : 'Active'}
                  </p>
                </div>
              </div>
            </Link>
          ))
        ) : (
          <Card className="rounded-3xl sm:col-span-2 lg:col-span-3">
            <CardHeader>
              <CardTitle>No accounts yet</CardTitle>
              <p className="text-muted-foreground text-sm">
                Open an account with the API or Postman collection to start banking with
                fernbank.
              </p>
            </CardHeader>
          </Card>
        )}
      </div>

      {hasDegradedAccount && (
        <p className="text-muted-foreground text-xs">
          One account&apos;s recent activity couldn&apos;t be loaded just now — its balance above is
          still current, only its contribution to the chart and feed below is missing.
        </p>
      )}

      <Card className="rounded-3xl">
        <CardHeader>
          <CardTitle>Spending, last {SPEND_WINDOW_DAYS} days</CardTitle>
        </CardHeader>
        <CardContent>
          {isPending ? (
            <Skeleton className="h-60 w-full" />
          ) : !hasSpend ? (
            <p className="text-muted-foreground py-8 text-center text-sm">
              No spending in the last {SPEND_WINDOW_DAYS} days.
            </p>
          ) : (
            <ResponsiveContainer width="100%" height={240}>
              <AreaChart data={spendData}>
                <defs>
                  <linearGradient id="spendFill" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stopColor="var(--chart-1)" stopOpacity={0.35} />
                    <stop offset="100%" stopColor="var(--chart-1)" stopOpacity={0} />
                  </linearGradient>
                </defs>
                <CartesianGrid strokeDasharray="3 3" className="stroke-border" />
                <XAxis
                  dataKey="date"
                  tickFormatter={(d: string) => d.slice(5)}
                  fontSize={12}
                  stroke="var(--muted-foreground)"
                />
                <YAxis fontSize={12} width={48} stroke="var(--muted-foreground)" />
                <Tooltip
                  formatter={(value) => (typeof value === 'number' ? value.toFixed(2) : String(value))}
                  contentStyle={{
                    background: 'var(--popover)',
                    color: 'var(--popover-foreground)',
                    border: '1px solid var(--border)',
                    borderRadius: 'var(--radius-md)',
                    fontSize: '0.8rem',
                  }}
                  labelStyle={{ color: 'var(--muted-foreground)' }}
                />
                <Area
                  type="monotone"
                  dataKey="amount"
                  stroke="var(--chart-1)"
                  strokeWidth={2}
                  fill="url(#spendFill)"
                />
              </AreaChart>
            </ResponsiveContainer>
          )}
        </CardContent>
      </Card>

      <Card className="rounded-3xl">
        <CardHeader>
          <CardTitle>Recent activity</CardTitle>
        </CardHeader>
        <CardContent>
          {isPending ? (
            <div className="flex flex-col gap-2">
              {[1, 2, 3].map((i) => (
                <Skeleton key={i} className="h-10 w-full" />
              ))}
            </div>
          ) : recentActivity.length === 0 ? (
            <p className="text-muted-foreground py-8 text-center text-sm">No recent transactions.</p>
          ) : (
            <ul className="flex flex-col divide-y">
              {recentActivity.map((entry) => {
                const debit = isDebit(entry.amount);
                return (
                  <li key={entry.id} className="flex items-center gap-3 py-3 text-sm">
                    <span
                      className={
                        debit
                          ? 'bg-destructive/10 text-destructive flex size-9 shrink-0 items-center justify-center rounded-full text-xs font-semibold'
                          : 'flex size-9 shrink-0 items-center justify-center rounded-full bg-emerald-500/10 text-xs font-semibold text-emerald-600 dark:text-emerald-400'
                      }
                    >
                      {debit ? '↑' : '↓'}
                    </span>
                    <div className="min-w-0 flex-1">
                      <p className="truncate font-medium">{entry.description || 'Transaction'}</p>
                      <p className="text-muted-foreground text-xs">
                        {entry.createdAt ? new Date(entry.createdAt).toLocaleString() : ''}
                      </p>
                    </div>
                    <span
                      className={
                        debit
                          ? 'text-destructive shrink-0 font-medium'
                          : 'shrink-0 font-medium text-emerald-600 dark:text-emerald-400'
                      }
                    >
                      {formatMoney(entry.amount)}
                    </span>
                  </li>
                );
              })}
            </ul>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
