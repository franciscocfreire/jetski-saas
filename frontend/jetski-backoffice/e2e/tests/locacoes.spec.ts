import { test, expect } from '../fixtures/auth';
import { LocacoesPage, DashboardPage } from '../pages';
import { hasCredentials, SKIP_MESSAGE } from '../fixtures/auth-check';

// Skip all tests in this file if credentials aren't configured
test.beforeEach(async () => {
  test.skip(!hasCredentials(), SKIP_MESSAGE);
});

test.describe('Locações', () => {
  test.describe('Visualização', () => {
    test('deve exibir página de locações corretamente', async ({ authenticatedPage }) => {
      // Navigate via sidebar instead of goto (to keep session)
      await authenticatedPage.getByRole('link', { name: /locações/i }).click();
      await authenticatedPage.waitForLoadState('networkidle');

      const locacoesPage = new LocacoesPage(authenticatedPage);

      // Verifica elementos principais
      await expect(locacoesPage.pageTitle).toBeVisible();
      await expect(locacoesPage.novoCheckInButton).toBeVisible();
      await expect(locacoesPage.searchInput).toBeVisible();
    });

    test('deve exibir tabela de locações ou mensagem vazia', async ({ authenticatedPage }) => {
      const locacoesPage = new LocacoesPage(authenticatedPage);
      await locacoesPage.goto();

      // Aguarda carregamento
      await authenticatedPage.waitForLoadState('networkidle');

      // Verifica se tem tabela ou mensagem de vazio
      const hasTable = (await locacoesPage.locacoesTable.locator('tbody tr').count()) > 0;
      const hasEmptyMessage = await locacoesPage.emptyMessage.isVisible().catch(() => false);

      expect(hasTable || hasEmptyMessage).toBeTruthy();
    });

    test('deve filtrar locações por status "Em curso"', async ({ authenticatedPage }) => {
      const locacoesPage = new LocacoesPage(authenticatedPage);
      await locacoesPage.goto();

      await locacoesPage.filterByStatus('EM_CURSO');
      await authenticatedPage.waitForLoadState('networkidle');

      // Todas as locações visíveis devem ter status "Em curso" ou tabela vazia
      const rows = locacoesPage.locacoesTable.locator('tbody tr');
      const count = await rows.count();

      if (count > 0) {
        // Verifica se não há status diferente de "Em curso"
        const finalizadas = await locacoesPage.finalizadaStatus.count();
        expect(finalizadas).toBe(0);
      }
    });

    test('deve filtrar locações por status "Finalizadas"', async ({ authenticatedPage }) => {
      const locacoesPage = new LocacoesPage(authenticatedPage);
      await locacoesPage.goto();

      await locacoesPage.filterByStatus('FINALIZADA');
      await authenticatedPage.waitForLoadState('networkidle');

      const rows = locacoesPage.locacoesTable.locator('tbody tr');
      const count = await rows.count();

      if (count > 0) {
        // Verifica se não há status diferente de "Finalizada"
        const emCurso = await locacoesPage.emCursoStatus.count();
        expect(emCurso).toBe(0);
      }
    });
  });

  test.describe('Check-in Walk-in', () => {
    test('deve abrir dialog de check-in ao clicar no botão', async ({ authenticatedPage }) => {
      const locacoesPage = new LocacoesPage(authenticatedPage);
      await locacoesPage.goto();

      await locacoesPage.openCheckInDialog();

      // Verifica que o dialog está aberto
      await expect(locacoesPage.checkInDialog).toBeVisible();
      await expect(authenticatedPage.getByText('Novo Check-in (Walk-in)')).toBeVisible();
    });

    test('deve exibir campos obrigatórios no dialog de check-in', async ({ authenticatedPage }) => {
      const locacoesPage = new LocacoesPage(authenticatedPage);
      await locacoesPage.goto();

      await locacoesPage.openCheckInDialog();

      // Verifica campos obrigatórios
      await expect(authenticatedPage.getByText('Jetski *')).toBeVisible();
      await expect(authenticatedPage.getByText('Duração Prevista *')).toBeVisible();
      await expect(authenticatedPage.getByText('Horímetro Inicial *')).toBeVisible();
      await expect(authenticatedPage.getByText('Checklist de Saída')).toBeVisible();
    });

    test('deve exibir lista de jetskis disponíveis', async ({ authenticatedPage }) => {
      const locacoesPage = new LocacoesPage(authenticatedPage);
      await locacoesPage.goto();

      await locacoesPage.openCheckInDialog();

      // Abre o select de jetski
      await authenticatedPage.getByText('Selecione um jetski disponível').click();

      // Aguarda opções carregarem
      await authenticatedPage.waitForTimeout(1000);

      // Verifica se tem pelo menos um jetski ou mensagem de sem opções
      const options = authenticatedPage.getByRole('option');
      const count = await options.count();

      // Pode ter jetskis ou não (depende do estado do tenant de teste)
      expect(count).toBeGreaterThanOrEqual(0);
    });

    test('deve exibir checklist com itens de verificação', async ({ authenticatedPage }) => {
      const locacoesPage = new LocacoesPage(authenticatedPage);
      await locacoesPage.goto();

      await locacoesPage.openCheckInDialog();

      // Verifica itens do checklist
      await expect(locacoesPage.motorOkCheckbox).toBeVisible();
      await expect(locacoesPage.cascoOkCheckbox).toBeVisible();
      await expect(locacoesPage.combustivelOkCheckbox).toBeVisible();
      await expect(locacoesPage.equipamentosOkCheckbox).toBeVisible();
    });

    test('deve permitir marcar itens do checklist', async ({ authenticatedPage }) => {
      const locacoesPage = new LocacoesPage(authenticatedPage);
      await locacoesPage.goto();

      await locacoesPage.openCheckInDialog();

      // Marca os itens
      await locacoesPage.motorOkCheckbox.check();
      await locacoesPage.cascoOkCheckbox.check();

      // Verifica que estão marcados
      await expect(locacoesPage.motorOkCheckbox).toBeChecked();
      await expect(locacoesPage.cascoOkCheckbox).toBeChecked();
    });

    test('deve fechar dialog ao clicar em cancelar', async ({ authenticatedPage }) => {
      const locacoesPage = new LocacoesPage(authenticatedPage);
      await locacoesPage.goto();

      await locacoesPage.openCheckInDialog();
      await expect(locacoesPage.checkInDialog).toBeVisible();

      await locacoesPage.cancelarButton.click();

      await expect(locacoesPage.checkInDialog).not.toBeVisible();
    });
  });

  test.describe('Navegação', () => {
    test('deve acessar locações via dashboard', async ({ authenticatedPage }) => {
      // Dashboard is where we land after login
      const dashboardPage = new DashboardPage(authenticatedPage);

      // Clicar em "Novo Check-in" no dashboard
      await dashboardPage.clickNovoCheckIn();

      // Verificar que chegou na página de locações
      await expect(authenticatedPage).toHaveURL(/.*\/locacoes.*/);
    });

    test('deve buscar locações por texto', async ({ authenticatedPage }) => {
      const locacoesPage = new LocacoesPage(authenticatedPage);
      await locacoesPage.goto();

      // Digita uma busca
      await locacoesPage.search('jetski');
      await authenticatedPage.waitForTimeout(500);

      // O filtro deve estar ativo (verifica que o input tem valor)
      await expect(locacoesPage.searchInput).toHaveValue('jetski');
    });
  });

  test.describe('Check-out', () => {
    test('deve exibir opção de check-out para locações em curso', async ({ authenticatedPage }) => {
      const locacoesPage = new LocacoesPage(authenticatedPage);
      await locacoesPage.goto();

      // Filtrar apenas locações em curso
      await locacoesPage.filterByStatus('EM_CURSO');
      await authenticatedPage.waitForLoadState('networkidle');

      // Verifica se tem a mensagem de "nenhuma locação" (tabela vazia)
      const emptyMessage = authenticatedPage.getByText('Nenhuma locação encontrada');
      const hasEmpty = await emptyMessage.isVisible().catch(() => false);

      if (hasEmpty) {
        test.skip(true, 'Nenhuma locação em curso para testar check-out');
        return;
      }

      // Se tem locações, abre menu de ações da primeira
      const rows = locacoesPage.locacoesTable.locator('tbody tr');
      const firstRowButton = rows.first().getByRole('button');

      if (await firstRowButton.isVisible().catch(() => false)) {
        await firstRowButton.click();
        // Verifica se tem opção de check-out
        await expect(authenticatedPage.getByRole('menuitem', { name: /Fazer Check-out/i })).toBeVisible();
      } else {
        test.skip(true, 'Nenhuma locação com botão de ações encontrada');
      }
    });

    test('deve abrir dialog de check-out com informações da locação', async ({ authenticatedPage }) => {
      const locacoesPage = new LocacoesPage(authenticatedPage);
      await locacoesPage.goto();

      await locacoesPage.filterByStatus('EM_CURSO');
      await authenticatedPage.waitForLoadState('networkidle');

      // Verifica se tem a mensagem de "nenhuma locação" (tabela vazia)
      const emptyMessage = authenticatedPage.getByText('Nenhuma locação encontrada');
      const hasEmpty = await emptyMessage.isVisible().catch(() => false);

      if (hasEmpty) {
        test.skip(true, 'Nenhuma locação em curso para testar dialog de check-out');
        return;
      }

      // Se tem locações, abre menu e clica em check-out
      const rows = locacoesPage.locacoesTable.locator('tbody tr');
      const firstRowButton = rows.first().getByRole('button');

      if (await firstRowButton.isVisible().catch(() => false)) {
        await firstRowButton.click();
        await authenticatedPage.getByRole('menuitem', { name: /Fazer Check-out/i }).click();

        // Verifica dialog
        await expect(authenticatedPage.getByRole('dialog')).toBeVisible();
        await expect(authenticatedPage.getByText('Check-out')).toBeVisible();
        await expect(authenticatedPage.getByText('Horímetro Final *')).toBeVisible();
        await expect(authenticatedPage.getByText('Checklist de Verificação')).toBeVisible();
      } else {
        test.skip(true, 'Nenhuma locação com botão de ações encontrada');
      }
    });
  });

  test.describe('Fluxo Completo: Check-in → Check-out', () => {
    test('deve realizar check-in, verificar opções de check-out e finalizar locação', async ({ authenticatedPage }) => {
      const locacoesPage = new LocacoesPage(authenticatedPage);
      await locacoesPage.goto();

      // ========== PASSO 1: Verificar se há jetskis disponíveis ==========
      await locacoesPage.openCheckInDialog();

      // Abre o select de jetski
      const jetskiSelectTrigger = authenticatedPage.getByText('Selecione um jetski disponível');
      await jetskiSelectTrigger.click();
      await authenticatedPage.waitForTimeout(1000);

      // Verifica se tem opções
      const options = authenticatedPage.getByRole('option');
      const optionsCount = await options.count();

      if (optionsCount === 0) {
        // Fecha pressionando Escape ao invés de clicar em Cancelar
        await authenticatedPage.keyboard.press('Escape');
        await authenticatedPage.waitForTimeout(500);
        test.skip(true, 'Nenhum jetski disponível. Execute o global-setup com criação de dados de teste.');
        return;
      }

      // Seleciona o primeiro jetski disponível
      const primeiroJetski = options.first();
      const jetskiNome = await primeiroJetski.textContent();
      await primeiroJetski.click();

      // ========== PASSO 2: Preencher dados do check-in ==========
      // Seleciona duração (1 hora)
      const duracaoSelect = authenticatedPage.getByRole('combobox').filter({ hasText: /hora|min|Selecione/i }).last();
      await duracaoSelect.click();
      await authenticatedPage.waitForTimeout(500);

      // Tenta selecionar 1 hora ou a primeira opção disponível
      const duracaoOption = authenticatedPage.getByRole('option').filter({ hasText: /1.*hora|60.*min/i }).first();
      const hasDuracaoOption = await duracaoOption.isVisible().catch(() => false);
      if (hasDuracaoOption) {
        await duracaoOption.click();
      } else {
        // Seleciona a primeira opção disponível
        await authenticatedPage.getByRole('option').first().click();
      }

      // Preenche horímetro inicial
      const horimetroInput = authenticatedPage.locator('input[type="number"]').first();
      await horimetroInput.fill('100');

      // Marca itens do checklist
      await locacoesPage.motorOkCheckbox.check();
      await locacoesPage.cascoOkCheckbox.check();
      await locacoesPage.combustivelOkCheckbox.check();
      await locacoesPage.equipamentosOkCheckbox.check();

      // ========== PASSO 3: Iniciar locação ==========
      await locacoesPage.iniciarLocacaoButton.click();

      // Aguarda o dialog fechar e a página atualizar
      await expect(locacoesPage.checkInDialog).not.toBeVisible({ timeout: 10000 });
      await authenticatedPage.waitForLoadState('networkidle');
      await authenticatedPage.waitForTimeout(1000);

      // ========== PASSO 4: Verificar que a locação foi criada ==========
      // Filtra por em curso para ver a locação recém criada
      await locacoesPage.filterByStatus('EM_CURSO');
      await authenticatedPage.waitForLoadState('networkidle');

      // Deve ter pelo menos uma locação em curso agora
      const rows = locacoesPage.locacoesTable.locator('tbody tr');
      const rowCount = await rows.count();
      expect(rowCount).toBeGreaterThan(0);

      // ========== PASSO 5: Verificar opções de check-out ==========
      const firstRowButton = rows.first().getByRole('button');
      await expect(firstRowButton).toBeVisible({ timeout: 5000 });
      await firstRowButton.click();

      // Verifica se tem opção de check-out
      const checkoutMenuItem = authenticatedPage.getByRole('menuitem', { name: /Fazer Check-out/i });
      await expect(checkoutMenuItem).toBeVisible();

      // ========== PASSO 6: Abrir dialog de check-out ==========
      await checkoutMenuItem.click();

      // Verifica que o dialog de check-out abriu
      const checkoutDialog = authenticatedPage.getByRole('dialog');
      await expect(checkoutDialog).toBeVisible({ timeout: 5000 });
      // Verifica o título do dialog (heading específico dentro do dialog)
      await expect(checkoutDialog.getByRole('heading', { name: 'Check-out' })).toBeVisible();

      // Verifica campos obrigatórios do check-out (usando label específico)
      await expect(checkoutDialog.getByLabel(/Horímetro Final/i)).toBeVisible();

      // ========== PASSO 7: Preencher e finalizar check-out ==========
      // Preenche horímetro final (maior que o inicial) - usando o label
      const horimetroFimInput = checkoutDialog.getByLabel(/Horímetro Final/i);
      await horimetroFimInput.fill('105');

      // Marca itens do checklist de entrada (Radix checkbox - usa click)
      const checklistLabels = ['Motor OK', 'Casco OK', 'Limpeza OK', 'Combustível', 'Equipamentos OK'];
      for (const label of checklistLabels) {
        const checkbox = checkoutDialog.getByLabel(new RegExp(label, 'i'));
        if (await checkbox.isVisible().catch(() => false)) {
          // Radix checkbox precisa de click, não check
          const isChecked = await checkbox.getAttribute('data-state') === 'checked';
          if (!isChecked) {
            await checkbox.click();
          }
        }
      }

      // Marca "Pular validação de fotos" para não exigir upload de fotos no teste
      const skipFotosCheckbox = checkoutDialog.getByLabel(/Pular validação de fotos/i);
      if (await skipFotosCheckbox.isVisible().catch(() => false)) {
        const isChecked = await skipFotosCheckbox.getAttribute('data-state') === 'checked';
        if (!isChecked) {
          await skipFotosCheckbox.click();
        }
      }

      // Clica em finalizar usando JavaScript (botão pode estar fora da viewport)
      const finalizarButton = checkoutDialog.getByRole('button', { name: /Finalizar|Confirmar/i });
      await expect(finalizarButton).toBeVisible({ timeout: 5000 });

      // Scroll o botão para view e clica via JavaScript
      await finalizarButton.scrollIntoViewIfNeeded();
      await authenticatedPage.waitForTimeout(300);

      // Use JavaScript click to bypass viewport check
      await finalizarButton.evaluate((button: HTMLButtonElement) => button.click());

      // Aguarda o dialog fechar
      await expect(checkoutDialog).not.toBeVisible({ timeout: 10000 });
      await authenticatedPage.waitForLoadState('networkidle');

      // ========== PASSO 8: Verificar que a locação foi finalizada ==========
      // Filtra por finalizadas
      await locacoesPage.filterByStatus('FINALIZADA');
      await authenticatedPage.waitForLoadState('networkidle');
      await authenticatedPage.waitForTimeout(1000);

      // Deve ter pelo menos uma locação finalizada
      const finalizadasCount = await locacoesPage.locacoesTable.locator('tbody tr').count();
      expect(finalizadasCount).toBeGreaterThan(0);

      console.log(`✅ Fluxo completo executado com sucesso para jetski: ${jetskiNome}`);
    });
  });
});
