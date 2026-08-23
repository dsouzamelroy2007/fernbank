import { describe, expect, it } from 'vitest';
import { isValidFernbankAccountNumber } from '@/lib/validation/iban';

// "FB" + BBAN "0000000000000000" checksums to "27" via the same ISO 7064 MOD 97-10
// algorithm banking.AccountNumberGenerator uses (verified against a standalone
// implementation of that exact algorithm, not asserted from thin air).
const VALID = 'FB270000000000000000';

describe('isValidFernbankAccountNumber', () => {
  it('accepts a checksum-valid account number', () => {
    expect(isValidFernbankAccountNumber(VALID)).toBe(true);
  });

  it('rejects a tampered check digit', () => {
    expect(isValidFernbankAccountNumber('FB260000000000000000')).toBe(false);
  });

  it('rejects the wrong length', () => {
    expect(isValidFernbankAccountNumber('FB88000000')).toBe(false);
  });

  it('rejects a non-numeric BBAN', () => {
    expect(isValidFernbankAccountNumber('FB88ABCDEFGHIJKLMNOP')).toBe(false);
  });

  it('rejects an empty string', () => {
    expect(isValidFernbankAccountNumber('')).toBe(false);
  });
});
