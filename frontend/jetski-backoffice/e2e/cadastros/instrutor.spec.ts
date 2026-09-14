import { expect, test, type Locator, type Page } from '@playwright/test';
import { sufixoExecucao } from '../delegada/helpers/ambiente';
import { gerarCpf } from '../delegada/helpers/cpf';
import { prepararEmpresa, type EmpresaSimples } from '../delegada/helpers/empresas';

/**
 * Cadastro de instrutor pela UI (/dashboard/instrutores), numa empresa nova por execução.
 * Os inputs do diálogo não têm id/label associados: são localizados pela ORDEM
 * (Nome, RG, Órgão emissor, CPF, Nº da CHA, Data de emissão). O botão "Novo instrutor"
 * só aparece depois que /v1/user/permissions responde. A assinatura é um canvas
 * (data-testid "assinatura-canvas") com pointer events.
 */
test.describe.serial('cadastros · instrutor', () => {
  const sufixo = sufixoExecucao();
  const completo = {
    nome: `Instrutor Completo E2E ${sufixo}`,
    rg: '12.345.678-9',
    orgao: 'SSP/SP',
    cpf: gerarCpf(),
    cha: `CHA-E2E-${sufixo}`,
    dataEmissao: '2015-06-20',
  };
  const minimo = `Instrutor Mínimo E2E ${sufixo}`;
  let emp: EmpresaSimples;
  let page: Page;

  const campo = (dialog: Locator, i: number) => dialog.locator('input').nth(i);

  async function abrirNovoInstrutor(p: Page) {
    await p.goto('/dashboard/instrutores');
    await expect(p.getByRole('heading', { name: 'Instrutores (EAMA)' })).toBeVisible();
    const novo = p.getByRole('button', { name: 'Novo instrutor' });
    await expect(novo).toBeVisible({ timeout: 30_000 }); // depende das permissões
    await novo.click();
    const dialog = p.getByRole('dialog');
    await expect(dialog.getByText('Novo instrutor')).toBeVisible();
    return dialog;
  }

  /** Traço em vários passos: o SignaturePad só emite onChange se já registrou movimento. */
  async function assinar(p: Page, dialog: Locator) {
    const canvas = dialog.getByTestId('assinatura-canvas');
    await canvas.scrollIntoViewIfNeeded();
    const box = await canvas.boundingBox();
    if (!box) throw new Error('canvas de assinatura sem dimensões');
    await p.mouse.move(box.x + box.width * 0.15, box.y + box.height * 0.6);
    await p.mouse.down();
    await p.mouse.move(box.x + box.width * 0.45, box.y + box.height * 0.3, { steps: 10 });
    await p.mouse.move(box.x + box.width * 0.8, box.y + box.height * 0.65, { steps: 10 });
    await p.mouse.up();
    await expect(dialog.getByText('Assine aqui (dedo, caneta ou mouse)')).toBeHidden();
  }

  test.beforeAll(async ({ browser }) => {
    test.setTimeout(3 * 60 * 1000);
    emp = await prepararEmpresa(browser, sufixo, 'instrutor');
    page = emp.sessao.page;
  });

  test.afterAll(async () => {
    await emp?.encerrar();
  });

  test('Salvar fica desabilitado enquanto o nome está vazio', async () => {
    const dialog = await abrirNovoInstrutor(page);
    await expect(page.getByText('Nenhum instrutor cadastrado.')).toBeVisible();
    const salvar = dialog.getByRole('button', { name: 'Salvar' });

    await expect(salvar).toBeDisabled();
    await campo(dialog, 0).fill('   ');
    await expect(salvar).toBeDisabled();
    await campo(dialog, 0).fill('Alguém');
    await expect(salvar).toBeEnabled();

    await page.keyboard.press('Escape');
    expect(await emp.api.listarInstrutores()).toHaveLength(0);
  });

  test('cria instrutor com todos os campos e assinatura; aparece na lista e persiste', async () => {
    const dialog = await abrirNovoInstrutor(page);
    await campo(dialog, 0).fill(completo.nome);
    await campo(dialog, 1).fill(completo.rg);
    await campo(dialog, 2).fill(completo.orgao);
    await campo(dialog, 3).fill(completo.cpf);
    await campo(dialog, 4).fill(completo.cha);
    await dialog.locator('input[type="date"]').fill(completo.dataEmissao);
    await assinar(page, dialog);

    await dialog.getByRole('button', { name: 'Salvar' }).click();
    await expect(page.getByText('Instrutor cadastrado.')).toBeVisible();
    await expect(dialog).toBeHidden();

    const linha = page.getByRole('row').filter({ hasText: completo.nome });
    await expect(linha).toBeVisible();
    for (const valor of [completo.rg, completo.orgao, completo.cpf, completo.cha, 'Ativo']) {
      await expect(linha).toContainText(valor);
    }

    const salvo = (await emp.api.listarInstrutores()).find((i) => i.nome === completo.nome);
    expect(salvo, 'instrutor completo na API').toBeTruthy();
    expect(salvo!.rg).toBe(completo.rg);
    expect(salvo!.orgaoEmissor).toBe(completo.orgao);
    expect(salvo!.cpf).toBe(completo.cpf);
    expect(salvo!.cha).toBe(completo.cha);
    expect(salvo!.dataEmissao).toBe(completo.dataEmissao);
    expect(salvo!.temAssinatura).toBe(true);
    expect(salvo!.ativo).not.toBe(false);
  });

  test('cria instrutor só com o nome (campos opcionais vazios e sem assinatura)', async () => {
    const dialog = await abrirNovoInstrutor(page);
    await campo(dialog, 0).fill(minimo);
    await dialog.getByRole('button', { name: 'Salvar' }).click();
    await expect(page.getByText('Instrutor cadastrado.').last()).toBeVisible();
    await expect(dialog).toBeHidden();

    await expect(page.getByRole('row').filter({ hasText: minimo })).toBeVisible();

    const instrutores = await emp.api.listarInstrutores();
    expect(instrutores).toHaveLength(2);
    const salvo = instrutores.find((i) => i.nome === minimo);
    expect(salvo, 'instrutor mínimo na API').toBeTruthy();
    expect(salvo!.temAssinatura).toBe(false);
    for (const opcional of [salvo!.rg, salvo!.orgaoEmissor, salvo!.cpf, salvo!.cha, salvo!.dataEmissao]) {
      expect(opcional ?? '').toBe('');
    }
  });
});
