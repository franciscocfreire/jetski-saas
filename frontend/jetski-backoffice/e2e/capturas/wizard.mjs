/**
 * Percurso automático do balcão para as capturas da apresentação.
 *
 * Conduz o atendimento inteiro pelos `data-testid` dos passos e fotografa nos
 * pontos do roteiro. Antes disto o percurso era manual — e o modo de falha era
 * o pior possível: se o passo não avançasse, o script fotografava a tela
 * anterior em silêncio (foi assim que saíram 14 PNGs idênticos).
 *
 * Os três "portões" do balcão não são obstáculo real:
 *   - câmera → o upload tem <input type=file>, e setInputFiles não abre diálogo;
 *   - assinatura → traço com pointer sobre o canvas;
 *   - código OTP → lido pela API do Mailpit.
 * O único que resiste é a VIDEOAULA: 10min57s sem poder adiantar. Para passar,
 * usamos o caminho de degradação do próprio produto — bloqueando o YouTube, o
 * player acusa erro e a tela oferece a confirmação manual (`modoFallback`).
 * Por isso este modo NÃO captura os slides 12a/12b: aquelas duas telas só
 * valem vindas de um player de verdade, e já estão capturadas.
 *
 * Uso:
 *   node e2e/capturas/wizard.mjs              # percurso completo
 *   node e2e/capturas/wizard.mjs --so=10b,13  # só estes alvos
 */

import { chromium } from '@playwright/test';
import * as crypto from 'node:crypto';
import * as fs from 'node:fs';
import * as path from 'node:path';
import { fileURLToPath } from 'node:url';

const AQUI = path.dirname(fileURLToPath(import.meta.url));
const RAIZ = path.resolve(AQUI, '../../../..');

const BASE_URL = process.env.BASE_URL || 'https://app.pegaojet.com.br';
const MAILPIT_URL = process.env.MAILPIT_URL || 'http://localhost:8025';
const EMAIL = process.env.EMAIL || 'operador@acme.com';
const SENHA = process.env.SENHA || 'operador123';
const SAIDA = process.env.SAIDA || path.join(RAIZ, 'capturas-apresentacao');
const AMOSTRA = process.env.AMOSTRA || path.join(RAIZ, 'apresentacao/insumos/documento-amostra.png');

const CPF = process.env.CPF || '847.215.903-50';
const HEADLESS = process.env.HEADLESS === '1';

const JANELA_W = Number(process.env.JANELA_W || 1600);
const JANELA_H = Math.round(JANELA_W * 9 / 16);
const ALVO_W = Number(process.env.ALVO_W || 3840);
const ESCALA = ALVO_W / JANELA_W;

const V = '\x1b[0;32m', A = '\x1b[1;33m', Z = '\x1b[0;34m', C = '\x1b[0;90m', R = '\x1b[0;31m', O = '\x1b[0m';

const so = (process.argv.find((a) => a.startsWith('--so=')) || '').slice(5)
  .split(',').map((s) => s.trim()).filter(Boolean);

let ultimoHash = null;
let feitas = [];
/** Refs de módulo só para a foto de diagnóstico quando algo falha. */
let _ctx = null;

/** Fotografa a aba da frente (a prévia do PDF abre em aba nova). */
async function shot(ctx, page, slide, nome) {
  if (so.length && !so.includes(slide)) return;
  const abas = ctx.pages().filter((p) => !p.isClosed());
  const alvo = abas.at(-1) || page;
  if (alvo !== page) await alvo.bringToFront().catch(() => {});
  await alvo.waitForTimeout(600);

  const png = await alvo.screenshot();
  const hash = crypto.createHash('md5').update(png).digest('hex');
  if (hash === ultimoHash) {
    console.log(`${R}   ✗ ${slide}: a tela não mudou desde a captura anterior — não salvei${O}`);
    return;
  }
  // Página em branco: um PNG de 3840x2160 quase vazio comprime para poucos KB.
  // O Chromium HEADLESS não renderiza o visualizador de PDF, então a prévia sai
  // branca — e o guarda-corpo de hash não pega, porque a captura anterior era
  // outra tela. Sem esta checagem, um slide branco chegaria à montagem.
  if (png.length < 60_000) {
    console.log(`${R}   ✗ ${slide}: tela praticamente em branco (${Math.round(png.length / 1024)} KB)${O}`);
    if (HEADLESS) console.log(`${A}      PDF não renderiza em headless — rode sem HEADLESS=1${O}`);
    return;
  }
  const arq = path.join(SAIDA, `${slide}-${nome}.png`);
  fs.writeFileSync(arq, png);
  ultimoHash = hash;
  feitas.push(path.basename(arq));
  console.log(`${V}   ✓ ${path.basename(arq)}${O}`);
}

