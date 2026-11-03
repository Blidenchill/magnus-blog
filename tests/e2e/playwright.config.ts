import { defineConfig, devices } from '@playwright/test';

/**
 * E2E runs against a locally booted app (local profile) with a throwaway database.
 * Uses a dedicated test admin account and local image fixtures only —
 * never real passwords or private photos (per docs/technical-design.md §10.1).
 *
 * Prerequisites:
 *   1. Local PostgreSQL with database `magnus` (user magnus / password magnus)
 *   2. App running: `mvn spring-boot:run -Dspring-boot.run.profiles=local`
 *      with LOCAL_ADMIN_PASSWORD_HASH set to a hash of E2E_ADMIN_PASSWORD.
 */
export default defineConfig({
  testDir: './specs',
  timeout: 30_000,
  retries: process.env.CI ? 1 : 0,
  workers: 1, // single admin account: serial to avoid login rate limiting
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: process.env.E2E_BASE_URL || 'http://localhost:8080',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
  ],
});
