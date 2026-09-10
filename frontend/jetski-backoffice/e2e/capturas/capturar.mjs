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
import * as crypto from 'node:crypto';
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

/**
 * A janela precisa CABER na sua tela (com a barra do navegador em cima), mas a
 * foto precisa sair grande para o projetor. São duas coisas diferentes: o
 * viewport define o que você opera, e o fator de escala multiplica só o PNG.
 * 1600x900 @2.4 = 3840x2160 — mesma proporção 16:9, janela folgada em 1920x1080.
 */
const JANELA_W = Number(process.env.JANELA_W || 1600);
const JANELA_H = Math.round(JANELA_W * 9 / 16);
const ALVO_W = Number(process.env.ALVO_W || 3840);
const ESCALA = ALVO_W / JANELA_W;

/**
 * WSL sem janela visível? Em vez de abrir um Chrome dentro do Linux (que
 * depende do WSLg aparecer na sua tela), o script se conecta ao Chrome do
 * WINDOWS por depuração remota — você opera o navegador que já usa.
 *
 *   1) feche o Chrome e abra assim, no Windows (cmd ou PowerShell):
 *      "C:\Program Files\Google\Chrome\Application\chrome.exe"
 *        --remote-debugging-port=9222 --user-data-dir=C:\temp\chrome-captura
 *      (o --user-data-dir é obrigatório: sem ele o Chrome só reaproveita a
 *       instância aberta e ignora a porta de depuração)
 *   2) CDP=1 node e2e/capturas/capturar.mjs 08 09 10 ...
 */
const CDP = process.env.CDP;

const AUTO = process.env.AUTO === '1';
const ESPERA_AUTO = Number(process.env.ESPERA_AUTO || 2500);

const VERMELHO = '\x1b[0;31m';
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
    instrucao: 'Guia CONFIRMADA. Se "Verificar pagamento" disser que não identificou, use '
             + '"Paguei por outro meio" e anexe apresentacao/insumos/documento-amostra.png. '
             + 'NUNCA fotografe com o aviso vermelho de pagamento não identificado na tela.' },

  { slide: '11',  arquivo: 'documento-identidade', manual: true,
    instrucao: 'Passo Documentos: suba apresentacao/insumos/documento-amostra.png e deixe a miniatura visível.' },

  { slide: '12a', arquivo: 'videoaula-bloqueada', manual: true,
    instrucao: 'Passo Orientações com a videoaula RODANDO e o botão Continuar ainda desabilitado.' },
  { slide: '12b', arquivo: 'videoaula-liberada', manual: true,
    instrucao: 'Mesmo passo, vídeo terminado e confirmação do operador marcada — botão Continuar habilitado.' },

  { slide: '13',  arquivo: 'anexos-normam',
    instrucao: 'No passo Emissão clique "Prévia Marinha" e espere o PDF abrir na aba nova. '
             + 'Role até enquadrar as PÁGINAS dos anexos (1-C / 5-C / 5-B-1 / 5-B-2) — não a tela do wizard.' },

  { slide: '14a', arquivo: 'assinatura', manual: true,
    instrucao: 'Passo Termos: declarações marcadas e a assinatura já traçada no quadro.' },
  { slide: '14b', arquivo: 'codigo-confirmacao', manual: true,
    instrucao: 'Campo do código de 6 dígitos preenchido (o código chega no Mailpit).' },

  { slide: '15',  arquivo: 'pagina-auditoria',
    instrucao: 'Ainda no PDF aberto: role até a PÁGINA DE AUDITORIA (data/hora, IP, dispositivo, '
             + 'carimbo de tempo e o hash). Depois feche a aba do PDF para seguir.' },

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

/** Endereços onde o Chrome do Windows costuma responder, visto de dentro do WSL. */
function candidatosCdp() {
  if (CDP && CDP !== '1') return [CDP];
  const alvos = ['http://localhost:9222', 'http://127.0.0.1:9222'];
  try {
    // WSL2 sem rede espelhada: o Windows é o nameserver do resolv.conf.
    const ip = fs.readFileSync('/etc/resolv.conf', 'utf8')
      .split('\n').find((l) => l.startsWith('nameserver'))?.split(/\s+/)[1];
    if (ip) alvos.push(`http://${ip}:9222`);
  } catch { /* sem resolv.conf: só os locais */ }
  return alvos;
}

