import path from 'path';
import { expect, type Locator, type Page } from '@playwright/test';
import type { ApiEmpresa } from './backend';
import { buscar, lerMensagem } from './mailpit';

/**
 * Atendimento de balcão pela UI, passos 1 a 7 (cliente → pagamento). A emissão (passo 8)
 * fica na spec, porque é lá que moram os asserts da delegação.
 *
 * Base: e2e/capturas/wizard.mjs, com duas diferenças deliberadas:
 *  - GRU: NUNCA clicar em "Gerar PIX" (gera GRU REAL no site da Marinha). A GRU é marcada
 *    paga pela API logo depois que o passo Aluguel cria o rascunho, e a página é
 *    recarregada — o balcão reidrata o passo do sessionStorage e o passo Habilitação lê
 *    a GRU paga do servidor. Não dá para usar ?reserva=<id>: a retomada pula Documentos,
 *    e sem identidade/selfie/naturalidade/residência o ofício à Marinha fica BLOQUEADO.
 *  - Videoaula: o YouTube é bloqueado no contexto (feito pela spec) e a tela cai na
 *    confirmação manual, que só HABILITA depois do erro do player — por isso a espera.
 */

export const AMOSTRA_DOCUMENTO = path.resolve(__dirname, '../../../../../apresentacao/insumos/documento-amostra.png');

export interface ClienteBalcao {
  nome: string;
  cpf: string;
  email: string;
}

const tid = (page: Page, id: string) => page.getByTestId(id);

/** Espera um controle ficar habilitado (isEnabled não espera sozinho). */
export async function esperarHabilitado(loc: Locator, ms = 20_000): Promise<void> {
  await expect(loc).toBeEnabled({ timeout: ms });
}

/**
 * Marca um checkbox conferindo o estado. Os checkboxes ficam dentro de <label>: um clique
 * pode disparar no controle E no rótulo, dois toggles que se anulam sem erro na tela —
 * por isso o estado é conferido depois de cada clique.
 *
 * SEM `force`: dentro de um Dialog do Radix o conteúdo entra animado (slide + zoom, 200 ms)
 * e o `force` pula a espera por elemento estável. O clique saía nas coordenadas do meio da
 * animação, caía FORA do diálogo, o Radix fechava o diálogo e o checkbox sumia — a próxima
 * tentativa esperava para sempre um elemento que não existia mais.
 */
export async function marcar(loc: Locator): Promise<void> {
  await loc.waitFor({ state: 'visible', timeout: 20_000 });
  for (let i = 0; i < 4; i++) {
    if ((await loc.getAttribute('data-state', { timeout: 5_000 })) === 'checked') return;
    if ((await loc.getAttribute('aria-checked', { timeout: 5_000 })) === 'true') return;
    await loc.click({ timeout: 15_000 });
    await loc.page().waitForTimeout(350);
  }
  throw new Error('checkbox continuou desmarcado após 4 tentativas');
}

/** Radix Select: abre o gatilho e escolhe a opção por texto (ou a primeira). */
export async function escolher(page: Page, gatilho: Locator, texto?: string): Promise<void> {
  await gatilho.click();
  const opcoes = page.getByRole('option');
  await opcoes.first().waitFor({ state: 'visible', timeout: 15_000 });
  await (texto ? opcoes.filter({ hasText: texto }).first() : opcoes.first()).click();
}

/** Traço de assinatura sobre o canvas. */
async function assinar(page: Page): Promise<void> {
  const canvas = tid(page, 'balcao-termos-assinatura');
  await canvas.scrollIntoViewIfNeeded();
  const box = await canvas.boundingBox();
  if (!box) throw new Error('canvas de assinatura sem dimensões');
  await page.mouse.move(box.x + box.width * 0.2, box.y + box.height * 0.6);
  await page.mouse.down();
  for (const [dx, dy] of [[0.35, 0.3], [0.5, 0.7], [0.65, 0.35], [0.8, 0.55]]) {
    await page.mouse.move(box.x + box.width * dx, box.y + box.height * dy, { steps: 8 });
  }
  await page.mouse.up();
}

/** Código de 6 dígitos mais recente enviado ao e-mail do cliente. */
async function codigoOtp(email: string): Promise<string> {
  const limite = Date.now() + 60_000;
  while (Date.now() < limite) {
    for (const resumo of (await buscar({ para: email })).slice(0, 3)) {
      const m = await lerMensagem(resumo.ID);
      const achado = `${m.Text ?? ''} ${m.HTML ?? ''}`.match(/\b(\d{6})\b/);
      if (achado) return achado[1];
    }
    await new Promise((r) => setTimeout(r, 1_500));
  }
  throw new Error(`código OTP não chegou ao Mailpit para ${email}`);
}

/**
 * Conduz os passos 1 a 7 e devolve o id da reserva. Ao final, a página está no passo
 * Emissão com a habilitação resolvida.
 */
