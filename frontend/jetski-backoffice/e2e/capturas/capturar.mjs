/**
 * Capturas da apresentação à Capitania — harness de screenshot.
 *
 * Abre o backoffice de dev já logado, conduz a captura slide a slide e salva
 * cada PNG com o mesmo enquadramento (1920×1080 @2x = 3840×2160), o mesmo
 * recorte e o nome certo. O objetivo é que as vinte telas pareçam ter sido
 * tiradas no mesmo instante — o que, passando em sequência num projetor, é a
 * diferença entre uma apresentação e um álbum de prints.
 *
 * O balcão tem três portões que existem de propósito e que NÃO dá para
 * automatizar sem descaracterizar a demonstração: a foto do documento pela
 * câmera, o fim obrigatório da videoaula e a assinatura no traço. Nesses
 * pontos o script para, diz o que fazer no navegador e espera ENTER — quem
 * opera é você, quem fotografa é ele.
 *
 * Antes de rodar:  ../../seed-apresentacao.sh   (persona + rearme da demo)
 *
 * Uso:
 *   node e2e/capturas/capturar.mjs                 # tudo, na ordem dos slides
 *   node e2e/capturas/capturar.mjs 13 17           # só os slides 13 e 17
 *   BASE_URL=... EMAIL=... SENHA=... node e2e/capturas/capturar.mjs
 */

import { chromium } from '@playwright/test';
import * as fs from 'node:fs';
import * as path from 'node:path';
import * as readline from 'node:readline/promises';
import { fileURLToPath } from 'node:url';

const AQUI = path.dirname(fileURLToPath(import.meta.url));

const BASE_URL = process.env.BASE_URL || 'https://app.pegaojet.com.br';
const MAILPIT_URL = process.env.MAILPIT_URL || 'http://localhost:8025';
const EMAIL = process.env.EMAIL || 'operador@acme.com';
const SENHA = process.env.SENHA || 'operador123';
const SAIDA = process.env.SAIDA || path.resolve(AQUI, '../../../../capturas-apresentacao');

const AUTO = process.env.AUTO === '1';
const ESPERA_AUTO = Number(process.env.ESPERA_AUTO || 2500);

const VERDE = '\x1b[0;32m', AMARELO = '\x1b[1;33m', AZUL = '\x1b[0;34m', CINZA = '\x1b[0;90m', OFF = '\x1b[0m';

/**
 * As telas a capturar, na ordem do roteiro. `manual` marca o que depende de
 * você mexer no navegador antes do clique da foto.
 */
const CENAS = [
  { slide: '07', arquivo: 'balcao-oito-passos',      url: '/dashboard/balcao',
    instrucao: 'Balcão no primeiro passo, indicador de passos visível no topo. Confira "Juliana Prado Loureiro" no canto.' },

  { slide: '08a', arquivo: 'passo-cliente',
    instrucao: 'Passo Cliente com a ficha de HELENA ANDRADE VASCONCELOS preenchida (busque pelo CPF 847.215.903-50).' },
  { slide: '08b', arquivo: 'passo-passeio',
    instrucao: 'Passo Passeio & Preço com o modelo e a duração escolhidos, valor calculado à vista.' },

  { slide: '09',  arquivo: 'habilitacao-bifurcacao',
    instrucao: 'Passo Habilitação com as DUAS opções visíveis ("Sim, tem CHA" / "Não tem"), com "Não tem" selecionado.' },

  { slide: '10a', arquivo: 'gru-gerada',
    instrucao: 'Guia gerada: número 80893100047512026 e o código PIX na tela. O número precisa estar legível.' },
  { slide: '10b', arquivo: 'gru-paga',
    instrucao: 'Depois de "Verificar pagamento": guia confirmada e comprovante anexado.' },

  { slide: '11',  arquivo: 'documento-identidade', manual: true,
    instrucao: 'Passo Documentos: suba apresentacao/insumos/documento-amostra.png e deixe a miniatura visível.' },

  { slide: '12a', arquivo: 'videoaula-bloqueada', manual: true,
    instrucao: 'Passo Orientações com a videoaula RODANDO e o botão Continuar ainda desabilitado.' },
  { slide: '12b', arquivo: 'videoaula-liberada', manual: true,
    instrucao: 'Mesmo passo, vídeo terminado e confirmação do operador marcada — botão Continuar habilitado.' },

  { slide: '13',  arquivo: 'anexos-normam',
    instrucao: 'Prévia do documento aberta. Role até enquadrar os anexos 1-C / 5-C / 5-B-1 / 5-B-2 (uma foto por anexo, repita esta cena).' },

  { slide: '14a', arquivo: 'assinatura', manual: true,
    instrucao: 'Passo Termos: declarações marcadas e a assinatura já traçada no quadro.' },
  { slide: '14b', arquivo: 'codigo-confirmacao', manual: true,
    instrucao: 'Campo do código de 6 dígitos preenchido (o código chega no Mailpit).' },

  { slide: '15',  arquivo: 'pagina-auditoria',
    instrucao: 'Página de auditoria do PDF: data/hora, IP, dispositivo, carimbo de tempo e o hash.' },

  { slide: '16',  arquivo: 'emissao-resultado',
    instrucao: 'Passo Emissão concluído: documento emitido, envios confirmados.' },

  { slide: '17',  arquivo: 'oficio-recebido', url: MAILPIT_URL,
    instrucao: 'Abra a mensagem mais recente: assunto, corpo do ofício, anexos e o arquivo "HELENA ... .pdf".' },

  { slide: '18',  arquivo: 'documento-no-celular', manual: true,
    instrucao: 'Esta é foto de celular, não de tela — pule aqui (ENTER) e fotografe o aparelho à parte.' , pular: true },

  { slide: '19',  arquivo: 'lista-emissoes', url: '/dashboard/documentos',
    instrucao: 'Lista de emissões com várias linhas. Se houver só uma, rode o seed e emita de novo antes.' },
];

