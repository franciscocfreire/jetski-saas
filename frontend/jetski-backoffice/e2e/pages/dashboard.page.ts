import { Page, Locator } from '@playwright/test';

/**
 * Page Object para a página de Dashboard
 */
export class DashboardPage {
  readonly page: Page;
  readonly pageTitle: Locator;
  readonly tenantName: Locator;

  // Stat Cards
  readonly receitaHojeCard: Locator;
  readonly locacoesAtivasCard: Locator;
  readonly clientesCard: Locator;
  readonly receitaMensalCard: Locator;

  // Fleet Status
  readonly jetskisDisponiveisCard: Locator;
  readonly jetskisLocadosCard: Locator;
  readonly emManutencaoCard: Locator;

  // Ações Rápidas
  readonly novoCheckInLink: Locator;
  readonly novaReservaLink: Locator;
  readonly novoClienteLink: Locator;

  // Locações em curso
  readonly locacoesEmCursoSection: Locator;

  constructor(page: Page) {
    this.page = page;
    this.pageTitle = page.getByRole('heading', { name: 'Dashboard' });
    this.tenantName = page.locator('p.text-muted-foreground').first();

    // Cards de estatísticas (por texto do título)
    this.receitaHojeCard = page.locator('div').filter({ hasText: 'Receita Hoje' }).first();
    this.locacoesAtivasCard = page.locator('div').filter({ hasText: 'Locações Ativas' }).first();
    this.clientesCard = page.locator('div').filter({ hasText: /^Clientes$/ }).first();
    this.receitaMensalCard = page.locator('div').filter({ hasText: 'Receita Mensal' }).first();

    // Fleet status
    this.jetskisDisponiveisCard = page.locator('div').filter({ hasText: 'Jetskis Disponíveis' }).first();
    this.jetskisLocadosCard = page.locator('div').filter({ hasText: 'Jetskis Locados' }).first();
    this.emManutencaoCard = page.locator('div').filter({ hasText: 'Em Manutenção' }).first();

    // Ações rápidas
    this.novoCheckInLink = page.getByRole('link', { name: /Novo Check-in/i });
    this.novaReservaLink = page.getByRole('link', { name: /Nova Reserva/i });
    this.novoClienteLink = page.getByRole('link', { name: /Novo Cliente/i });

    // Seções
    this.locacoesEmCursoSection = page.locator('div').filter({ hasText: 'Locações em Curso' }).first();
  }

  /**
   * Navega para o dashboard
   */
  async goto() {
    await this.page.goto('/dashboard');
    await this.pageTitle.waitFor({ state: 'visible', timeout: 30000 });
  }

  /**
   * Verifica se o dashboard está carregado corretamente
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
   * Aguarda os dados do dashboard serem carregados (skeletons desaparecem)
   */
  async waitForDataLoad() {
    // Aguardar até que não haja mais skeletons visíveis
    await this.page.waitForFunction(() => {
      const skeletons = document.querySelectorAll('.animate-pulse');
      return skeletons.length === 0;
    }, { timeout: 30000 });
  }

  /**
   * Obtém o valor de receita do dia
   */
  async getReceitaHoje(): Promise<string> {
    const card = this.receitaHojeCard;
    const valueElement = card.locator('.text-3xl');
    return await valueElement.textContent() || '';
  }

  /**
   * Obtém o número de locações ativas
   */
  async getLocacoesAtivas(): Promise<number> {
    const card = this.locacoesAtivasCard;
    const valueElement = card.locator('.text-3xl');
    const text = await valueElement.textContent();
    return parseInt(text || '0', 10);
  }

  /**
   * Clica em "Novo Check-in"
   */
  async clickNovoCheckIn() {
    await this.novoCheckInLink.click();
    await this.page.waitForURL(/.*\/locacoes.*/);
  }

  /**
   * Clica em "Nova Reserva"
   */
  async clickNovaReserva() {
    await this.novaReservaLink.click();
    await this.page.waitForURL(/.*\/agenda.*/);
  }

  /**
   * Clica em "Novo Cliente"
   */
  async clickNovoCliente() {
    await this.novoClienteLink.click();
    await this.page.waitForURL(/.*\/clientes.*/);
  }

  /**
   * Obtém a lista de locações em curso visíveis
   */
  async getLocacoesEmCurso(): Promise<string[]> {
    const section = this.locacoesEmCursoSection;
    const items = section.locator('.rounded-lg.border.p-3');
    const count = await items.count();

    const locacoes: string[] = [];
    for (let i = 0; i < count; i++) {
      const text = await items.nth(i).locator('.font-semibold').first().textContent();
      if (text) locacoes.push(text);
    }
    return locacoes;
  }
}