export async function atenderNoBalcao(
  page: Page,
  operadora: ApiEmpresa,
  cliente: ClienteBalcao,
  gruNumero: string,
): Promise<string> {
  // ---- 1. Cliente (pré-conta nova, com e-mail: a via do cliente precisa de destino)
  await page.goto('/dashboard/balcao');
  const novo = page.getByRole('button', { name: /Novo atendimento/i });
  if (await novo.isVisible().catch(() => false)) await novo.click();
  await tid(page, 'balcao-cliente-documento').fill(cliente.cpf);
  await tid(page, 'balcao-cliente-buscar').click();
  await tid(page, 'balcao-cliente-nome').waitFor({ timeout: 30_000 });
  await tid(page, 'balcao-cliente-nome').fill(cliente.nome);
  await page.locator('input[type="email"]').first().fill(cliente.email);
  await tid(page, 'balcao-cliente-criar').click();

  // ---- 2. Passeio & Preço — o rascunho da reserva nasce no "continuar"
  await tid(page, 'balcao-aluguel-modelo').waitFor({ timeout: 30_000 });
  await escolher(page, tid(page, 'balcao-aluguel-modelo'));
  await tid(page, 'balcao-aluguel-duracao-60').click();
  const [respostaRascunho] = await Promise.all([
    page.waitForResponse((r) => r.request().method() === 'POST' && /\/reservas\/rascunho$/.test(r.url()), {
      timeout: 30_000,
    }),
    tid(page, 'balcao-aluguel-continuar').click(),
  ]);
  expect(respostaRascunho.ok(), `rascunho da reserva: HTTP ${respostaRascunho.status()}`).toBeTruthy();
  const reservaId = ((await respostaRascunho.json()) as { id: string }).id;

  // ---- 3. Habilitação: GRU paga pela API + recarga (ver cabeçalho)
  await tid(page, 'balcao-hab-sem-cha').waitFor({ timeout: 30_000 });
  await operadora.registrarGruPaga(reservaId, gruNumero);
  await page.reload();
  await tid(page, 'balcao-hab-sem-cha').waitFor({ timeout: 30_000 });
  await tid(page, 'balcao-hab-sem-cha').click();
  await esperarHabilitado(tid(page, 'balcao-hab-avancar'), 30_000);
  await tid(page, 'balcao-hab-avancar').click();

  // ---- 4. Documentos: identidade, selfie, dados NORMAM e endereço (Declaração 1-C)
  await tid(page, 'balcao-doc-identidade').waitFor({ state: 'attached', timeout: 30_000 });
  await tid(page, 'balcao-doc-identidade').setInputFiles(AMOSTRA_DOCUMENTO);
  await tid(page, 'balcao-doc-selfie').setInputFiles(AMOSTRA_DOCUMENTO);
  await tid(page, 'balcao-doc-rg').fill('12.345.678-9');
  await tid(page, 'balcao-doc-orgao').fill('SSP/SP');

  // Naturalidade: UF (padrão SP) + cidade. A lista vem do IBGE no navegador; se o IBGE
  // estiver fora, a tela troca o select por um campo de texto.
  const blocoNaturalidade = page.getByText('Naturalidade', { exact: false }).first().locator('xpath=ancestor::div[1]');
  const cidadeTexto = blocoNaturalidade.getByPlaceholder('Cidade');
  if (await cidadeTexto.isVisible().catch(() => false)) {
    await cidadeTexto.fill('Santos');
  } else {
    await escolher(page, blocoNaturalidade.getByRole('combobox').nth(1), 'Santos');
  }

  await tid(page, 'balcao-doc-sem-comprovante').click();
  await page.getByPlaceholder('00000-000').fill('11010-000');
  const logradouro = page.getByPlaceholder('Rua / Avenida');
  await page.waitForTimeout(1_500); // ViaCEP pode preencher o logradouro
  if (!(await logradouro.inputValue())) await logradouro.fill('Rua E2E da Emissão Delegada');
  await page.getByText('Número', { exact: true }).locator('xpath=following-sibling::input[1]').fill('100');
  // Espera os uploads comprimirem antes de avançar (o salvar sobe os dataURLs).
  await expect(page.getByText('Carregando…')).toHaveCount(0, { timeout: 20_000 });
  await tid(page, 'balcao-doc-avancar').click();

  // ---- 5. Orientações: YouTube bloqueado → erro do player → confirmação manual
  const confirmar = tid(page, 'balcao-orient-confirmar');
  await confirmar.waitFor({ timeout: 30_000 });
  await esperarHabilitado(confirmar, 90_000);
  await marcar(confirmar);
  await esperarHabilitado(tid(page, 'balcao-orient-continuar'));
  await tid(page, 'balcao-orient-continuar').click();

  // ---- 6. Termos: ciência, assinatura e OTP se a empresa exigir
  await marcar(tid(page, 'balcao-termos-ciencia'));
  await assinar(page);
  const enviarCodigo = tid(page, 'balcao-termos-enviar-codigo');
  if (await enviarCodigo.isVisible().catch(() => false)) {
    await enviarCodigo.click();
    await tid(page, 'balcao-termos-codigo').fill(await codigoOtp(cliente.email));
    await tid(page, 'balcao-termos-verificar-codigo').click();
  }
  await esperarHabilitado(tid(page, 'balcao-termos-assinar-avancar'));
  await tid(page, 'balcao-termos-assinar-avancar').click();

  // ---- 7. Pagamento do passeio: fica para depois (não bloqueia a emissão)
  const depois = tid(page, 'balcao-pag-depois');
  const continuar = tid(page, 'balcao-pag-continuar');
  await expect(depois.or(continuar)).toBeVisible({ timeout: 30_000 });
  await ((await depois.isVisible()) ? depois : continuar).click();

  await tid(page, 'balcao-emissao-emitir').waitFor({ timeout: 30_000 });
  return reservaId;
}
