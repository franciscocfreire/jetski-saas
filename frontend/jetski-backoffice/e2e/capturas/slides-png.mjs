/**
 * Exporta cada slide do deck como PNG 1920x1080, para virar quadro de vídeo.
 *
 * O deck desenha o slide em 1920x1080 e escala por CSS conforme a janela; com
 * o viewport exatamente nessa medida a escala é 1 e o PNG sai pixel a pixel.
 *
 * Esconde a ajuda de teclado e o painel de narração — eles existem para quem
 * apresenta ao vivo e não podem aparecer no vídeo.
 *
 * Uso: node e2e/capturas/slides-png.mjs
 */

import { chromium } from '@playwright/test';
import * as fs from 'node:fs';
import * as path from 'node:path';
import { fileURLToPath } from 'node:url';

const AQUI = path.dirname(fileURLToPath(import.meta.url));
const RAIZ = path.resolve(AQUI, '../../../..');
const DECK = path.join(RAIZ, 'apresentacao/deck.html');
const SAIDA = path.join(RAIZ, 'apresentacao/video/slides');

const browser = await chromium.launch();
const ctx = await browser.newContext({
  viewport: { width: 1920, height: 1080 },
  deviceScaleFactor: 1,
  colorScheme: 'dark',
});
const page = await ctx.newPage();

fs.mkdirSync(SAIDA, { recursive: true });

await page.goto('file://' + DECK);
await page.addStyleTag({ content: '#ajuda,#notas{display:none!important}' });
await page.waitForTimeout(2500); // fontes do Google

const total = await page.locator('.slide').count();
for (let i = 0; i < total; i++) {
  const n = await page.locator('.slide').nth(i).getAttribute('data-n');
  await page.evaluate((k) => {
    document.querySelectorAll('.slide').forEach((s, j) => s.classList.toggle('ativo', j === k));
  }, i);
  await page.waitForTimeout(400);
  const arq = path.join(SAIDA, `${n}.png`);
  await page.screenshot({ path: arq });
  process.stdout.write(`${n} `);
}
console.log(`\n${total} slides em ${SAIDA}`);

await browser.close();
