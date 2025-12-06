import { test as base, Page } from '@playwright/test';

/**
 * Fixture de autenticação que fornece helpers para login/logout
 */
export interface AuthFixture {
  /**
   * Faz login com credenciais específicas (útil para testes de login)
   */
  login: (email: string, password: string) => Promise<void>;

  /**
   * Faz logout da sessão atual
   */
  logout: () => Promise<void>;

  /**
   * Verifica se o usuário está autenticado
   */
  isAuthenticated: () => Promise<boolean>;
}

export const test = base.extend<{ auth: AuthFixture }>({
  auth: async ({ page }, use) => {
    const auth: AuthFixture = {
      async login(email: string, password: string) {
        const baseURL = process.env.PLAYWRIGHT_BASE_URL || 'https://pegaojet.com.br';

        // Navegar para login
        await page.goto(`${baseURL}/login`);

        // Clicar no botão de login
        const loginButton = page.locator('button:has-text("Entrar"), button:has-text("Login")');
        await loginButton.click();

        // Aguardar Keycloak
        await page.waitForURL(/.*\/realms\/.*/, { timeout: 15000 });

        // Preencher credenciais
        await page.fill('#username', email);
        await page.fill('#password', password);
        await page.click('#kc-login');

        // Aguardar redirect
        await page.waitForURL(/.*\/dashboard.*/, { timeout: 30000 });
      },

      async logout() {
        const baseURL = process.env.PLAYWRIGHT_BASE_URL || 'https://pegaojet.com.br';

        // Clicar no menu de usuário e depois em logout
        // Ajustar seletores conforme a UI real
        await page.goto(`${baseURL}/logout`);
        await page.waitForURL(/.*\/login.*/, { timeout: 15000 });
      },

      async isAuthenticated() {
        // Verificar se está no dashboard ou tem token
        const url = page.url();
        return url.includes('/dashboard');
      },
    };

    await use(auth);
  },
});

export { expect } from '@playwright/test';
