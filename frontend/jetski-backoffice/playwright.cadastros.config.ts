import { defineConfig } from '@playwright/test';
import base from './playwright.delegada.config';

/**
 * E2E de cadastros (modelo, jetski, instrutor) contra o DEV.
 *
 * Herda tudo da config da emissão delegada: guarda de ambiente (sem PLAYWRIGHT_BASE_URL
 * explícito ou com *.meujet.com.br, aborta), global-setup (operador de plataforma do e2e),
 * timeouts de ação/navegação, 1 worker. Muda só a pasta e o relatório.
 *
 * Rodar (dev): PLAYWRIGHT_BASE_URL=https://app.pegaojet.com.br npm run test:e2e:cadastros
 */
export default defineConfig({
  ...base,
  testDir: './e2e/cadastros',
  reporter: [
    ['html', { outputFolder: 'playwright-report-cadastros', open: 'never' }],
    ['list'],
  ],
});
