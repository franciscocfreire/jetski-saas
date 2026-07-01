import { test as authTest } from '../fixtures/auth';
import { expect } from '@playwright/test';
import { hasCredentials, SKIP_MESSAGE } from '../fixtures/auth-check';

/**
 * Guard-rail de responsividade mobile: garante que as telas operacionais NÃO
 * têm overflow horizontal em viewport de celular (roda no projeto "mobile" =
 * Pixel 5). Conteúdo largo (tabelas, grid mensal) deve rolar DENTRO de um
 * container `overflow-x-auto`, não estourar a página. Se algum ajuste futuro
 * reintroduzir overflow da página, este teste quebra.
 */
const ROTAS_OPERACIONAIS = [
  '/dashboard/balcao',
  '/dashboard/fila',
  '/dashboard/pendencias',
  '/dashboard/clientes',
  '/dashboard/agenda',
  '/dashboard/documentos',
];

authTest.describe('Mobile: sem overflow horizontal (telas operacionais)', () => {
  authTest.skip(!hasCredentials(), SKIP_MESSAGE);

  for (const rota of ROTAS_OPERACIONAIS) {
    authTest(`sem scroll horizontal em ${rota}`, async ({ authenticatedPage: page }) => {
      await page.goto(rota);
      await page.waitForLoadState('networkidle').catch(() => {});
      // dá um tempo pro conteúdo assíncrono (tabelas/queries) renderizar
      await page.waitForTimeout(1500);

      const { scrollW, clientW } = await page.evaluate(() => {
        const el = document.scrollingElement || document.documentElement;
        return { scrollW: el.scrollWidth, clientW: el.clientWidth };
      });

      // tolerância de 2px para arredondamentos de sub-pixel
      expect(
        scrollW,
        `${rota}: overflow horizontal da página (scrollWidth ${scrollW} > clientWidth ${clientW}). ` +
          `Provável tabela/grid/elemento sem wrapper overflow-x-auto ou largura fixa.`
      ).toBeLessThanOrEqual(clientW + 2);
    });
  }
});
