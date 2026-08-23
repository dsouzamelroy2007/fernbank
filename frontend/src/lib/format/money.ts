/**
 * Display-only formatting of the wire-format `{ amount: string; currency: string }`
 * (CONTRIBUTING.md: money crosses the API as a decimal string, never a raw number). This
 * never feeds back into arithmetic that matters — the backend remains the source of
 * truth for every real balance/transfer figure; the one place this module's parsed
 * `Number` is summed client-side (the dashboard's spend chart) is a visualization aid,
 * not an authoritative amount.
 */
export interface MoneyLike {
  amount?: string;
  currency?: string;
}

export function formatMoney(money: MoneyLike | undefined): string {
  if (!money?.amount || !money.currency) {
    return '—';
  }
  try {
    return new Intl.NumberFormat(undefined, {
      style: 'currency',
      currency: money.currency,
    }).format(Number(money.amount));
  } catch {
    return `${money.amount} ${money.currency}`;
  }
}

/** True when the decimal amount string represents a debit (negative minor units). */
export function isDebit(money: MoneyLike | undefined): boolean {
  return money?.amount != null && money.amount.trim().startsWith('-');
}
