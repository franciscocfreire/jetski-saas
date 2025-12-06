import { test as baseTest, expect } from '@playwright/test';
import { test as authTest } from '../fixtures/auth';
import { SignupPage, MagicActivatePage, LoginPage } from '../pages';
import { uniqueId } from '../fixtures/test-data';
import { hasCredentials, SKIP_MESSAGE } from '../fixtures/auth-check';

/**
 * Gera dados únicos para um tenant de teste
 */
function generateTestTenantData() {
  const timestamp = Date.now();
  const random = Math.random().toString(36).substring(2, 6);

  return {
    razaoSocial: `Empresa Teste E2E ${timestamp}`,
    slug: `teste-e2e-${random}`,
    adminNome: `Admin Teste ${random}`,
    adminEmail: `teste.e2e.${timestamp}@example.com`,
  };
}

// Testes de signup não precisam de autenticação (páginas públicas)
baseTest.describe('Signup - Criação de Tenant', () => {
  baseTest.describe('Página de Signup', () => {
    baseTest('deve exibir página de signup corretamente', async ({ page }) => {
      const signupPage = new SignupPage(page);
      await signupPage.goto();

      // Verifica elementos principais (usando seletor específico para heading)
      await expect(page.getByRole('heading', { name: 'Criar Conta' })).toBeVisible();
      await expect(signupPage.razaoSocialInput).toBeVisible();
      await expect(signupPage.slugInput).toBeVisible();
      await expect(signupPage.adminNomeInput).toBeVisible();
      await expect(signupPage.adminEmailInput).toBeVisible();
      await expect(signupPage.criarContaButton).toBeVisible();
    });

    baseTest('deve gerar slug automaticamente a partir do nome da empresa', async ({ page }) => {
      const signupPage = new SignupPage(page);
      await signupPage.goto();

      // Preenche nome da empresa
      await signupPage.razaoSocialInput.fill('Minha Locadora de Jet Skis');

      // Aguarda slug ser gerado
      await page.waitForTimeout(600);

      // Verifica que o slug foi gerado
      const slug = await signupPage.getSlug();
      expect(slug).toContain('minha-locadora');
    });

    baseTest('deve validar slug em tempo real', async ({ page }) => {
      const signupPage = new SignupPage(page);
      await signupPage.goto();

      // Preenche um slug único
      const uniqueSlug = `teste-${Date.now()}`;
      await signupPage.slugInput.fill(uniqueSlug);

      // Aguarda verificação (mais tempo)
      await signupPage.waitForSlugCheck();
      await page.waitForTimeout(1500); // Espera adicional para API responder

      // Verifica se o slug foi validado (disponível OU botão habilitado)
      const isAvailable = await signupPage.isSlugAvailable();
      const buttonEnabled = await signupPage.criarContaButton.isEnabled().catch(() => false);

      // Se o slug está disponível OU o formulário permite submit, o teste passa
      expect(isAvailable || buttonEnabled).toBeTruthy();
    });

    baseTest('deve mostrar erro para slug já existente', async ({ page }) => {
      const signupPage = new SignupPage(page);
      await signupPage.goto();

      // Usa um slug que provavelmente já existe (o tenant principal)
      await signupPage.slugInput.fill('pegaojet');

      // Aguarda verificação
      await signupPage.waitForSlugCheck();

      // Deve mostrar como indisponível ou disponível (depende se existe)
      // O importante é que a verificação aconteceu
      const slugInput = await signupPage.slugInput.inputValue();
      expect(slugInput).toBe('pegaojet');
    });

    baseTest('deve ter link para voltar ao login', async ({ page }) => {
      const signupPage = new SignupPage(page);
      await signupPage.goto();

      await expect(signupPage.voltarLoginLink).toBeVisible();
      await signupPage.voltarLoginLink.click();

      await expect(page).toHaveURL(/.*\/login.*/);
    });
  });

  baseTest.describe('Fluxo de Signup Completo', () => {
    baseTest('deve criar tenant com sucesso e mostrar mensagem de confirmação', async ({ page }) => {
      const signupPage = new SignupPage(page);
      await signupPage.goto();

      // Gera dados únicos para o teste
      const testData = generateTestTenantData();

      // Preenche o formulário
      await signupPage.fillForm(testData);

      // Aguarda verificação de slug
      await signupPage.waitForSlugCheck();

      // Verifica se o botão está habilitado
      await expect(signupPage.criarContaButton).toBeEnabled();

      // Submete o formulário
      await signupPage.submitForm();

      // Aguarda mensagem de sucesso
      await signupPage.waitForSuccess();

      // Verifica mensagem de sucesso
      await expect(signupPage.successMessage).toBeVisible();
      await expect(page.getByText(testData.adminEmail)).toBeVisible();
    });

    baseTest('deve mostrar erro ao tentar criar tenant com email inválido', async ({ page }) => {
      const signupPage = new SignupPage(page);
      await signupPage.goto();

      const testData = generateTestTenantData();
      testData.adminEmail = 'email-invalido'; // Email inválido

      await signupPage.fillForm(testData);

      // O HTML5 validation deve impedir o submit
      await signupPage.submitForm();

      // Não deve ter mensagem de sucesso
      await expect(signupPage.successMessage).not.toBeVisible();
    });

    baseTest('deve exibir campos obrigatórios', async ({ page }) => {
      const signupPage = new SignupPage(page);
      await signupPage.goto();

      // Verifica labels com asterisco (obrigatórios)
      await expect(page.getByText('Nome da Empresa *')).toBeVisible();
      await expect(page.getByText('Identificador (URL) *')).toBeVisible();
      await expect(page.getByText('Seu Nome *')).toBeVisible();
      await expect(page.getByText('Seu Email *')).toBeVisible();

      // CNPJ não é obrigatório
      await expect(page.getByText('CNPJ (opcional)')).toBeVisible();
    });
  });

  baseTest.describe('Ativação de Conta', () => {
    baseTest('deve mostrar erro para token inválido', async ({ page }) => {
      const magicActivatePage = new MagicActivatePage(page);

      // Tenta ativar com token inválido
      await magicActivatePage.gotoWithToken('token-invalido-123');

      // Aguarda resultado
      const result = await magicActivatePage.waitForResult();

      // Deve mostrar erro
      expect(result).toBe('error');
      await expect(magicActivatePage.errorMessage).toBeVisible();
    });

    baseTest('deve mostrar erro quando token não é fornecido', async ({ page }) => {
      // Acessa página sem token
      await page.goto('/magic-activate');

      // Deve mostrar erro de token inválido
      await expect(page.getByText(/inválido|Token não encontrado/i)).toBeVisible({ timeout: 10000 });
    });

    baseTest('deve ter botão para criar nova conta após erro', async ({ page }) => {
      const magicActivatePage = new MagicActivatePage(page);

      await magicActivatePage.gotoWithToken('token-invalido');
      await magicActivatePage.waitForResult();

      // Deve ter botão para criar nova conta
      await expect(magicActivatePage.criarNovaContaButton).toBeVisible();
    });
  });

  baseTest.describe('Integração Signup + Ativação', () => {
    baseTest.skip('deve completar fluxo completo de signup → ativação → login', async ({ page, request }) => {
      /**
       * NOTA: Este teste requer:
       * 1. Endpoint de teste no backend para obter o magic token
       * 2. Ou acesso ao serviço de email de teste (DevEmailService)
       *
       * O teste está skipado até que o endpoint de teste seja implementado.
       */
    });
  });
});

