import { describe, expect, it } from 'vitest';
import { mergeRecentActivity } from '@/lib/statement/activity';

describe('mergeRecentActivity', () => {
  it('merges entries from multiple accounts sorted newest-first', () => {
    const result = mergeRecentActivity(
      [
        {
          accountId: 'a',
          entries: [{ id: '1', createdAt: '2026-08-19T10:00:00Z', amount: { amount: '10.00', currency: 'USD' } }],
        },
        {
          accountId: 'b',
          entries: [{ id: '2', createdAt: '2026-08-20T10:00:00Z', amount: { amount: '-5.00', currency: 'USD' } }],
        },
      ],
      10,
    );
    expect(result.map((e) => e.id)).toEqual(['2', '1']);
    expect(result[0].accountId).toBe('b');
  });

  it('caps the result at the given limit', () => {
    const entries = Array.from({ length: 5 }, (_, i) => ({
      id: `${i}`,
      createdAt: `2026-08-${10 + i}T10:00:00Z`,
      amount: { amount: '1.00', currency: 'USD' },
    }));
    const result = mergeRecentActivity([{ accountId: 'a', entries }], 2);
    expect(result).toHaveLength(2);
    // Newest two: ids "4" and "3" (2026-08-14, 2026-08-13).
    expect(result.map((e) => e.id)).toEqual(['4', '3']);
  });

  it('returns an empty list when there are no accounts', () => {
    expect(mergeRecentActivity([], 10)).toEqual([]);
  });
});
