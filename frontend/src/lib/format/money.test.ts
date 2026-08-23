import { describe, expect, it } from 'vitest';
import { formatMoney, isDebit } from '@/lib/format/money';

describe('formatMoney', () => {
  it('formats a positive amount with the currency symbol', () => {
    expect(formatMoney({ amount: '125.50', currency: 'USD' })).toBe('$125.50');
  });

  it('formats a negative (debit) amount', () => {
    expect(formatMoney({ amount: '-30.00', currency: 'USD' })).toBe('-$30.00');
  });

  it('returns a placeholder for missing data', () => {
    expect(formatMoney(undefined)).toBe('—');
    expect(formatMoney({ amount: undefined, currency: 'USD' })).toBe('—');
    expect(formatMoney({ amount: '10.00', currency: undefined })).toBe('—');
  });

  it('falls back to a plain string for an invalid currency code', () => {
    expect(formatMoney({ amount: '10.00', currency: 'NOTACODE' })).toBe('10.00 NOTACODE');
  });
});

describe('isDebit', () => {
  it('is true for a negative amount string', () => {
    expect(isDebit({ amount: '-5.00', currency: 'USD' })).toBe(true);
  });

  it('is false for a positive amount string', () => {
    expect(isDebit({ amount: '5.00', currency: 'USD' })).toBe(false);
  });

  it('is false when the amount is missing', () => {
    expect(isDebit(undefined)).toBe(false);
    expect(isDebit({ amount: undefined, currency: 'USD' })).toBe(false);
  });
});
