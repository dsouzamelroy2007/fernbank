import { test, expect } from '@playwright/test';
import { totpCode, uniqueEmail } from './helpers';

test('register, sign in, and enrol MFA end to end', async ({ page }) => {
  const email = uniqueEmail('register-mfa');
  const password = 'correct horse battery staple';

  await page.goto('/register');
  await page.getByLabel('Full name').fill('E2E User');
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password').fill(password);
  await page.getByRole('button', { name: 'Create account' }).click();

  await expect(page).toHaveURL(/\/login/);

  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password').fill(password);
  await page.locator('form').getByRole('button', { name: 'Sign in' }).click();
  await expect(page).toHaveURL(/\/dashboard/);

  await page.goto('/profile');
  await page.getByRole('tab', { name: 'MFA' }).click();
  await page.getByRole('button', { name: 'Enable MFA' }).click();

  const secretText = await page.locator('p.font-mono').innerText();
  const secret = secretText.trim();
  expect(secret.length).toBeGreaterThan(0);

  await page.getByLabel('Authentication code').fill(totpCode(secret));
  await page.getByRole('button', { name: 'Confirm' }).click();

  await expect(page.getByText('MFA enabled')).toBeVisible();
  const recoveryCodes = page.locator('ul.grid > li');
  await expect(recoveryCodes.first()).toBeVisible();
  expect(await recoveryCodes.count()).toBeGreaterThan(0);

  await page.getByLabel(/saved these recovery codes/).check();
  await page.getByRole('button', { name: 'Done' }).click();
  await expect(page.getByRole('button', { name: 'Enable MFA' })).toBeVisible();
});
