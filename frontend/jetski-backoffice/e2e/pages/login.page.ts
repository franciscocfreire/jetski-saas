import { Page, Locator } from '@playwright/test';

/**
 * Page Object do login.
 *
 * O `/login` do app é TRAMPOLIM: sem sessão ele salta direto para o Keycloak,
 * que é quem tem a tela de credenciais. Essa tela é identifier-first (tema
 * meujet): primeiro o e-mail/CPF, depois senha ou código por e-mail — os
 * seletores clássicos `#username`/`#kc-login` não existem mais.
 *
 * O card do próprio app só aparece no caminho de falha (`?error=`, ou sessão
 * morta), onde ele NÃO redireciona — é a guarda anti-loop.
 */
export class LoginPage {
  readonly page: Page;

  // Card do app (só no caminho de erro)
  readonly loginButton: Locator;
  readonly signupLink: Locator;
  readonly welcomeText: Locator;

  // Tela do Keycloak — passo 1 (identificador)
  readonly identifierInput: Locator;
  readonly continuarButton: Locator;
  readonly criarContaLink: Locator;
  readonly socialProviders: Locator;

  // Tela do Keycloak — passo 2 (senha ou código)
  readonly passwordInput: Locator;
  readonly entrarComSenhaButton: Locator;
  readonly erroAlert: Locator;

  constructor(page: Page) {
    this.page = page;

    this.loginButton = page.getByRole('button', { name: /Entrar com sua conta/i });
    this.signupLink = page.getByRole('link', { name: /Criar Conta Gratuita/i });
    this.welcomeText = page.getByText(/Bem-vindo de volta/i);

    this.identifierInput = page.locator('#identifier');
    this.continuarButton = page.locator('#mj-send-code');
    this.criarContaLink = page.locator('#mj-create-account');
    this.socialProviders = page.locator('#kc-social-providers');

    this.passwordInput = page.locator('#password');
    this.entrarComSenhaButton = page.locator('#mj-login-password');
    this.erroAlert = page.locator('.mj-alert--error');
  }

  /** Vai ao /login e espera a tela de credenciais do Keycloak. */
  async goto() {
    await this.page.goto('/login');
    await this.page.waitForURL(/\/realms\/[^/]+\/protocol\/openid-connect\/auth/, {
      timeout: 30000,
    });
    await this.identifierInput.waitFor({ state: 'visible', timeout: 15000 });
  }

  /** Card manual do app (sessão expirada / falha de auth) — não redireciona. */
  async gotoCardDeErro() {
    await this.page.goto('/login?error=SessionExpired');
    await this.welcomeText.waitFor({ state: 'visible', timeout: 20000 });
  }

  /** Passo 1: informa o identificador e avança para senha/código. */
  async informarIdentificador(identificador: string) {
    await this.identifierInput.fill(identificador);
    await this.continuarButton.click();
    await this.passwordInput.waitFor({ state: 'visible', timeout: 15000 });
  }

  /** Login completo pelo caminho da senha. */
  async login(identificador: string, password: string) {
    await this.goto();
    await this.informarIdentificador(identificador);
    await this.passwordInput.fill(password);
    await this.entrarComSenhaButton.click();
    await this.page.waitForURL(/.*\/dashboard.*/, { timeout: 30000 });
  }

  /** Navega para a criação de conta (link da própria tela do Keycloak). */
  async goToSignup() {
    await this.criarContaLink.click();
    await this.page.waitForURL(/.*\/signup.*/);
  }
}