async function conectarCdp() {
  for (const url of candidatosCdp()) {
    try {
      const b = await chromium.connectOverCDP(url, { timeout: 4000 });
      console.log(`${VERDE}Conectado ao Chrome do Windows em ${url}${OFF}`);
      return b;
    } catch { /* tenta o próximo */ }
  }
  console.log(`${VERMELHO}Não achei o Chrome com depuração remota.${OFF}`);
  console.log(`${AMARELO}Abra no Windows (feche o Chrome antes):${OFF}`);
  console.log('  "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe" \\');
  console.log('    --remote-debugging-port=9222 --user-data-dir=C:\\temp\\chrome-captura');
  console.log(`${CINZA}Testados: ${candidatosCdp().join(', ')}${OFF}`);
  process.exit(1);
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
  console.log(`${CINZA}saída: ${SAIDA}${OFF}`);
  console.log(`${CINZA}janela ${JANELA_W}x${JANELA_H} · PNG ${ALVO_W}x${Math.round(ALVO_W*9/16)}`
    + ` (JANELA_W=1280 se a janela não couber)${OFF}\n`);

  let browser, context, page;

  if (CDP) {
    browser = await conectarCdp();
    context = browser.contexts()[0] || (await browser.newContext());
    page = context.pages()[0] || (await context.newPage());
    // O tamanho da janela é o do Chrome do Windows; a resolução do PNG vem do
    // override abaixo, na hora de cada foto.
    console.log(`${CINZA}usando a janela do Chrome do Windows${OFF}\n`);
  } else {
    browser = await chromium.launch({
    headless: false,
    args: [
      // Câmera falsa: o passo Documentos abre getUserMedia e, sem isto, o
      // navegador pede permissão de um dispositivo que não existe no WSL.
      '--use-fake-ui-for-media-stream',
      '--use-fake-device-for-media-stream',
      `--window-size=${JANELA_W},${JANELA_H + 132}`,
      '--window-position=30,20',
    ],
    });

    context = await browser.newContext({
      viewport: { width: JANELA_W, height: JANELA_H },
      deviceScaleFactor: ESCALA, // janela cabe na tela; PNG sai em ALVO_W
      locale: 'pt-BR',
      timezoneId: 'America/Sao_Paulo',
      permissions: ['camera'],
      ignoreHTTPSErrors: true,
      colorScheme: 'light',      // o backoffice do vídeo é claro, sempre
    });
    page = await context.newPage();
  }

  await login(page);

  const feitas = [];
  let ultimoHash = null, ultimoArquivo = null;

  for (let c = 0; c < cenas.length; c++) {
    const cena = cenas[c];

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

    // A prévia do PDF abre em ABA NOVA. Fotografar sempre a última aba, senão
    // o script retrata a tela que ficou para trás (foi o que estragou o 13).
    const abas = context.pages().filter((pg) => !pg.isClosed());
    const alvoFoto = abas.length ? abas[abas.length - 1] : page;
    if (alvoFoto !== page) {
      await alvoFoto.bringToFront().catch(() => {});
    }

    const arquivo = path.join(SAIDA, `${cena.slide}-${cena.arquivo}.png`);
    // No modo CDP a janela é a do Windows, de tamanho qualquer: forçamos as
    // métricas só durante a foto, para o PNG sair sempre em ALVO_W x 16:9.
    let cdpSessao = null;
    if (CDP) {
      try {
        cdpSessao = await context.newCDPSession(alvoFoto);
        await cdpSessao.send('Emulation.setDeviceMetricsOverride', {
          width: JANELA_W, height: JANELA_H, deviceScaleFactor: ESCALA, mobile: false,
        });
        await page.waitForTimeout(400);
      } catch { cdpSessao = null; }
    }
    const png = await alvoFoto.screenshot();
    if (cdpSessao) {
      await cdpSessao.send('Emulation.clearDeviceMetricsOverride').catch(() => {});
      await cdpSessao.detach().catch(() => {});
    }
    const hash = crypto.createHash('md5').update(png).digest('hex');

    // Guarda-corpo: a maioria das cenas NÃO navega (o wizard é conduzido por
    // você). Se a tela é a mesma da captura anterior, o passo não avançou —
    // salvar aqui renderia treze slides com a mesma imagem, e isso só apareceria
    // na hora de montar o deck.
    if (hash === ultimoHash) {
      console.log(`${VERMELHO}   ✗ a tela não mudou desde ${ultimoArquivo}.${OFF}`);
      console.log(`${AMARELO}     Avance o passo no navegador e aperte ENTER de novo`);
      console.log(`     (ou "p" + ENTER se esta cena realmente repete a anterior).${OFF}\n`);
      if (!AUTO) { c--; continue; }
    }

    fs.writeFileSync(arquivo, png);
    ultimoHash = hash;
    ultimoArquivo = path.basename(arquivo);
    feitas.push(arquivo);
    console.log(`${VERDE}   ✓ ${ultimoArquivo}${OFF}\n`);
  }

  console.log(`${AZUL}=== ${feitas.length} captura(s) ===${OFF}`);
  for (const f of feitas) console.log(`   ${f}`);
  console.log(`\n${AMARELO}Confira antes de montar:${OFF} nenhum "ACME", nenhum "example.com",`);
  console.log(`${AMARELO}nenhum dado real, e o nome "Juliana Prado Loureiro" no canto de todas.${OFF}`);

  pergunta.close();
  // No modo CDP o Chrome é seu — desconecta, não fecha.
  if (CDP) await browser.close().catch(() => {});
  else await browser.close();
}

main().catch((e) => {
  console.error(e);
  pergunta.close();
  process.exit(1);
});
