import { Page, Locator } from '@playwright/test';

/**
 * Page Object para a página de Locações
 */
export class LocacoesPage {
  readonly page: Page;
  readonly pageTitle: Locator;

  // Ações principais
  readonly novoCheckInButton: Locator;
  readonly searchInput: Locator;
  readonly statusFilter: Locator;

  // Tabela de locações
  readonly locacoesTable: Locator;
  readonly emptyMessage: Locator;

  // Dialog de Check-in
  readonly checkInDialog: Locator;
  readonly jetskiSelect: Locator;
  readonly duracaoSelect: Locator;
  readonly horimetroInput: Locator;
  readonly clienteSelect: Locator;
  readonly vendedorSelect: Locator;
  readonly iniciarLocacaoButton: Locator;
  readonly cancelarButton: Locator;

  // Checklist items no check-in
  readonly motorOkCheckbox: Locator;
  readonly cascoOkCheckbox: Locator;
  readonly combustivelOkCheckbox: Locator;
  readonly equipamentosOkCheckbox: Locator;

  // Dialog de Check-out
  readonly checkOutDialog: Locator;
  readonly horimetroFimInput: Locator;
  readonly finalizarLocacaoButton: Locator;

  // Status badges
  readonly emCursoStatus: Locator;
  readonly finalizadaStatus: Locator;

  constructor(page: Page) {
    this.page = page;
    this.pageTitle = page.getByRole('heading', { name: 'Locações' });

    // Ações principais
    this.novoCheckInButton = page.getByRole('button', { name: /Novo Check-in/i });
    this.searchInput = page.getByPlaceholder(/Buscar por jetski ou cliente/i);
    this.statusFilter = page.locator('button').filter({ hasText: /Filtrar por status|Todas|Em curso|Finalizadas/i });

    // Tabela
    this.locacoesTable = page.getByRole('table');
    this.emptyMessage = page.getByText(/Nenhuma locação encontrada/i);

    // Dialog de Check-in
    this.checkInDialog = page.getByRole('dialog');
    this.jetskiSelect = page.getByRole('combobox', { name: '' }).first();
    this.duracaoSelect = page.locator('[id*="duracao"]');
    this.horimetroInput = page.locator('input#horimetro');
    this.clienteSelect = page.locator('[data-testid="cliente-select"]');
    this.vendedorSelect = page.locator('[data-testid="vendedor-select"]');
    this.iniciarLocacaoButton = page.getByRole('button', { name: /Iniciar Locação/i });
    this.cancelarButton = page.getByRole('button', { name: 'Cancelar' });

    // Checklist
    this.motorOkCheckbox = page.getByLabel('Motor OK');
    this.cascoOkCheckbox = page.getByLabel('Casco OK');
    this.combustivelOkCheckbox = page.getByLabel('Combustível OK');
    this.equipamentosOkCheckbox = page.getByLabel('Equipamentos OK');

    // Dialog Check-out
    this.checkOutDialog = page.getByRole('dialog').filter({ hasText: 'Check-out' });
    this.horimetroFimInput = page.locator('input#horimetroFim');
    this.finalizarLocacaoButton = page.getByRole('button', { name: /Finalizar Locação/i });

    // Status
    this.emCursoStatus = page.locator('span:has-text("Em curso")');
    this.finalizadaStatus = page.locator('span:has-text("Finalizada")');
  }

  /**
   * Navega para a página de locações
   */
  async goto() {
    await this.page.goto('/dashboard/locacoes');
    await this.pageTitle.waitFor({ state: 'visible', timeout: 30000 });
  }

  /**
   * Verifica se a página está carregada
   */
  async isLoaded(): Promise<boolean> {
    try {
      await this.pageTitle.waitFor({ state: 'visible', timeout: 10000 });
      return true;
    } catch {
      return false;
    }
  }

  /**
   * Abre o dialog de novo check-in
   */
  async openCheckInDialog() {
    await this.novoCheckInButton.click();
    await this.checkInDialog.waitFor({ state: 'visible' });
  }

