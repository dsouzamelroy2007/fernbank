import { test, expect } from '@playwright/test';
import { apiOpenAccount, apiRegisterAndLogin, loginViaUi, uniqueEmail } from './helpers';

test('a transfer beyond the source balance is rejected, not silently applied', async ({ page }) => {
  const password = 'correct horse battery staple';
  const sourceEmail = uniqueEmail('insufficient-source');
  const destEmail = uniqueEmail('insufficient-dest');

  // Source account is left at its opening balance of 0.00 - no deposit.
  const sourceToken = await apiRegisterAndLogin(sourceEmail, password);
  const sourceAccount = await apiOpenAccount(sourceToken, 'USD');

  const destToken = await apiRegisterAndLogin(destEmail, password);
  const destAccount = await apiOpenAccount(destToken, 'USD');

  await loginViaUi(page, sourceEmail, password);
  await expect(page).toHaveURL(/\/dashboard/);

  await page.goto('/transfer');
  await page.getByLabel('From account').selectOption({ value: sourceAccount.id });
  await page.getByRole('button', { name: 'Continue' }).click();

  await page.getByRole('tab', { name: 'New recipient' }).click();
  await page.getByLabel('Recipient account number').fill(destAccount.accountNumber);
  await page.getByRole('button', { name: 'Continue' }).click();

  await page.getByLabel('Amount').fill('10.00');
  await page.getByRole('button', { name: 'Review' }).click();
  await page.getByRole('button', { name: 'Confirm transfer' }).click();

  await expect(page.getByText(/insufficient funds/i)).toBeVisible();
  // Rejected, not silently accepted - still on the review step, not a receipt.
  await expect(page.getByText('Transfer complete')).not.toBeVisible();
  await expect(page.getByRole('button', { name: 'Confirm transfer' })).toBeVisible();
});
