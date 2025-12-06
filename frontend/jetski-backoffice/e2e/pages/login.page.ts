import { Page, Locator } from '@playwright/test';

/**
 * Page Object para a página de Login
 */
export class LoginPage {
  readonly page: Page;
  readonly loginButton: Locator;
  readonly signupLink: Locator;
  readonly welcomeText: Locator;

  constructor(page: Page) {
    this.page = page;
    this.loginButton = page.getByRole('button', { name: /Entrar com sua conta/i });
    this.signupLink = page.getByRole('link', { name: /Criar Conta Gratuita/i });
    this.welcomeText = page.getByText(/Bem-vindo de volta/i);
  }

  /**
   * Navega para a página de login
   */
  async goto() {
    await this.page.goto('/login');
    await this.welcomeText.waitFor({ state: 'visible' });
  }

  /**
   * Clica no botão de login (redireciona para Keycloak)
   */
  async clickLogin() {
    await this.loginButton.click();
  }

  /**
   * Faz login completo via Keycloak
   */
  async login(email: string, password: string) {
    await this.clickLogin();

    // Aguardar redirecionamento para Keycloak
    await this.page.waitForURL(/.*\/realms\/.*\/protocol\/openid-connect\/auth.*/, {
      timeout: 15000,
    });

    // Preencher credenciais no Keycloak
    await this.page.fill('#username', email);
    await this.page.fill('#password', password);
    await this.page.click('#kc-login');

    // Aguardar redirecionamento para dashboard
    await this.page.waitForURL(/.*\/dashboard.*/, {
      timeout: 30000,
    });
  }

  /**
   * Navega para página de signup
   */
  async goToSignup() {
    await this.signupLink.click();
    await this.page.waitForURL(/.*\/signup.*/);
  }
}
