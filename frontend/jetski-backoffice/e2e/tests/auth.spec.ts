import { test as baseTest, expect } from '@playwright/test';
import { test as authTest } from '../fixtures/auth';
import { LoginPage, DashboardPage } from '../pages';
import { hasCredentials, SKIP_MESSAGE } from '../fixtures/auth-check';

baseTest.describe('Autenticação', () => {
  baseTest.describe('Login', () => {
    // O /login é trampolim: quem mostra credenciais é o Keycloak, em duas etapas
    // (identifier-first). Estes testes falhavam desde a troca de tema porque
    // esperavam a tela antiga (#username/#kc-login e o card do app, que hoje só
    // aparece no caminho de erro).
    baseTest('login leva à tela de credenciais do Keycloak', async ({ page }) => {
      const loginPage = new LoginPage(page);
      await loginPage.goto();

      await expect(page).toHaveURL(/.*\/realms\/.*\/protocol\/openid-connect\/auth.*/);
      await expect(loginPage.identifierInput).toBeVisible();
      await expect(loginPage.continuarButton).toBeVisible();
    });

    baseTest('a tela de credenciais oferece criar conta e provedor social', async ({ page }) => {
      const loginPage = new LoginPage(page);
      await loginPage.goto();

      await expect(loginPage.criarContaLink).toBeVisible();
      await expect(loginPage.socialProviders).toBeVisible();
    });

    baseTest('senha errada mostra erro na tela do Keycloak', async ({ page }) => {
      const loginPage = new LoginPage(page);
      await loginPage.goto();

      // Identificador desconhecido avança do mesmo jeito (anti-enumeração):
      // a tela de senha aparece e o erro só surge ao submeter.
      await loginPage.informarIdentificador('usuario-invalido@example.com');
      await loginPage.passwordInput.fill('senha-errada');
      await loginPage.entrarComSenhaButton.click();

      await expect(loginPage.erroAlert).toBeVisible({ timeout: 15000 });
    });
  });

  // Regressão do loop infinito (HAR de 12/set/2026): com a sessão morta no
  // Keycloak, o app ia /login -> /dashboard -> 401 -> signOut -> /login, ~1 volta
  // por segundo, para sempre. A guarda é o /login PARAR quando a chegada está
  // marcada como falha de sessão, em vez de tentar entrar de novo.
  baseTest('login marcado com erro não entra em loop', async ({ page }) => {
    await page.goto('/login?error=SessionExpired');

    // Mostra o card manual em vez de disparar novo fluxo OIDC...
    await expect(page.getByText(/Bem-vindo de volta/i)).toBeVisible({ timeout: 20000 });

    // ...e continua parado: nada de bater no Keycloak nem voltar ao dashboard.
    await page.waitForTimeout(4000);
    await expect(page).toHaveURL(/\/login/);
    await expect(page).not.toHaveURL(/\/dashboard/);
  });

  baseTest.describe('Proteção de Rotas', () => {
    baseTest('deve redirecionar para login quando não autenticado', async ({ page }) => {
      // Limpar estado de autenticação
      await page.context().clearCookies();

      // Tentar acessar dashboard diretamente
      await page.goto('/dashboard');

      // Deve redirecionar para login
      await expect(page).toHaveURL(/.*\/login.*|.*\/auth.*/, { timeout: 15000 });
    });

    baseTest('deve redirecionar para login ao acessar locações sem autenticação', async ({ page }) => {
      await page.context().clearCookies();
      await page.goto('/dashboard/locacoes');

      await expect(page).toHaveURL(/.*\/login.*|.*\/auth.*/, { timeout: 15000 });
    });
  });
});

// Testes que requerem autenticação
authTest.describe('Autenticação - Sessão Autenticada', () => {
  authTest.beforeEach(async () => {
    authTest.skip(!hasCredentials(), SKIP_MESSAGE);
  });

  authTest('deve fazer login com sucesso e estar no dashboard', async ({ authenticatedPage }) => {
    // authenticatedPage já está autenticado pelo fixture
    const dashboardPage = new DashboardPage(authenticatedPage);

    // Verifica que está no dashboard
    await expect(dashboardPage.pageTitle).toBeVisible();
  });

  authTest('deve fazer logout e redirecionar para login', async ({ authenticatedPage }) => {
    // Navega para logout
    await authenticatedPage.goto('/logout');

    // Destino do logout: /login (trampolim) OU já a tela do Keycloak, para onde
    // o trampolim salta em seguida. Exigir só /login deixa o teste na mão do
    // timing — a janela em /login dura milissegundos.
    await expect(authenticatedPage).toHaveURL(
      /\/login|\/realms\/[^/]+\/protocol\/openid-connect\/auth/,
      { timeout: 30000 },
    );
  });

  // Regressão do "sair que não sai" (HAR de 12/set/2026): chegar em /login não
  // provava nada — o cookie ressuscitava logo depois e o trampolim devolvia a
  // pessoa ao dashboard. Parar EM /login também não serve de asserção: o /login
  // é trampolim e salta para o Keycloak em milissegundos (foi o que deixou a
  // primeira versão deste teste flaky). O que vale é a sessão estar morta DE
  // VERDADE e a pessoa não acabar dentro do app.
  authTest('logout mata a sessão e não devolve ao dashboard', async ({ authenticatedPage }) => {
    await authenticatedPage.goto('/logout');

    // Saiu da página de saída — destino legítimo é /login ou a tela do Keycloak.
    await authenticatedPage.waitForURL((url) => !url.pathname.startsWith('/logout'), {
      timeout: 30000,
    });

    // A sessão do Auth.js precisa estar vazia (o endpoint devolve "null").
    await expect
      .poll(
        async () => {
          const r = await authenticatedPage.request.get('/api/auth/session');
          const corpo = (await r.text()).trim();
          return corpo === 'null' || corpo === '{}';
        },
        { timeout: 15000, message: 'sessão do NextAuth continuou viva após o logout' },
      )
      .toBe(true);

    // E não pode haver bounce tardio para dentro do app.
    await authenticatedPage.waitForTimeout(3000);
    await expect(authenticatedPage).not.toHaveURL(/.*\/dashboard.*/);
  });

  authTest('deve exibir seletor de tenant no sidebar quando autenticado', async ({ authenticatedPage }) => {
    // Verifica se o sidebar tem informações do tenant/user
    // Procura pelo footer do sidebar onde geralmente fica o tenant selector ou info do usuário
    const sidebarFooter = authenticatedPage.locator('aside [data-sidebar="footer"]');
    const hasSidebarFooter = await sidebarFooter.isVisible().catch(() => false);

    if (hasSidebarFooter) {
      // Verifica se tem algum elemento clicável no footer (dropdown, botão, etc)
      const footerButton = sidebarFooter.locator('button, [role="button"]');
      const hasButton = await footerButton.count() > 0;
      expect(hasButton).toBeTruthy();
    } else {
      // Fallback: procura por qualquer elemento que pareça ser um seletor de tenant
      const tenantInfo = authenticatedPage.locator('[class*="tenant"], [class*="user"], aside .dropdown');
      const hasTenantInfo = await tenantInfo.count() > 0;
      expect(hasTenantInfo).toBeTruthy();
    }
  });
});
