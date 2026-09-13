import { expect, test } from '@playwright/test';
import { sufixoExecucao } from './helpers/ambiente';
import { prepararParDelegado, type ParDelegado } from './helpers/empresas';
import { esperarMensagem } from './helpers/mailpit';

/**
 * Fase 2 do plano (e2e/EMISSAO_DELEGADA_E2E_PLANO.md): prova que a BASE funciona antes
 * da jornada — preparo do par por API, login das duas empresas em contextos separados,
 * âncoras (data-testid) da página Emissão delegada e leitura do Mailpit.
 */
test.describe.serial('emissão delegada · preparo do par EAMA × operadora', () => {
  let par: ParDelegado;

  test.beforeAll(async ({ browser }) => {
    test.setTimeout(4 * 60 * 1000);
    par = await prepararParDelegado(browser, sufixoExecucao());
  });

  test.afterAll(async () => {
    await par?.encerrar();
  });

  test('EAMA aparece habilitada como emissora', async () => {
    const { page } = par.eama.sessao;
    await page.goto('/dashboard/emissao-delegada');
    await expect(page.getByTestId('delegada-perfil-habilitada')).toBeVisible();
    await expect(page.getByTestId('delegada-convite-slug')).toBeVisible();
    await expect(page.getByTestId('delegada-parceria')).toHaveCount(0);
  });

  test('operadora não é emissora, tem créditos e ainda não tem parceria', async () => {
    const { page } = par.operadora.sessao;
    await page.goto('/dashboard/emissao-delegada');
    await expect(page.getByTestId('delegada-convite-enviar')).toBeVisible();
    await expect(page.getByTestId('delegada-perfil-habilitada')).toHaveCount(0);
    await expect(page.getByTestId('delegada-parceria')).toHaveCount(0);

    expect(par.saldoInicialOperadora).toBeGreaterThanOrEqual(10);
    expect(await par.operadora.api.listarVinculos()).toEqual([]);
  });

  test('Mailpit recebe o e-mail de ativação de cada admin', async () => {
    for (const e of [par.eama, par.operadora]) {
      const m = await esperarMensagem({ para: e.adminEmail, timeoutMs: 30_000 });
      expect(m.Subject.length).toBeGreaterThan(0);
    }
  });
});
