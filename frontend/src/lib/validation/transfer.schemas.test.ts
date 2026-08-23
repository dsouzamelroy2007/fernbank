import { describe, expect, it } from 'vitest';
import { moneyDtoSchema, transferSchema } from '@/lib/validation/transfer.schemas';

const VALID_ACCOUNT_NUMBER = 'FB270000000000000000';
const VALID_SOURCE = '3fa85f64-5717-4562-b3fc-2c963f66afa6';

describe('moneyDtoSchema', () => {
  it('accepts a positive decimal amount and a 3-letter currency', () => {
    expect(moneyDtoSchema.safeParse({ amount: '125.50', currency: 'USD' }).success).toBe(true);
  });

  it('rejects zero and negative amounts', () => {
    expect(moneyDtoSchema.safeParse({ amount: '0', currency: 'USD' }).success).toBe(false);
    expect(moneyDtoSchema.safeParse({ amount: '-5.00', currency: 'USD' }).success).toBe(false);
  });

  it('rejects a non-numeric amount', () => {
    expect(moneyDtoSchema.safeParse({ amount: 'abc', currency: 'USD' }).success).toBe(false);
  });

  it('rejects a malformed currency code', () => {
    expect(moneyDtoSchema.safeParse({ amount: '10.00', currency: 'us' }).success).toBe(false);
    expect(moneyDtoSchema.safeParse({ amount: '10.00', currency: 'DOLLAR' }).success).toBe(false);
  });
});

describe('transferSchema', () => {
  const base = { sourceAccountId: VALID_SOURCE, amount: { amount: '10.00', currency: 'USD' } };

  it('accepts a transfer to an account number', () => {
    expect(
      transferSchema.safeParse({ ...base, destinationAccountNumber: VALID_ACCOUNT_NUMBER }).success,
    ).toBe(true);
  });

  it('accepts a transfer to an account id', () => {
    expect(
      transferSchema.safeParse({ ...base, destinationAccountId: VALID_SOURCE }).success,
    ).toBe(true);
  });

  it('rejects neither destination field being set', () => {
    expect(transferSchema.safeParse(base).success).toBe(false);
  });

  it('rejects both destination fields being set at once', () => {
    expect(
      transferSchema.safeParse({
        ...base,
        destinationAccountId: VALID_SOURCE,
        destinationAccountNumber: VALID_ACCOUNT_NUMBER,
      }).success,
    ).toBe(false);
  });

  it('rejects an invalid destination account number', () => {
    expect(
      transferSchema.safeParse({ ...base, destinationAccountNumber: 'not-an-account' }).success,
    ).toBe(false);
  });
});
