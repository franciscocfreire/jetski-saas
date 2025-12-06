import { defineConfig, devices } from '@playwright/test';
import dotenv from 'dotenv';
import path from 'path';

// Carregar variáveis de ambiente para testes
dotenv.config({ path: path.resolve(__dirname, '.env.e2e.local') });

export default defineConfig({
  testDir: './e2e/tests',

  // Global setup/teardown
  globalSetup: './e2e/global-setup.ts',
  globalTeardown: './e2e/global-teardown.ts',

  // Timeout global para cada teste
  timeout: 60 * 1000,

  // Timeout para expect()
  expect: {
    timeout: 10 * 1000,
  },

  // Execução completa em CI
  fullyParallel: true,

  // Falhar o build se deixar test.only no código
  forbidOnly: !!process.env.CI,

  // Retries em caso de falha (1 local, 2 in CI - helps with flaky auth)
  retries: process.env.CI ? 2 : 1,

  // Workers paralelos (limit to 2 locally to reduce Keycloak concurrent auth issues)
  workers: process.env.CI ? 1 : 2,

  // Reporter
  reporter: [
    ['html', { outputFolder: 'playwright-report' }],
    ['list'],
  ],

  // Configurações globais
  use: {
    // Base URL do ambiente (produção)
    baseURL: process.env.PLAYWRIGHT_BASE_URL || 'https://pegaojet.com.br',

    // Coletar trace em caso de falha
    trace: 'on-first-retry',

    // Screenshot em caso de falha
    screenshot: 'only-on-failure',

    // Video em caso de falha
    video: 'on-first-retry',

    // Ignorar erros HTTPS (útil para ambientes de teste)
    ignoreHTTPSErrors: true,
  },

  // Projetos/browsers
  projects: [
    // Chrome - all tests (auth handled by fixture when needed)
    {
      name: 'chromium',
      use: {
        ...devices['Desktop Chrome'],
      },
    },
  ],
});
