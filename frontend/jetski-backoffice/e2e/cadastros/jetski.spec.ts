import { expect, test, type Page } from '@playwright/test';
import { sufixoExecucao } from '../delegada/helpers/ambiente';
import { prepararEmpresa, type EmpresaSimples } from '../delegada/helpers/empresas';

/**
 * Cadastro de jetski pela UI (/dashboard/jetskis), numa empresa nova por execução.
 * Pré-requisito: um modelo (criado pela API no beforeAll — o cadastro de modelo tem spec própria).
 * O modelo é um Radix Select sem nome acessível; a lista de opções abre num portal fora do
 * diálogo. A tela não mostra toast de erro: os testes de erro leem a resposta HTTP.
 * Plano Trial: frota_max = 3 jetskis ativos.
 */
test.describe.serial('cadastros · jetski', () => {
  const sufixo = sufixoExecucao();
  const nomeModelo = `SeaDoo Spark E2E ${sufixo}`;
  const serie = `E2E-${sufixo}-01`;
  let emp: EmpresaSimples;
  let page: Page;
  let modeloId: string;

  const postJetski = (p: Page) =>
    p.waitForResponse((r) => r.request().method() === 'POST' && /\/jetskis$/.test(r.url()), { timeout: 30_000 });

  async function abrirNovoJetski(p: Page) {
    await p.goto('/dashboard/jetskis');
    await p.getByRole('button', { name: 'Novo Jetski' }).click();
    const dialog = p.getByRole('dialog');
    // Título pelo papel: getByText casaria também a descrição "Cadastre um novo jetski na frota".
    await expect(dialog.getByRole('heading', { name: 'Novo Jetski' })).toBeVisible();
    return dialog;
  }

  async function escolherModelo(p: Page, dialog: ReturnType<Page['getByRole']>) {
    await dialog.getByRole('combobox').first().click();
    const opcao = p.getByRole('option', { name: nomeModelo });
    await expect(opcao).toBeVisible();
    await opcao.click();
    await expect(dialog.getByRole('combobox').first()).toContainText(nomeModelo);
  }

  test.beforeAll(async ({ browser }) => {
    test.setTimeout(3 * 60 * 1000);
    emp = await prepararEmpresa(browser, sufixo, 'jetski');
    page = emp.sessao.page;
    modeloId = (await emp.api.criarModelo(nomeModelo)).id;
  });

  test.afterAll(async () => {
    await emp?.encerrar();
  });

  test('série é obrigatória: sem série o formulário não envia', async () => {
    const dialog = await abrirNovoJetski(page);
    await expect(page.getByText('Nenhum jetski encontrado')).toBeVisible();
    await escolherModelo(page, dialog);

    let posts = 0;
    const contar = (r: { method(): string; url(): string }) => {
      if (r.method() === 'POST' && /\/jetskis$/.test(r.url())) posts++;
    };
    page.on('request', contar);
    await dialog.getByRole('button', { name: 'Criar' }).click();
    expect(await dialog.locator('#serie').evaluate((el: HTMLInputElement) => el.validity.valueMissing)).toBe(true);
    await page.waitForTimeout(1_000);
    page.off('request', contar);

    expect(posts).toBe(0);
    await expect(dialog).toBeVisible();
    await page.keyboard.press('Escape');
    expect(await emp.api.listarJetskis()).toHaveLength(0);
  });

  test('cria jetski com modelo, série, ano e horímetro; aparece na lista e persiste', async () => {
    const dialog = await abrirNovoJetski(page);
    await escolherModelo(page, dialog);
    await dialog.locator('#serie').fill(serie);
    await dialog.locator('#ano').fill('2024');
    await dialog.locator('#horimetroAtual').fill('12.5');

    const [resposta] = await Promise.all([postJetski(page), dialog.getByRole('button', { name: 'Criar' }).click()]);
    expect(resposta.status(), await resposta.text()).toBe(201);
    await expect(dialog).toBeHidden();

    const linha = page.getByRole('row').filter({ hasText: serie });
    await expect(linha).toBeVisible();
    await expect(linha).toContainText('2024');
    await expect(linha).toContainText('12.5');
    await expect(linha).toContainText('Disponível');

    const jetskis = await emp.api.listarJetskis();
    expect(jetskis).toHaveLength(1);
    const j = jetskis[0];
    expect(j.serie).toBe(serie);
    expect(j.modeloId).toBe(modeloId);
    expect(Number(j.ano)).toBe(2024);
    expect(Number(j.horimetroAtual)).toBe(12.5);
    expect(j.status).toBe('DISPONIVEL');
  });

  test('lista mostra o nome do modelo na coluna Modelo', async () => {
    // BUG CONHECIDO (13/set/2026): jetskis/page.tsx renderiza `jetski.modelo?.nome || '-'`,
    // mas GET /v1/tenants/{t}/jetskis (JetskiResponse) só devolve `modeloId` — a coluna
    // Modelo mostra "-" para TODO jetski. `test.fail` mantém a suíte verde enquanto o bug
    // existir e passa a acusar ("expected to fail, but passed") quando for corrigido:
    // aí é só remover esta linha.
    test.fail(true, 'coluna Modelo da lista de jetskis mostra "-": JetskiResponse não traz o modelo');
    await page.goto('/dashboard/jetskis');
    const linha = page.getByRole('row').filter({ hasText: serie });
    await expect(linha).toBeVisible();
    await expect(linha).toContainText(nomeModelo, { timeout: 5_000 });
  });

  test('série duplicada é recusada pelo backend e o diálogo continua aberto', async () => {
    const dialog = await abrirNovoJetski(page);
    await escolherModelo(page, dialog);
    await dialog.locator('#serie').fill(serie);

    const [resposta] = await Promise.all([postJetski(page), dialog.getByRole('button', { name: 'Criar' }).click()]);
    expect(resposta.status()).toBe(400);
    expect(await resposta.text()).toContain('Já existe um jetski com esta série');
    await expect(dialog).toBeVisible();
    expect(await emp.api.listarJetskis()).toHaveLength(1);
  });

  test('limite do plano Trial: o 4º jetski ativo é recusado', async () => {
    // Completa a frota até o limite (3) pela API; o 4º vai pela UI.
    await emp.api.criarJetski(modeloId, `E2E-${sufixo}-02`);
    await emp.api.criarJetski(modeloId, `E2E-${sufixo}-03`);
    expect(await emp.api.listarJetskis()).toHaveLength(3);

    const dialog = await abrirNovoJetski(page);
    await escolherModelo(page, dialog);
    await dialog.locator('#serie').fill(`E2E-${sufixo}-04`);

    const [resposta] = await Promise.all([postJetski(page), dialog.getByRole('button', { name: 'Criar' }).click()]);
    expect(resposta.ok()).toBe(false);
    expect(await resposta.text()).toContain('Limite de jetskis ativos');
    await expect(dialog).toBeVisible();
    expect(await emp.api.listarJetskis()).toHaveLength(3);
  });
});