  /**
   * Seleciona um jetski no dropdown
   */
  async selectJetski(jetskiName: string) {
    // Clica no select de jetski
    await this.page.getByText('Selecione um jetski disponível').click();
    // Seleciona o jetski pelo nome
    await this.page.getByRole('option', { name: new RegExp(jetskiName, 'i') }).click();
  }

  /**
   * Seleciona a duração prevista
   */
  async selectDuracao(duracao: string) {
    await this.page.getByRole('combobox').filter({ hasText: /hora|min/i }).click();
    await this.page.getByRole('option', { name: duracao }).click();
  }

  /**
   * Preenche o horímetro inicial
   */
  async setHorimetroInicial(valor: number) {
    await this.horimetroInput.fill(valor.toString());
  }

  /**
   * Marca todos os itens do checklist de saída
   */
  async checkAllChecklistItems() {
    await this.motorOkCheckbox.check();
    await this.cascoOkCheckbox.check();
    await this.combustivelOkCheckbox.check();
    await this.equipamentosOkCheckbox.check();
  }

  /**
   * Realiza um check-in walk-in completo
   */
  async performWalkInCheckIn(options: {
    jetskiName: string;
    duracao?: string;
    horimetro?: number;
  }) {
    await this.openCheckInDialog();
    await this.selectJetski(options.jetskiName);

    if (options.duracao) {
      await this.selectDuracao(options.duracao);
    }

    if (options.horimetro !== undefined) {
      await this.setHorimetroInicial(options.horimetro);
    }

    await this.checkAllChecklistItems();
    await this.iniciarLocacaoButton.click();

    // Aguardar dialog fechar
    await this.checkInDialog.waitFor({ state: 'hidden', timeout: 10000 });
  }

  /**
   * Realiza check-out de uma locação
   */
  async performCheckOut(locacaoIndex: number, horimetroFim: number) {
    // Clica no menu de ações da locação
    const row = this.locacoesTable.locator('tbody tr').nth(locacaoIndex);
    await row.getByRole('button', { name: '' }).click();

    // Clica em "Fazer Check-out"
    await this.page.getByRole('menuitem', { name: /Fazer Check-out/i }).click();

    // Aguarda dialog
    await this.checkOutDialog.waitFor({ state: 'visible' });

    // Preenche horímetro final
    await this.horimetroFimInput.fill(horimetroFim.toString());

    // Marca todos os itens do checklist de entrada
    await this.page.getByLabel('Motor OK').check();
    await this.page.getByLabel('Casco OK').check();
    await this.page.getByLabel('Limpeza OK').check();
    await this.page.getByLabel('Combustível verificado').check();
    await this.page.getByLabel('Equipamentos OK').check();

    // Finaliza
    await this.finalizarLocacaoButton.click();
    await this.checkOutDialog.waitFor({ state: 'hidden', timeout: 10000 });
  }

  /**
   * Filtra locações por status
   */
  async filterByStatus(status: 'all' | 'EM_CURSO' | 'FINALIZADA' | 'CANCELADA') {
    await this.page.getByRole('combobox').filter({ hasText: /Todas|Em curso|Finalizadas|Canceladas/i }).click();

    const statusLabels = {
      all: 'Todas',
      EM_CURSO: 'Em curso',
      FINALIZADA: 'Finalizadas',
      CANCELADA: 'Canceladas',
    };

    await this.page.getByRole('option', { name: statusLabels[status] }).click();
  }

  /**
   * Busca locações por texto
   */
  async search(text: string) {
    await this.searchInput.fill(text);
  }

  /**
   * Obtém o número de linhas na tabela de locações
   */
  async getLocacoesCount(): Promise<number> {
    // Aguardar carregamento
    await this.page.waitForTimeout(500);
    const rows = this.locacoesTable.locator('tbody tr');
    return await rows.count();
  }

  /**
   * Verifica se há locações em curso
   */
  async hasLocacoesEmCurso(): Promise<boolean> {
    const count = await this.emCursoStatus.count();
    return count > 0;
  }
}
