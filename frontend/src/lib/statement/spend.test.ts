import { describe, expect, it } from 'vitest';
import { dailySpend } from '@/lib/statement/spend';

const NOW = new Date('2026-08-21T12:00:00Z');

describe('dailySpend', () => {
  it('returns one zeroed bucket per day when there are no entries', () => {
    const result = dailySpend([], 3, NOW);
    expect(result).toEqual([
      { date: '2026-08-19', amount: 0 },
      { date: '2026-08-20', amount: 0 },
      { date: '2026-08-21', amount: 0 },
    ]);
  });

  it('sums only debit (negative-amount) entries into their day', () => {
    const result = dailySpend(
      [
        { id: '1', createdAt: '2026-08-20T10:00:00Z', amount: { amount: '-12.50', currency: 'USD' } },
        { id: '2', createdAt: '2026-08-20T14:00:00Z', amount: { amount: '-7.50', currency: 'USD' } },
        // A credit (deposit) on the same day must not count as spend.
        { id: '3', createdAt: '2026-08-20T16:00:00Z', amount: { amount: '100.00', currency: 'USD' } },
      ],
      3,
      NOW,
    );
    expect(result.find((d) => d.date === '2026-08-20')?.amount).toBe(20);
    expect(result.find((d) => d.date === '2026-08-19')?.amount).toBe(0);
  });

  it('ignores entries outside the requested window', () => {
    const result = dailySpend(
      [{ id: '1', createdAt: '2026-08-01T10:00:00Z', amount: { amount: '-50.00', currency: 'USD' } }],
      3,
      NOW,
    );
    expect(result.every((d) => d.amount === 0)).toBe(true);
  });

  it('ignores entries with a malformed amount rather than throwing', () => {
    const result = dailySpend(
      [{ id: '1', createdAt: '2026-08-20T10:00:00Z', amount: { amount: 'not-a-number', currency: 'USD' } }],
      3,
      NOW,
    );
    expect(result.every((d) => d.amount === 0)).toBe(true);
  });
});
