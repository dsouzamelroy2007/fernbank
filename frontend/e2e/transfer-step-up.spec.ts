import { test, expect } from '@playwright/test';
import {
  apiDeposit,
  apiEnrollMfa,
  apiOpenAccount,
  apiRegisterAndLogin,
  loginViaUi,
  totpCode,
  uniqueEmail,
} from './helpers';

test('transfer over the step-up threshold requires a fresh MFA code', async ({ page }) => {
  const password = 'correct horse battery staple';
  const sourceEmail = uniqueEmail('stepup-source');
  const destEmail = uniqueEmail('stepup-dest');

  const sourceToken = await apiRegisterAndLogin(sourceEmail, password);
  const sourceAccount = await apiOpenAccount(sourceToken, 'USD');
  await apiDeposit(sourceToken, sourceAccount.id, '2000.00', 'USD');
  const mfaSecret = await apiEnrollMfa(sourceToken);

  const destToken = await apiRegisterAndLogin(destEmail, password);
  const destAccount = await apiOpenAccount(destToken, 'USD');

  await loginViaUi(page, sourceEmail, password);
  // MFA is now enrolled, so login itself issues a challenge before dashboard access.
  await page.getByLabel('Authentication code').fill(totpCode(mfaSecret));
  await page.getByRole('button', { name: 'Verify' }).click();
  await expect(page).toHaveURL(/\/dashboard/);

  await page.goto('/transfer');
  await page.getByLabel('From account').selectOption({ value: sourceAccount.id });
  await page.getByRole('button', { name: 'Continue' }).click();

  await page.getByRole('tab', { name: 'New recipient' }).click();
  await page.getByLabel('Recipient account number').fill(destAccount.accountNumber);
  await page.getByRole('button', { name: 'Continue' }).click();

  await page.getByLabel('Amount').fill('1500.00');
  await page.getByRole('button', { name: 'Review' }).click();
  await page.getByRole('button', { name: 'Confirm transfer' }).click();

  await expect(page.getByText(/step-up threshold/i)).toBeVisible();
  await page.getByLabel('Authentication code').fill(totpCode(mfaSecret));
  await page.getByRole('button', { name: 'Verify and send' }).click();

  await expect(page.getByText('Transfer complete', { exact: true })).toBeVisible();
});
