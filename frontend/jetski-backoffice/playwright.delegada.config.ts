import { defineConfig, devices } from '@playwright/test';
import dotenv from 'dotenv';
import path from 'path';

/**
 * E2E da emissão delegada (EAMA emissora × operadora) — config PRÓPRIA, de propósito:
 *  - sem o global-setup geral (que cria um tenant avulso e tem produção como default);
 *  - sem baseURL default: a guarda em e2e/delegada/helpers/ambiente.ts recusa rodar sem
 *    PLAYWRIGHT_BASE_URL explícito e recusa qualquer *.meujet.com.br;
 *  - serial e com 1 worker: a jornada é uma só, com passos dependentes.
 *
 * Rodar (dev): PLAYWRIGHT_BASE_URL=https://app.pegaojet.com.br npm run test:e2e:delegada
 * Plano: e2e/EMISSAO_DELEGADA_E2E_PLANO.md
 */
dotenv.config({ path: path.resolve(__dirname, '.env.e2e.local') });

export default defineConfig({
  testDir: './e2e/delegada',
  testMatch: /.*\.spec\.ts/,
  globalSetup: './e2e/delegada/global-setup.ts',

  // A jornada inclui dois logins, SMTP real (Mailpit) e envio assíncrono.
  timeout: 5 * 60 * 1000,
  expect: { timeout: 20 * 1000 },

  fullyParallel: false,
  workers: 1,
  // Retry refaz a jornada inteira com empresas novas; no dev local, falhar logo é mais útil.
  retries: process.env.CI ? 1 : 0,
  forbidOnly: !!process.env.CI,

  reporter: [
    ['html', { outputFolder: 'playwright-report-delegada', open: 'never' }],
    ['list'],
  ],

  use: {
    baseURL: process.env.PLAYWRIGHT_BASE_URL,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
    ignoreHTTPSErrors: true,
    locale: 'pt-BR',
    timezoneId: 'America/Sao_Paulo',
  },

  projects: [
    {
      name: 'delegada-chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
});
