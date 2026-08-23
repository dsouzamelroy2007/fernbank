import { defineConfig, devices } from '@playwright/test';

/**
 * These e2e tests exercise the real login -> dashboard -> transfer flow against a real
 * Spring Boot backend + Postgres, not mocks - `docker compose up` (see
 * infra/docker-compose.yml) must already be running before `npm run test:e2e`, same
 * precondition scripts/smoke-test-web.sh documents. WEB_BASE_URL/BACKEND_BASE_URL
 * override the defaults if the stack's ports were remapped locally (infra/.env).
 */
const baseURL = process.env.WEB_BASE_URL ?? 'http://localhost:3000';

export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: 'list',
  use: {
    baseURL,
    trace: 'on-first-retry',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: {
    command: 'npm run dev',
    url: baseURL,
    reuseExistingServer: true,
    timeout: 60_000,
  },
});
