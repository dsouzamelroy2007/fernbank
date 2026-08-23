import { test, expect } from '@playwright/test';
import { apiRegister, loginViaUi, uniqueEmail } from './helpers';

test('a user can list and revoke their own active sessions', async ({ page }) => {
  const email = uniqueEmail('session-revoke');
  const password = 'correct horse battery staple';
  await apiRegister(email, password);

  await loginViaUi(page, email, password);
  await expect(page).toHaveURL(/\/dashboard/);

  await page.goto('/profile');
  await page.getByRole('tab', { name: 'Sessions' }).click();

  const revokeButton = page.getByRole('button', { name: 'Revoke' }).first();
  await expect(revokeButton).toBeVisible();
  await revokeButton.click();

  await expect(page.getByText('Session revoked')).toBeVisible();
});