const tid = (page, id) => page.getByTestId(id);

/**
 * Marca um checkbox CONFERINDO o resultado. Um .click() simples não serve: os
 * checkboxes do balcão ficam dentro de <label>, então o clique dispara no
 * controle E no rótulo — dois toggles que se anulam, deixando a caixa
 * desmarcada e o botão de avançar travado, sem erro nenhum na tela.
 */
async function marcar(page, id) {
  const el = tid(page, id);
  await el.waitFor({ timeout: 20000 });
  for (let i = 0; i < 4; i++) {
    if ((await el.getAttribute('data-state').catch(() => null)) === 'checked') return;
    if ((await el.getAttribute('aria-checked').catch(() => null)) === 'true') return;
    await el.click({ force: true });
    await page.waitForTimeout(350);
  }
  throw new Error(`não consegui marcar ${id} (segue desmarcado após 4 tentativas)`);
}

/**
 * Espera um locator ficar HABILITADO. isEnabled()/isVisible() do Playwright
 * checam na hora e ignoram o timeout que se passa a eles — foi assim que o
 * passo Pagamento foi "pulado" com o botão na tela, e que a verificação do PIX
 * era dada como falha antes mesmo de a resposta chegar.
 */
async function esperarHabilitado(loc, ms = 20000) {
  const fim = Date.now() + ms;
  while (Date.now() < fim) {
    if (await loc.isEnabled().catch(() => false)) return true;
    await new Promise((r) => setTimeout(r, 400));
  }
  return false;
}

/** Visível de verdade (com espera), sem confiar no isVisible imediato. */
async function esperarVisivel(loc, ms = 20000) {
  return loc.waitFor({ state: 'visible', timeout: ms }).then(() => true).catch(() => false);
}

async function login(page) {
  console.log(`${Z}Entrando como ${EMAIL}…${O}`);
  await page.goto(`${BASE_URL}/login`);
  const kc = /\/realms\/.*\/protocol\/openid-connect\/auth/;
  if (!(await page.waitForURL(kc, { timeout: 15000 }).then(() => true).catch(() => false))) {
    await page.locator('button:has-text("Entrar")').first().click();
    await page.waitForURL(kc, { timeout: 15000 });
  }
  const ident = page.locator('#identifier');
  if (await ident.waitFor({ state: 'visible', timeout: 5000 }).then(() => true).catch(() => false)) {
    await ident.fill(EMAIL);
    await page.click('#mj-send-code');
    await page.fill('#password', SENHA);
    await page.click('#mj-login-password');
  } else {
    await page.fill('#username', EMAIL);
    await page.fill('#password', SENHA);
    await page.click('#kc-login');
  }
  await page.waitForURL((u) => !u.href.includes('/realms/'), { timeout: 30000 });
  console.log(`${V}   sessão aberta${O}\n`);
}

/** Radix Select: abre o gatilho e escolhe a opção (por índice ou por texto). */
async function escolher(page, testId, { indice = 0, texto } = {}) {
  await tid(page, testId).click();
  const opcoes = page.locator('[role="option"]');
  await opcoes.first().waitFor({ state: 'visible', timeout: 10000 });
  await (texto ? opcoes.filter({ hasText: texto }).first() : opcoes.nth(indice)).click();
  await page.waitForTimeout(300);
}

