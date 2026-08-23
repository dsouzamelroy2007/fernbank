import { randomUUID, createHmac } from 'node:crypto';
import type { Page } from '@playwright/test';

export const BACKEND_BASE_URL = process.env.BACKEND_BASE_URL ?? 'http://localhost:8080';

/** RFC 6238 TOTP-SHA1-6digit, matching the backend's hand-rolled TotpService exactly
 * (same standard, verified against the same RFC 6238 Appendix B vectors). */
export function totpCode(secretBase32: string, time = Date.now()): string {
  const counter = Math.floor(time / 1000 / 30);
  const counterBuffer = Buffer.alloc(8);
  counterBuffer.writeBigUInt64BE(BigInt(counter));
  const key = base32Decode(secretBase32);
  const hmac = createHmac('sha1', key).update(counterBuffer).digest();
  const offset = hmac[hmac.length - 1] & 0xf;
  const binCode =
    ((hmac[offset] & 0x7f) << 24) |
    ((hmac[offset + 1] & 0xff) << 16) |
    ((hmac[offset + 2] & 0xff) << 8) |
    (hmac[offset + 3] & 0xff);
  return (binCode % 1_000_000).toString().padStart(6, '0');
}

function base32Decode(base32: string): Buffer {
  const alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567';
  let bits = '';
  for (const char of base32.replace(/=+$/, '').toUpperCase()) {
    const val = alphabet.indexOf(char);
    if (val === -1) continue;
    bits += val.toString(2).padStart(5, '0');
  }
  const bytes: number[] = [];
  for (let i = 0; i + 8 <= bits.length; i += 8) {
    bytes.push(parseInt(bits.slice(i, i + 8), 2));
  }
  return Buffer.from(bytes);
}

export function uniqueEmail(prefix: string): string {
  return `${prefix}-${randomUUID()}@example.com`;
}

/** Direct backend calls for test setup — seeding accounts/balances/MFA has no UI in
 * Phase 7's scope (open-account and deposit screens weren't asked for), and driving the
 * UI for setup that isn't the behavior under test would just make these tests slower
 * and more brittle. */
export async function apiRegister(email: string, password: string): Promise<void> {
  await fetch(`${BACKEND_BASE_URL}/api/v1/auth/register`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ fullName: 'E2E User', email, password }),
  });
}

export async function apiLogin(email: string, password: string): Promise<string> {
  const response = await fetch(`${BACKEND_BASE_URL}/api/v1/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password }),
  });
  const body = await response.json();
  return body.accessToken as string;
}

export async function apiRegisterAndLogin(email: string, password: string): Promise<string> {
  await apiRegister(email, password);
  return apiLogin(email, password);
}

export async function apiOpenAccount(
  accessToken: string,
  currency = 'USD',
): Promise<{ id: string; accountNumber: string }> {
  const response = await fetch(`${BACKEND_BASE_URL}/api/v1/accounts`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${accessToken}`,
      'Idempotency-Key': randomUUID(),
    },
    body: JSON.stringify({ type: 'CHECKING', currency }),
  });
  return response.json();
}

export async function apiDeposit(
  accessToken: string,
  accountId: string,
  amount: string,
  currency = 'USD',
): Promise<void> {
  await fetch(`${BACKEND_BASE_URL}/api/v1/accounts/${accountId}/deposits`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${accessToken}`,
      'Idempotency-Key': randomUUID(),
    },
    body: JSON.stringify({ amount: { amount, currency }, description: 'e2e seed' }),
  });
}

/** Enrols and confirms MFA via the API, returning the TOTP secret so the caller can
 * derive codes for the login challenge and step-up screens that follow. */
export async function apiEnrollMfa(accessToken: string): Promise<string> {
  const enrollResponse = await fetch(`${BACKEND_BASE_URL}/api/v1/auth/mfa/enroll`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${accessToken}` },
  });
  const { secret } = await enrollResponse.json();
  await fetch(`${BACKEND_BASE_URL}/api/v1/auth/mfa/enroll/confirm`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${accessToken}` },
    body: JSON.stringify({ code: totpCode(secret) }),
  });
  return secret as string;
}

export async function loginViaUi(page: Page, email: string, password: string): Promise<void> {
  await page.goto('/login');
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password').fill(password);
  // The (public) layout's nav also has a "Sign in" link to this same page - scope to the
  // form's own submit button to avoid Playwright's strict-mode ambiguity.
  await page.locator('form').getByRole('button', { name: 'Sign in' }).click();
}
