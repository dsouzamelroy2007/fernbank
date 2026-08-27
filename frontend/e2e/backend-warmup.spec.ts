import { test, expect } from '@playwright/test';
import { apiRegister, uniqueEmail } from './helpers';

test('login form stays disabled with a waking-up message until the warmup probe succeeds, then submits normally', async ({
  page,
}) => {
  const email = uniqueEmail('warmup');
  const password = 'correct horse battery staple';
  await apiRegister(email, password);

  let warmupCalls = 0;
  await page.route('**/api/v1/warmup', async (route) => {
    warmupCalls += 1;
    if (warmupCalls < 3) {
      await route.fulfill({ status: 503, body: '{}' });
    } else {
      await route.fulfill({ status: 200, body: JSON.stringify({ status: 'UP' }) });
    }
  });

  await page.goto('/login');

  const signInButton = page.locator('form').getByRole('button', { name: 'Waking up…' });
  await expect(signInButton).toBeVisible();
  await expect(signInButton).toBeDisabled();
  await expect(page.getByText('Waking up the demo servers', { exact: false })).toBeVisible();

  // The simulated cold start clears after a couple of failed polls (see the route
  // handler above) - the button should flip to the normal, enabled "Sign in" state
  // with no page reload or user action needed. useBackendWarmup polls every 4s, so
  // three attempts (two 503s + the successful one) take a bit over 8s - give this
  // plenty of headroom rather than racing the default 5s assertion timeout.
  await expect(page.locator('form').getByRole('button', { name: 'Sign in' })).toBeEnabled({
    timeout: 20_000,
  });
  expect(warmupCalls).toBeGreaterThanOrEqual(3);

  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password').fill(password);
  await page.locator('form').getByRole('button', { name: 'Sign in' }).click();

  await expect(page).toHaveURL(/\/dashboard/);
});