const pergunta = readline.createInterface({ input: process.stdin, output: process.stdout });

/**
 * Espera o ENTER. Com AUTO=1 (ou stdin fechado, caso de fumaça/CI) não espera
 * ninguém: mostra a instrução, dá um respiro para a tela assentar e fotografa.
 */
async function esperarEnter(texto) {
  if (AUTO) {
    console.log(`${AMARELO}${texto}${OFF}`);
    await new Promise((r) => setTimeout(r, ESPERA_AUTO));
    return '';
  }
  try {
    return await pergunta.question(
      `${AMARELO}${texto}${OFF}\n${CINZA}   ENTER para fotografar · "p" + ENTER para pular${OFF} `);
  } catch {
    return ''; // stdin fechado — fotografa e segue
  }
}

async function login(page) {
  console.log(`${AZUL}Entrando como ${EMAIL}…${OFF}`);
  await page.goto(`${BASE_URL}/login`);

  const noKeycloak = /\/realms\/.*\/protocol\/openid-connect\/auth/;
  const chegou = await page.waitForURL(noKeycloak, { timeout: 15000 }).then(() => true).catch(() => false);
  if (!chegou) {
    await page.locator('button:has-text("Entrar")').first().click();
    await page.waitForURL(noKeycloak, { timeout: 15000 });
  }

  // Tema meujet: identificação primeiro (e-mail → Continuar → senha).
  const identifier = page.locator('#identifier');
  const temaMeujet = await identifier.waitFor({ state: 'visible', timeout: 5000 }).then(() => true).catch(() => false);
  if (temaMeujet) {
    await identifier.fill(EMAIL);
    await page.click('#mj-send-code');
    await page.fill('#password', SENHA);
    await page.click('#mj-login-password');
  } else {
    await page.fill('#username', EMAIL);
    await page.fill('#password', SENHA);
    await page.click('#kc-login');
  }

  await page.waitForURL((u) => !u.href.includes('/realms/'), { timeout: 30000 });
  console.log(`${VERDE}   Sessão aberta.${OFF}\n`);
}

async function main() {
  const filtro = process.argv.slice(2);
  const cenas = filtro.length
    ? CENAS.filter((c) => filtro.some((f) => c.slide.startsWith(f)))
    : CENAS;

  if (!cenas.length) {
    console.log('Nenhuma cena bate com o filtro. Slides disponíveis:',
      CENAS.map((c) => c.slide).join(', '));
    process.exit(1);
  }

  fs.mkdirSync(SAIDA, { recursive: true });
  console.log(`${AZUL}=== Capturas da apresentação ===${OFF}`);
  console.log(`${CINZA}saída: ${SAIDA}${OFF}\n`);

  const browser = await chromium.launch({
    headless: false,
    args: [
      // Câmera falsa: o passo Documentos abre getUserMedia e, sem isto, o
      // navegador pede permissão de um dispositivo que não existe no WSL.
      '--use-fake-ui-for-media-stream',
      '--use-fake-device-for-media-stream',
      '--window-position=0,0',
    ],
  });

  const context = await browser.newContext({
    viewport: { width: 1920, height: 1080 },
    deviceScaleFactor: 2,        // 3840×2160: sobra resolução para projetor
    locale: 'pt-BR',
    timezoneId: 'America/Sao_Paulo',
    permissions: ['camera'],
    ignoreHTTPSErrors: true,
    colorScheme: 'light',        // o backoffice do vídeo é claro, sempre
  });

  const page = await context.newPage();
  await login(page);

  const feitas = [];
  for (const cena of cenas) {
    const alvo = cena.url
      ? (cena.url.startsWith('http') ? cena.url : `${BASE_URL}${cena.url}`)
      : null;
    if (alvo) {
      await page.goto(alvo).catch(() => {});
      await page.waitForLoadState('networkidle', { timeout: 15000 }).catch(() => {});
    }

    console.log(`${AZUL}slide ${cena.slide}${OFF} — ${cena.arquivo}`);
    if (cena.pular) {
      console.log(`${CINZA}   ${cena.instrucao}${OFF}\n`);
      continue;
    }

    const resposta = await esperarEnter(`   ${cena.instrucao}`);
    if (String(resposta).trim().toLowerCase() === 'p') {
      console.log(`${CINZA}   pulado${OFF}\n`);
      continue;
    }

    const arquivo = path.join(SAIDA, `${cena.slide}-${cena.arquivo}.png`);
    await page.screenshot({ path: arquivo });
    feitas.push(arquivo);
    console.log(`${VERDE}   ✓ ${path.basename(arquivo)}${OFF}\n`);
  }

  console.log(`${AZUL}=== ${feitas.length} captura(s) ===${OFF}`);
  for (const f of feitas) console.log(`   ${f}`);
  console.log(`\n${AMARELO}Confira antes de montar:${OFF} nenhum "ACME", nenhum "example.com",`);
  console.log(`${AMARELO}nenhum dado real, e o nome "Juliana Prado Loureiro" no canto de todas.${OFF}`);

  pergunta.close();
  await browser.close();
}

main().catch((e) => {
  console.error(e);
  pergunta.close();
  process.exit(1);
});
