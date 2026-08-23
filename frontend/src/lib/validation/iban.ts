/**
 * Mirrors the backend's `banking.AccountNumberGenerator.isValidChecksum` exactly (ISO
 * 7064 MOD 97-10 over "FB" + 2 check digits + 16-digit BBAN, letters mapped A=10..Z=35)
 * so a malformed/mistyped account number is caught client-side before a real API call.
 * The backend's `@Iban` validator remains the actual authority — this is UX, not the
 * security boundary.
 */
const COUNTRY_CODE = 'FB';
const BBAN_LENGTH = 16;
const ACCOUNT_NUMBER_LENGTH = COUNTRY_CODE.length + 2 + BBAN_LENGTH;

function toNumericString(input: string): string {
  let result = '';
  for (const char of input) {
    if (/[A-Za-z]/.test(char)) {
      result += (char.toUpperCase().charCodeAt(0) - 'A'.charCodeAt(0) + 10).toString();
    } else {
      result += char;
    }
  }
  return result;
}

export function isValidFernbankAccountNumber(accountNumber: string): boolean {
  if (accountNumber.length !== ACCOUNT_NUMBER_LENGTH) {
    return false;
  }
  const countryCode = accountNumber.slice(0, 2);
  const checkDigits = accountNumber.slice(2, 4);
  const bban = accountNumber.slice(4);
  if (!/^\d+$/.test(checkDigits) || !/^\d+$/.test(bban)) {
    return false;
  }
  const numeric = toNumericString(bban + countryCode + checkDigits);
  try {
    return BigInt(numeric) % BigInt(97) === BigInt(1);
  } catch {
    return false;
  }
}
