import { test, expect } from '../fixtures/auth';
import { hasCredentials, SKIP_MESSAGE } from '../fixtures/auth-check';

// Skip all tests in this file if credentials aren't configured
test.beforeEach(async () => {
  test.skip(!hasCredentials(), SKIP_MESSAGE);
});

test.describe('Sidebar com permissões', () => {
  test('deve exibir grupos do menu para o usuário autenticado', async ({ authenticatedPage }) => {
    await authenticatedPage.goto('/dashboard');
    await authenticatedPage.waitForLoadState('networkidle');

    await expect(authenticatedPage.getByText('Principal')).toBeVisible({ timeout: 30000 });
    await expect(authenticatedPage.getByText('Cadastros')).toBeVisible();
    await expect(authenticatedPage.getByText('Sistema')).toBeVisible();
    // Usuário das fixtures é admin do tenant — deve ver Configurações
    await expect(
      authenticatedPage.getByRole('link', { name: 'Configurações' })
    ).toBeVisible();
  });

  test('deve recolher grupo e manter estado após reload', async ({ authenticatedPage }) => {
    await authenticatedPage.goto('/dashboard');
    await authenticatedPage.waitForLoadState('networkidle');

    const grupoFinanceiro = authenticatedPage.getByRole('button', { name: /Financeiro/ }).first();
    await expect(grupoFinanceiro).toBeVisible({ timeout: 30000 });

    // Item do grupo visível antes de recolher
    const itemComissoes = authenticatedPage.getByRole('link', { name: 'Comissões' });
    await expect(itemComissoes).toBeVisible();

    await grupoFinanceiro.click();
    await expect(itemComissoes).toBeHidden();

    // Estado persistido (localStorage sidebar-groups-storage)
    await authenticatedPage.reload();
    await authenticatedPage.waitForLoadState('networkidle');
    await expect(authenticatedPage.getByRole('link', { name: 'Comissões' })).toBeHidden({
      timeout: 30000,
    });

    // Restaura para não afetar outros testes
    await authenticatedPage.getByRole('button', { name: /Financeiro/ }).first().click();
    await expect(authenticatedPage.getByRole('link', { name: 'Comissões' })).toBeVisible();
  });

  test('deve exibir aba Permissões em Configurações com a matriz', async ({
    authenticatedPage,
  }) => {
    await authenticatedPage.goto('/dashboard/configuracoes?tab=permissoes');
    await authenticatedPage.waitForLoadState('networkidle');

    await expect(
      authenticatedPage.getByRole('tab', { name: /Permissões/ })
    ).toBeVisible({ timeout: 30000 });
    await expect(
      authenticatedPage.getByText('Matriz de permissões por papel')
    ).toBeVisible({ timeout: 30000 });
    // Colunas dos papéis (ADMIN_TENANT fica fora — badge "Acesso total")
    await expect(authenticatedPage.getByRole('columnheader', { name: 'Gerente' })).toBeVisible();
    await expect(authenticatedPage.getByRole('columnheader', { name: 'Operador' })).toBeVisible();
    await expect(authenticatedPage.getByText('Administrador: acesso total')).toBeVisible();
  });
});
