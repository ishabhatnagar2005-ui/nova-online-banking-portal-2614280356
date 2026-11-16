import { defineConfig } from '@playwright/test';

// The suite resets demo data and changes simulation settings. Explicitly attest
// the entire target environment (including its database) is disposable.
if (process.env['E2E_DISPOSABLE'] !== '1') throw new Error('Set E2E_DISPOSABLE=1 only for an isolated disposable environment; this suite resets data.');
const reuse = process.env['E2E_REUSE_SERVER'] === '1';
if (reuse && !process.env['E2E_BASE_URL']) throw new Error('E2E_REUSE_SERVER=1 requires an explicit E2E_BASE_URL for the disposable server.');
if (!reuse && !/^e2e-[a-z0-9][a-z0-9_-]*$/.test(process.env['COMPOSE_PROJECT_NAME'] || '')) throw new Error('Use a dedicated COMPOSE_PROJECT_NAME starting with e2e- for the disposable Compose volume.');
if (!reuse && process.env['E2E_BASE_URL']) throw new Error('E2E_BASE_URL requires explicit E2E_REUSE_SERVER=1.');
const baseURL = process.env['E2E_BASE_URL'] || `http://127.0.0.1:${process.env['FRONTEND_PORT'] || '8080'}`;
export default defineConfig({
  testDir: './e2e',
  workers: 1,
  use: { baseURL, screenshot: 'only-on-failure', trace: 'retain-on-failure' },
  // Explicit reuse never starts Compose if the external target is absent.
  webServer: reuse ? undefined : {
    command: 'docker compose -f ../docker-compose.yml up --build',
    url: new URL('/api/health', baseURL).toString(),
    reuseExistingServer: false,
    timeout: 240_000,
  },
});