/** Traço de assinatura sobre o canvas. */
async function assinar(page) {
  const canvas = tid(page, 'balcao-termos-assinatura');
  const box = await canvas.boundingBox();
  await page.mouse.move(box.x + box.width * 0.2, box.y + box.height * 0.6);
  await page.mouse.down();
  for (const [dx, dy] of [[.35, .3], [.5, .7], [.65, .35], [.8, .55]]) {
    await page.mouse.move(box.x + box.width * dx, box.y + box.height * dy, { steps: 8 });
  }
  await page.mouse.up();
  await page.waitForTimeout(400);
}

/** Último código de 6 dígitos que chegou ao Mailpit. */
async function codigoDoMailpit() {
  const r = await fetch(`${MAILPIT_URL}/api/v1/messages?limit=5`).then((x) => x.json());
  for (const m of r.messages || []) {
    const txt = await fetch(`${MAILPIT_URL}/api/v1/message/${m.ID}`).then((x) => x.json());
    const achado = `${txt.Text || ''} ${txt.HTML || ''}`.match(/\b(\d{6})\b/);
    if (achado) return achado[1];
  }
  return null;
}

async function main() {
  fs.mkdirSync(SAIDA, { recursive: true });
  if (!fs.existsSync(AMOSTRA)) {
    console.log(`${R}Falta a amostra de identidade: ${AMOSTRA}${O}`);
    process.exit(1);
  }
  console.log(`${Z}=== Percurso automático do balcão ===${O}`);
  console.log(`${C}saída: ${SAIDA} · janela ${JANELA_W}x${JANELA_H} · PNG ${ALVO_W}x${Math.round(ALVO_W * 9 / 16)}${O}\n`);

  const browser = await chromium.launch({
    headless: HEADLESS,
    args: ['--use-fake-ui-for-media-stream', '--use-fake-device-for-media-stream',
           `--window-size=${JANELA_W},${JANELA_H + 132}`, '--window-position=30,20'],
  });
  const ctx = await browser.newContext({
    viewport: { width: JANELA_W, height: JANELA_H },
    deviceScaleFactor: ESCALA,
    locale: 'pt-BR', timezoneId: 'America/Sao_Paulo',
    permissions: ['camera'], ignoreHTTPSErrors: true, colorScheme: 'light',
  });

  // Videoaula: sem o player, a tela cai no registro manual (caminho do produto).
  await ctx.route('**://*.youtube.com/**', (r) => r.abort());
  await ctx.route('**://*.ytimg.com/**', (r) => r.abort());

  _ctx = ctx;
  const page = await ctx.newPage();
  await login(page);

  const passo = async (rotulo, fn) => {
    console.log(`${Z}${rotulo}${O}`);
    await fn();
  };

  await passo('passo 1 — Cliente', async () => {
    await page.goto(`${BASE_URL}/dashboard/balcao`);
    const novo = page.getByRole('button', { name: /Novo atendimento/i });
    if (await novo.isVisible().catch(() => false)) await novo.click();
    await tid(page, 'balcao-cliente-documento').waitFor({ timeout: 20000 });
    await shot(ctx, page, '07', 'balcao-oito-passos');

    await tid(page, 'balcao-cliente-documento').fill(CPF);

    // A busca é uma mutation: pode demorar (consulta à Marinha no caminho de
    // "não achou") e pode falhar em silêncio. Tenta de novo antes de desistir,
    // e distingue "não encontrado" de "não respondeu".
    const achado = tid(page, 'balcao-cliente-usar');
    const formNovo = tid(page, 'balcao-cliente-nome');
    let ok = false;
    for (let i = 0; i < 3 && !ok; i++) {
      await tid(page, 'balcao-cliente-buscar').click();
      ok = await Promise.race([
        achado.waitFor({ timeout: 25000 }).then(() => 'achado'),
        formNovo.waitFor({ timeout: 25000 }).then(() => 'novo'),
      ]).catch(() => false);
      if (ok === 'novo') {
        throw new Error(`nenhum cliente com ${CPF} — rode ./seed-apresentacao.sh antes`);
      }
      if (!ok) console.log(`${A}   busca não respondeu (tentativa ${i + 1}/3)${O}`);
    }
    if (!ok) throw new Error('a busca de cliente não respondeu');
    await shot(ctx, page, '08a', 'passo-cliente');
    await achado.click();
  });

  await passo('passo 2 — Passeio & Preço', async () => {
    await tid(page, 'balcao-aluguel-modelo').waitFor({ timeout: 20000 });
    await escolher(page, 'balcao-aluguel-modelo', { indice: 0 });
    await tid(page, 'balcao-aluguel-duracao-60').click();
    await shot(ctx, page, '08b', 'passo-passeio');
    await tid(page, 'balcao-aluguel-continuar').click();
  });

  await passo('passo 3 — Habilitação e GRU', async () => {
    await tid(page, 'balcao-hab-sem-cha').waitFor({ timeout: 20000 });
    await tid(page, 'balcao-hab-sem-cha').click();
    await shot(ctx, page, '09', 'habilitacao-bifurcacao');

    await tid(page, 'balcao-hab-gerar-pix').click();
    await tid(page, 'balcao-hab-verificar-pix').waitFor({ timeout: 60000 });
    await shot(ctx, page, '10a', 'gru-gerada');

    await tid(page, 'balcao-hab-verificar-pix').click();
    const avancar = tid(page, 'balcao-hab-avancar');
    const pago = await esperarHabilitado(avancar, 20000);
    if (!pago) {
      // A verificação não confirmou (GRU real não paga): usa o caminho do
      // comprovante manual em vez de fotografar o aviso de "não identificado".
      console.log(`${A}   PIX não confirmado — anexando comprovante${O}`);
      await tid(page, 'balcao-hab-outro-meio').click();
      await tid(page, 'balcao-hab-comprovante').setInputFiles(AMOSTRA);
      await tid(page, 'balcao-hab-confirmar-comprovante').click();
      await avancar.waitFor({ timeout: 30000 });
    }
    await page.waitForTimeout(1200);
    await shot(ctx, page, '10b', 'gru-paga');
    await avancar.click();
  });

  await passo('passo 4 — Documentos', async () => {
    await tid(page, 'balcao-doc-identidade').waitFor({ state: 'attached', timeout: 20000 });
    await tid(page, 'balcao-doc-identidade').setInputFiles(AMOSTRA);
    await tid(page, 'balcao-doc-selfie').setInputFiles(AMOSTRA);
    await page.waitForTimeout(1500);
    await tid(page, 'balcao-doc-sem-comprovante').click();
    await shot(ctx, page, '11', 'documento-identidade');
    await tid(page, 'balcao-doc-avancar').click();
  });

  await passo('passo 5 — Orientações (fallback do player)', async () => {
    await marcar(page, 'balcao-orient-confirmar');
    console.log(`${C}   12a/12b não são capturados aqui: aquelas telas precisam do player real${O}`);
    await tid(page, 'balcao-orient-continuar').click();
  });

  await passo('passo 6 — Termos e assinatura', async () => {
    await marcar(page, 'balcao-termos-ciencia');
    await assinar(page);
    await shot(ctx, page, '14a', 'assinatura');

    const enviarCod = tid(page, 'balcao-termos-enviar-codigo');
    if (await esperarVisivel(enviarCod, 4000)) {
      await enviarCod.click();
      await page.waitForTimeout(2500);
      const codigo = await codigoDoMailpit();
      if (codigo) {
        await tid(page, 'balcao-termos-codigo').fill(codigo);
        await shot(ctx, page, '14b', 'codigo-confirmacao');
        await tid(page, 'balcao-termos-verificar-codigo').click();
        await page.waitForTimeout(1500);
      } else {
        console.log(`${A}   código não encontrado no Mailpit — seguindo sem OTP${O}`);
      }
    }
    const assinarAvancar = tid(page, 'balcao-termos-assinar-avancar');
    const alvo = (await esperarVisivel(assinarAvancar, 4000))
      ? assinarAvancar : tid(page, 'balcao-termos-avancar');
    if (!(await esperarHabilitado(alvo, 20000))) {
      throw new Error('botão de avançar dos Termos continuou desabilitado');
    }
    await alvo.click();
  });

  await passo('passo 7 — Pagamento', async () => {
    const registrar = tid(page, 'balcao-pag-registrar');
    const continuar = tid(page, 'balcao-pag-continuar');
    if (await esperarVisivel(registrar, 20000)) {
      await registrar.click();
    } else if (await esperarVisivel(continuar, 5000)) {
      await continuar.click();   // pagamento já registrado neste atendimento
    } else {
      throw new Error('passo Pagamento não ofereceu nem registrar nem continuar');
    }
    await page.waitForTimeout(2000);
  });

  await passo('passo 8 — Emissão', async () => {
    await tid(page, 'balcao-emissao-emitir').waitFor({ timeout: 25000 });
    await escolher(page, 'balcao-emissao-instrutor', { indice: 0 }).catch(() => {});

    // Prévia: abre em aba nova; os anexos e a auditoria saem daqui.
    const [previa] = await Promise.all([
      ctx.waitForEvent('page', { timeout: 30000 }).catch(() => null),
      tid(page, 'balcao-emissao-previa-marinha').click(),
    ]);
    if (previa) {
      await previa.waitForLoadState('load').catch(() => {});
      await previa.waitForTimeout(3000);
      // 13 e 15 (páginas do PDF) NÃO saem daqui, e a razão está no visualizador
      // de PDF do Chromium: em headless ele não renderiza nada (página branca);
      // em headed abre com o painel de miniaturas, zoom próprio e a página
      // cortada — legível para conferir, imprestável para projetar. O fragmento
      // #page/#zoom é ignorado em URL de blob, que é como a prévia abre.
      // Essas duas telas continuam no harness manual (capturar.mjs), onde você
      // rola e enquadra. Preferimos não entregar nada a entregar um slide ruim.
      console.log(`${A}   13/15 (páginas do PDF) ficam para o harness manual:${O}`);
      console.log(`${C}      node e2e/capturas/capturar.mjs 13 15${O}`);
      await previa.close();
      await page.bringToFront();
    } else {
      console.log(`${A}   a prévia não abriu em aba nova — 13 e 15 ficam para o modo manual${O}`);
    }

    await tid(page, 'balcao-emissao-emitir').click();
    await tid(page, 'balcao-emissao-abrir-pdf').waitFor({ timeout: 90000 });
    await page.waitForTimeout(1500);
    await shot(ctx, page, '16', 'emissao-resultado');
  });

  await passo('ofício no Mailpit', async () => {
    const mp = await ctx.newPage();
    await mp.goto(MAILPIT_URL);
    await mp.waitForTimeout(2500);
    // A mensagem mais recente é o e-mail AO CLIENTE ("Seus documentos"); o
    // ofício à Capitania é o anterior. Clicar no primeiro da lista trocava o
    // slide mais importante da apresentação por outro parecido.
    const oficio = mp.getByText(/Solicitação de Emissão de CHA-MTA-E/i).first();
    if (!(await esperarVisivel(oficio, 15000))) {
      throw new Error('ofício não apareceu no Mailpit — a emissão enviou à Marinha?');
    }
    await oficio.click();
    await mp.waitForTimeout(2500);
    await shot(ctx, page, '17', 'oficio-recebido');
    await mp.close();
  });

  await passo('lista de emissões', async () => {
    await page.bringToFront();
    await page.goto(`${BASE_URL}/dashboard/documentos`);
    await page.waitForLoadState('networkidle').catch(() => {});
    await page.waitForTimeout(1500);
    await shot(ctx, page, '19', 'lista-emissoes');
  });

  console.log(`\n${Z}=== ${feitas.length} captura(s) ===${O}`);
  feitas.forEach((f) => console.log('   ' + f));
  console.log(`\n${A}12a/12b (videoaula) e 18 (celular) não saem daqui — ver o roteiro.${O}`);
  await browser.close();
}

main().catch(async (e) => {
  console.error(`${R}falhou: ${e.message.split('\n')[0]}${O}`);
  // Foto do estado em que parou: sem ela, diagnosticar é adivinhação.
  try {
    const abas = _ctx?.pages().filter((p) => !p.isClosed()) || [];
    const alvo = abas.at(-1);
    if (alvo) {
      const arq = path.join(SAIDA, '_erro.png');
      await alvo.screenshot({ path: arq });
      console.error(`${A}   estado salvo em ${arq}${O}`);
    }
  } catch { /* nada a fazer */ }
  process.exit(1);
});
