import type { components } from '@/lib/api/schema';

type StatementEntryResponse = components['schemas']['StatementEntryResponse'];

export interface DailySpend {
  date: string;
  amount: number;
}

export function dailySpend(entries: StatementEntryResponse[], days: number, now = new Date()): DailySpend[] {
  const totals = new Map<string, number>();
  for (let i = days - 1; i >= 0; i--) {
    const date = new Date(now);
    date.setDate(date.getDate() - i);
    totals.set(date.toISOString().slice(0, 10), 0);
  }

  for (const entry of entries) {
    const value = entry.amount?.amount ? Number(entry.amount.amount) : NaN;
    if (!Number.isFinite(value) || value >= 0 || !entry.createdAt) {
      continue;
    }
    const day = entry.createdAt.slice(0, 10);
    if (!totals.has(day)) {
      continue;
    }
    totals.set(day, (totals.get(day) ?? 0) + Math.abs(value));
  }

  return Array.from(totals.entries()).map(([date, amount]) => ({ date, amount }));
}
