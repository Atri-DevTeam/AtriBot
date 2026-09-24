import { defineConfig, devices } from '@playwright/test'

export default defineConfig({
  testDir: './tests/browser',
  globalSetup: './tests/browser/server.ts',
  use: { baseURL: 'http://127.0.0.1:5174', headless: true },
  projects: [
    { name: 'desktop', use: { ...devices['Desktop Chrome'], channel: process.env.MINIAPP_BROWSER_CHANNEL || 'msedge' } },
    { name: 'mobile', use: { ...devices['iPhone 13'], defaultBrowserType: 'chromium', channel: process.env.MINIAPP_BROWSER_CHANNEL || 'msedge' } }
  ]
})
