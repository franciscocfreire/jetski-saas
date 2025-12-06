import { test, expect } from '../fixtures/auth';
import { hasCredentials, SKIP_MESSAGE } from '../fixtures/auth-check';

// Skip all tests in this file if credentials aren't configured
test.beforeEach(async () => {
  test.skip(!hasCredentials(), SKIP_MESSAGE);
});

test.describe('Cadastros', () => {
  test.describe('Jetskis', () => {
    test('deve exibir página de jetskis corretamente', async ({ authenticatedPage }) => {
      await authenticatedPage.goto('/dashboard/jetskis');
      await authenticatedPage.waitForLoadState('networkidle');

      await expect(authenticatedPage.getByRole('heading', { name: /Jetskis|Frota/i })).toBeVisible({
        timeout: 30000,
      });
    });

    test('deve ter botão para adicionar novo jetski', async ({ authenticatedPage }) => {
      await authenticatedPage.goto('/dashboard/jetskis');
      await authenticatedPage.waitForLoadState('networkidle');

      const novoButton = authenticatedPage.getByRole('button', { name: /Novo|Adicionar|Cadastrar/i });
      await expect(novoButton).toBeVisible({ timeout: 10000 });
    });

    test('deve exibir tabela ou lista de jetskis', async ({ authenticatedPage }) => {
      await authenticatedPage.goto('/dashboard/jetskis');
      await authenticatedPage.waitForLoadState('networkidle');

      // Verifica se tem tabela ou mensagem de vazio
      const hasTable = await authenticatedPage.getByRole('table').isVisible().catch(() => false);
      const hasEmptyMessage = await authenticatedPage.getByText(/Nenhum|vazio|cadastrado/i).isVisible().catch(() => false);
      const hasList = await authenticatedPage.locator('[class*="grid"], [class*="list"]').first().isVisible().catch(() => false);

      expect(hasTable || hasEmptyMessage || hasList).toBeTruthy();
    });
  });

  test.describe('Modelos', () => {
    test('deve exibir página de modelos corretamente', async ({ authenticatedPage }) => {
      await authenticatedPage.goto('/dashboard/modelos');
      await authenticatedPage.waitForLoadState('networkidle');

      await expect(authenticatedPage.getByRole('heading', { name: /Modelos/i })).toBeVisible({
        timeout: 30000,
      });
    });

    test('deve ter botão para adicionar novo modelo', async ({ authenticatedPage }) => {
      await authenticatedPage.goto('/dashboard/modelos');
      await authenticatedPage.waitForLoadState('networkidle');

      const novoButton = authenticatedPage.getByRole('button', { name: /Novo|Adicionar|Cadastrar/i });
      await expect(novoButton).toBeVisible({ timeout: 10000 });
    });
  });

  test.describe('Clientes', () => {
    test('deve exibir página de clientes corretamente', async ({ authenticatedPage }) => {
      await authenticatedPage.goto('/dashboard/clientes');
      await authenticatedPage.waitForLoadState('networkidle');

      await expect(authenticatedPage.getByRole('heading', { name: /Clientes/i })).toBeVisible({
        timeout: 30000,
      });
    });

    test('deve ter botão para adicionar novo cliente', async ({ authenticatedPage }) => {
      await authenticatedPage.goto('/dashboard/clientes');
      await authenticatedPage.waitForLoadState('networkidle');

      const novoButton = authenticatedPage.getByRole('button', { name: /Novo|Adicionar|Cadastrar/i });
      await expect(novoButton).toBeVisible({ timeout: 10000 });
    });

    test('deve ter campo de busca', async ({ authenticatedPage }) => {
      await authenticatedPage.goto('/dashboard/clientes');
      await authenticatedPage.waitForLoadState('networkidle');

      // Pode haver mais de um input de busca na página, usamos .first()
      const searchInput = authenticatedPage.getByPlaceholder(/Buscar|Pesquisar|Filtrar/i).first();
      await expect(searchInput).toBeVisible({ timeout: 10000 });
    });
  });

  test.describe('Vendedores', () => {
    test('deve exibir página de vendedores corretamente', async ({ authenticatedPage }) => {
      await authenticatedPage.goto('/dashboard/vendedores');
      await authenticatedPage.waitForLoadState('networkidle');

      await expect(authenticatedPage.getByRole('heading', { name: /Vendedores|Parceiros/i })).toBeVisible({
        timeout: 30000,
      });
    });
  });

  test.describe('Manutenção', () => {
    test('deve exibir página de manutenção corretamente', async ({ authenticatedPage }) => {
      await authenticatedPage.goto('/dashboard/manutencao');
      await authenticatedPage.waitForLoadState('networkidle');

      await expect(authenticatedPage.getByRole('heading', { name: /Manutenção|Manutenções/i })).toBeVisible({
        timeout: 30000,
      });
    });
  });
});
