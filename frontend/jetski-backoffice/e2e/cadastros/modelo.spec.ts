import { expect, test, type Page } from '@playwright/test';
import { sufixoExecucao } from '../delegada/helpers/ambiente';
import { prepararEmpresa, type EmpresaSimples } from '../delegada/helpers/empresas';

/**
 * Cadastro de modelo pela UI (/dashboard/modelos), numa empresa nova por execução.
 * O formulário é um Dialog com inputs de id próprio (getByLabel/#id funcionam).
 * Atenção: a tela NÃO mostra toast de erro — uma falha deixa o diálogo aberto e mudo;
 * por isso os testes de erro leem a resposta HTTP.
 */
test.describe.serial('cadastros · modelo', () => {
  const sufixo = sufixoExecucao();
  const nome = `SeaDoo GTI E2E ${sufixo}`;
  let emp: EmpresaSimples;
  let page: Page;

  const postModelo = (p: Page) =>
    p.waitForResponse((r) => r.request().method() === 'POST' && /\/modelos$/.test(r.url()), { timeout: 30_000 });

  test.beforeAll(async ({ browser }) => {
    test.setTimeout(3 * 60 * 1000);
    emp = await prepararEmpresa(browser, sufixo, 'modelo');
    page = emp.sessao.page;
  });

  test.afterAll(async () => {
    await emp?.encerrar();
  });

  test('nome é obrigatório: sem nome o formulário não envia', async () => {
    await page.goto('/dashboard/modelos');
    await expect(page.getByText('Nenhum modelo encontrado')).toBeVisible();

    let posts = 0;
    const contar = (r: { method(): string; url(): string }) => {
      if (r.method() === 'POST' && /\/modelos$/.test(r.url())) posts++;
    };
    page.on('request', contar);

    await page.getByRole('button', { name: 'Novo Modelo' }).click();
    const dialog = page.getByRole('dialog');
    await expect(dialog).toBeVisible();
    await dialog.getByRole('button', { name: 'Criar' }).click();

    // Validação nativa (required): o navegador bloqueia o submit.
    expect(await dialog.locator('#nome').evaluate((el: HTMLInputElement) => el.validity.valueMissing)).toBe(true);
    await expect(dialog).toBeVisible();
    await page.waitForTimeout(1_000);
    page.off('request', contar);
    expect(posts).toBe(0);

    await page.keyboard.press('Escape');
    await expect(dialog).toBeHidden();
    expect(await emp.api.listarModelos()).toHaveLength(0);
  });

  test('cria modelo com todos os campos, aparece na lista e persiste', async () => {
    await page.goto('/dashboard/modelos');
    await page.getByRole('button', { name: 'Novo Modelo' }).click();
    const dialog = page.getByRole('dialog');
    await expect(dialog.getByText('Novo Modelo')).toBeVisible();

    await dialog.locator('#nome').fill(nome);
    await dialog.locator('#fabricante').fill('Sea-Doo');
    await dialog.locator('#potenciaHp').fill('130');
    await dialog.locator('#capacidadePessoas').fill('3');
    await dialog.locator('#precoBase').fill('220');
    await dialog.locator('#taxaHoraExtra').fill('60');
    await dialog.locator('#toleranciaMin').fill('10');
    await dialog.locator('#caucao').fill('500');

    const [resposta] = await Promise.all([postModelo(page), dialog.getByRole('button', { name: 'Criar' }).click()]);
    expect(resposta.status(), await resposta.text()).toBe(201);
    await expect(dialog).toBeHidden();

    // Linha na tabela: nome, fabricante, preço, capacidade e badges padrão.
    const linha = page.getByRole('row').filter({ hasText: nome });
    await expect(linha).toBeVisible();
    await expect(linha).toContainText('Sea-Doo');
    await expect(linha).toContainText('220,00');
    await expect(linha).toContainText('60,00');
    await expect(linha).toContainText('3 pessoas');
    await expect(linha).toContainText('Ativo');

    // Persistido com os valores do formulário (API da empresa, escopo por tenant).
    const modelos = await emp.api.listarModelos();
    expect(modelos).toHaveLength(1);
    const m = modelos[0];
    expect(m.nome).toBe(nome);
    expect(m.fabricante).toBe('Sea-Doo');
    expect(Number(m.potenciaHp)).toBe(130);
    expect(Number(m.capacidadePessoas)).toBe(3);
    expect(Number(m.precoBaseHora)).toBe(220);
    expect(Number(m.taxaHoraExtra)).toBe(60);
    expect(Number(m.toleranciaMin)).toBe(10);
    expect(Number(m.caucao)).toBe(500);
  });

  test('nome duplicado é recusado pelo backend e o diálogo continua aberto', async () => {
    await page.goto('/dashboard/modelos');
    await page.getByRole('button', { name: 'Novo Modelo' }).click();
    const dialog = page.getByRole('dialog');
    await dialog.locator('#nome').fill(nome);

    const [resposta] = await Promise.all([postModelo(page), dialog.getByRole('button', { name: 'Criar' }).click()]);
    expect(resposta.status()).toBe(400);
    expect(await resposta.text()).toContain('Já existe um modelo com este nome');

    // Sem toast de erro na tela hoje: o sinal visível é o diálogo que não fecha.
    await expect(dialog).toBeVisible();
    expect(await emp.api.listarModelos()).toHaveLength(1);
  });
});