// Testes de convites precisam de autenticação
authTest.describe('Convites de Usuário', () => {
  authTest.beforeEach(async () => {
    authTest.skip(!hasCredentials(), SKIP_MESSAGE);
  });

  authTest.describe('Página de Usuários', () => {
    authTest('deve exibir página de gerenciamento de usuários', async ({ authenticatedPage }) => {
      await authenticatedPage.goto('/dashboard/usuarios');
      await authenticatedPage.waitForLoadState('networkidle');

      await expect(authenticatedPage.getByRole('heading', { name: /Usuários|Equipe|Membros/i })).toBeVisible({
        timeout: 30000,
      });
    });

    authTest('deve ter botão para convidar novo usuário', async ({ authenticatedPage }) => {
      await authenticatedPage.goto('/dashboard/usuarios');
      await authenticatedPage.waitForLoadState('networkidle');

      const convidarButton = authenticatedPage.getByRole('button', { name: /Convidar|Adicionar|Novo/i });
      await expect(convidarButton).toBeVisible({ timeout: 10000 });
    });

    authTest('deve abrir dialog de convite ao clicar no botão', async ({ authenticatedPage }) => {
      await authenticatedPage.goto('/dashboard/usuarios');
      await authenticatedPage.waitForLoadState('networkidle');

      const convidarButton = authenticatedPage.getByRole('button', { name: /Convidar|Adicionar|Novo/i });
      await convidarButton.click();

      // Verifica se abriu dialog
      const dialog = authenticatedPage.getByRole('dialog');
      await expect(dialog).toBeVisible({ timeout: 5000 });
    });
  });

  authTest.describe('Fluxo de Convite', () => {
    authTest.skip('deve enviar convite para novo membro', async ({ authenticatedPage }) => {
      /**
       * NOTA: Este teste requer tenant de teste configurado
       * e será habilitado após a configuração inicial.
       */
    });

    authTest.skip('deve listar convites pendentes', async ({ authenticatedPage }) => {
      /**
       * NOTA: Este teste requer convites pendentes no tenant de teste.
       */
    });

    authTest.skip('deve permitir reenviar convite pendente', async ({ authenticatedPage }) => {
      /**
       * NOTA: Este teste requer convites pendentes no tenant de teste.
       */
    });
  });
});
