import type { components } from '@/lib/api/schema';

type StatementEntryResponse = components['schemas']['StatementEntryResponse'];

export interface ActivityEntry extends StatementEntryResponse {
  accountId: string;
}

/** Merges each account's entries into one feed sorted newest-first, capped at `limit`. */
export function mergeRecentActivity(
  entriesByAccount: Array<{ accountId: string; entries: StatementEntryResponse[] }>,
  limit: number,
): ActivityEntry[] {
  const merged: ActivityEntry[] = entriesByAccount.flatMap(({ accountId, entries }) =>
    entries.map((entry) => ({ ...entry, accountId })),
  );
  merged.sort((a, b) => (b.createdAt ?? '').localeCompare(a.createdAt ?? ''));
  return merged.slice(0, limit);
}
