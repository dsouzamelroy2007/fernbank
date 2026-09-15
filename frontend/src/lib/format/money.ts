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

export function isDebit(money: MoneyLike | undefined): boolean {
  return money?.amount != null && money.amount.trim().startsWith('-');
}
