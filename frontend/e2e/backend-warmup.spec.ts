import { test, expect } from '@playwright/test';
import { apiRegister, uniqueEmail } from './helpers';

test('login form shows a retry state on a failed warmup check, then submits normally once retried', async ({
  page,
}) => {
  const email = uniqueEmail('warmup');
  const password = 'correct horse battery staple';
  await apiRegister(email, password);

  let backendReady = false;
  await page.route('**/api/v1/warmup', async (route) => {
    if (!backendReady) {
      // A little realistic latency so the transient "checking" UI is actually
      // observable - an instantly-resolving mock could settle before Playwright's
      // first assertion poll ever sees it.
      await new Promise((resolve) => setTimeout(resolve, 400));
    }
    await route.fulfill(
      backendReady
        ? { status: 200, body: JSON.stringify({ status: 'UP' }) }
        : { status: 503, body: '{}' },
    );
  });

  await page.goto('/login');

  const signInButton = page.locator('form').getByRole('button', { name: 'Waking up…' });
  await expect(signInButton).toBeVisible();
  await expect(signInButton).toBeDisabled();
  await expect(page.getByText('Waking up the demo servers', { exact: false })).toBeVisible();

  // No auto-retry loop by design - see use-backend-warmup.ts's doc comment on why a
  // fixed interval isn't safe against Render's own anti-abuse rate limiting. A single
  // failed attempt should surface a retry affordance instead of silently trying again.
  const retryButton = page.getByRole('button', { name: 'Try again' });
  await expect(retryButton).toBeVisible();
  await expect(page.getByText("Couldn't reach the demo servers")).toBeVisible();
  await expect(signInButton).toBeDisabled();

  backendReady = true;
  await retryButton.click();

  await expect(page.locator('form').getByRole('button', { name: 'Sign in' })).toBeEnabled();

  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password').fill(password);
  await page.locator('form').getByRole('button', { name: 'Sign in' }).click();

  await expect(page).toHaveURL(/\/dashboard/);
});
