import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: './e2e',
  workers: 1,
  retries: 0,
  use: { baseURL: process.env.MCR_BASE_URL ?? 'http://127.0.0.1:5174',
    browserName: 'chromium', channel: 'chrome', viewport: { width: 1440, height: 1000 } },
});
