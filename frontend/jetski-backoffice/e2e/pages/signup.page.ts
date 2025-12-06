import { Page, Locator } from '@playwright/test';

/**
 * Page Object para a página de Signup
 */
export class SignupPage {
  readonly page: Page;

  // Campos do formulário
  readonly razaoSocialInput: Locator;
  readonly slugInput: Locator;
  readonly cnpjInput: Locator;
  readonly adminNomeInput: Locator;
  readonly adminEmailInput: Locator;

  // Botões
  readonly criarContaButton: Locator;
  readonly voltarLoginLink: Locator;

  // Indicadores de slug
  readonly slugAvailableIcon: Locator;
  readonly slugUnavailableIcon: Locator;
  readonly slugLoadingIcon: Locator;

  // Mensagens
  readonly successMessage: Locator;
  readonly errorAlert: Locator;
  readonly slugErrorMessage: Locator;

  constructor(page: Page) {
    this.page = page;

    // Campos do formulário
    this.razaoSocialInput = page.locator('input#razaoSocial');
    this.slugInput = page.locator('input#slug');
    this.cnpjInput = page.locator('input#cnpj');
    this.adminNomeInput = page.locator('input#adminNome');
    this.adminEmailInput = page.locator('input#adminEmail');

    // Botões
    this.criarContaButton = page.getByRole('button', { name: /Criar Conta Gratuita/i });
    this.voltarLoginLink = page.getByRole('link', { name: /Fazer login/i });

    // Indicadores de slug (SVG icons) - múltiplos seletores possíveis
    this.slugAvailableIcon = page.locator('[class*="text-green"], [class*="success"], svg.text-green-500, svg.text-green-600').first();
    this.slugUnavailableIcon = page.locator('[class*="text-red"], [class*="error"], svg.text-red-500, svg.text-red-600').first();
    this.slugLoadingIcon = page.locator('.animate-spin, [class*="loading"]');

    // Mensagens
    this.successMessage = page.getByText(/Cadastro Realizado!/i);
    this.errorAlert = page.locator('[role="alert"]');
    this.slugErrorMessage = page.getByText(/Este identificador já está em uso/i);
  }

  /**
   * Navega para a página de signup
   */
  async goto() {
    await this.page.goto('/signup');
    await this.page.waitForLoadState('networkidle');
  }

  /**
   * Verifica se a página está carregada
   */
  async isLoaded(): Promise<boolean> {
    try {
      await this.razaoSocialInput.waitFor({ state: 'visible', timeout: 10000 });
      return true;
    } catch {
      return false;
    }
  }

  /**
   * Preenche o formulário de signup
   */
  async fillForm(data: {
    razaoSocial: string;
    slug?: string;
    cnpj?: string;
    adminNome: string;
    adminEmail: string;
  }) {
    await this.razaoSocialInput.fill(data.razaoSocial);

    // Aguarda slug ser gerado automaticamente
    await this.page.waitForTimeout(600);

    // Se forneceu slug específico, sobrescreve
    if (data.slug) {
      await this.slugInput.clear();
      await this.slugInput.fill(data.slug);
    }

    if (data.cnpj) {
      await this.cnpjInput.fill(data.cnpj);
    }

    await this.adminNomeInput.fill(data.adminNome);
    await this.adminEmailInput.fill(data.adminEmail);
  }

  /**
   * Submete o formulário de signup
   */
  async submitForm() {
    await this.criarContaButton.click();
  }

  /**
   * Aguarda verificação de disponibilidade do slug
   */
  async waitForSlugCheck() {
    // Aguarda loading terminar
    await this.slugLoadingIcon.waitFor({ state: 'hidden', timeout: 5000 }).catch(() => { });

    // Aguarda um dos indicadores aparecer
    await this.page.waitForTimeout(1000);
  }

  /**
   * Verifica se o slug está disponível
   */
  async isSlugAvailable(): Promise<boolean> {
    await this.waitForSlugCheck();
    return await this.slugAvailableIcon.isVisible().catch(() => false);
  }

  /**
   * Verifica se o slug está indisponível
   */
  async isSlugUnavailable(): Promise<boolean> {
    await this.waitForSlugCheck();
    return await this.slugUnavailableIcon.isVisible().catch(() => false);
  }

  /**
   * Realiza signup completo
   */
  async signup(data: {
    razaoSocial: string;
    slug?: string;
    cnpj?: string;
    adminNome: string;
    adminEmail: string;
  }) {
    await this.fillForm(data);
    await this.waitForSlugCheck();
    await this.submitForm();
  }

  /**
   * Aguarda mensagem de sucesso
   */
  async waitForSuccess() {
    await this.successMessage.waitFor({ state: 'visible', timeout: 30000 });
  }

  /**
   * Aguarda mensagem de erro
   */
  async waitForError() {
    await this.errorAlert.waitFor({ state: 'visible', timeout: 10000 });
  }

  /**
   * Obtém o slug atual (gerado ou inserido)
   */
  async getSlug(): Promise<string> {
    return await this.slugInput.inputValue();
  }
}

/**
 * Page Object para a página de Magic Activate
 */
export class MagicActivatePage {
  readonly page: Page;

  // Status messages
  readonly loadingMessage: Locator;
  readonly successMessage: Locator;
  readonly expiredMessage: Locator;
  readonly errorMessage: Locator;

  // Botões
  readonly irParaLoginButton: Locator;
  readonly criarNovaContaButton: Locator;
  readonly voltarLoginButton: Locator;

  constructor(page: Page) {
    this.page = page;

    this.loadingMessage = page.getByText(/Ativando sua conta/i);
    this.successMessage = page.getByText(/Conta Ativada!/i);
    this.expiredMessage = page.getByText(/Link Expirado/i);
    this.errorMessage = page.getByText(/Erro na Ativação/i);

    this.irParaLoginButton = page.getByRole('button', { name: /Ir para Login/i });
    this.criarNovaContaButton = page.getByRole('button', { name: /Criar Nova Conta/i });
    this.voltarLoginButton = page.getByRole('button', { name: /Voltar para Login/i });
  }

  /**
   * Navega para a página de ativação com token
   */
  async gotoWithToken(token: string) {
    await this.page.goto(`/magic-activate?token=${token}`);
  }

  /**
   * Aguarda resultado da ativação
   */
  async waitForResult(): Promise<'success' | 'expired' | 'error'> {
    // Aguarda loading terminar
    await this.loadingMessage.waitFor({ state: 'hidden', timeout: 30000 }).catch(() => { });

    // Verifica qual resultado apareceu
    if (await this.successMessage.isVisible().catch(() => false)) {
      return 'success';
    }
    if (await this.expiredMessage.isVisible().catch(() => false)) {
      return 'expired';
    }
    return 'error';
  }

  /**
   * Clica em "Ir para Login" após ativação bem-sucedida
   */
  async goToLogin() {
    await this.irParaLoginButton.click();
    await this.page.waitForURL(/.*\/login.*/);
  }
}
