import { Page, APIRequestContext } from '@playwright/test';
import { SignupPage, MagicActivatePage, LoginPage } from '../pages';

const API_URL = process.env.PLAYWRIGHT_API_URL || 'https://pegaojet.com.br/api';

/**
 * Interface para dados de criação de tenant
 */
export interface TenantSignupData {
  razaoSocial: string;
  slug: string;
  adminNome: string;
  adminEmail: string;
  cnpj?: string;
}

/**
 * Gera dados únicos para um tenant de teste
 */
export function generateTestTenantData(): TenantSignupData {
  const timestamp = Date.now();
  const random = Math.random().toString(36).substring(2, 6);

  return {
    razaoSocial: `Empresa Teste E2E ${timestamp}`,
    slug: `teste-e2e-${random}`,
    adminNome: `Admin Teste ${random}`,
    adminEmail: `teste.e2e.${timestamp}@example.com`,
  };
}

/**
 * Tenta obter o último magic token enviado por email.
 *
 * IMPORTANTE: Requer que o backend tenha um endpoint de teste habilitado
 * em ambiente dev/test para retornar o último token de email enviado.
 *
 * Alternativa: O DevEmailService pode salvar os emails em um local acessível.
 */
export async function getLastMagicToken(request: APIRequestContext): Promise<string | null> {
  try {
    // Endpoint de teste - implementar no backend se necessário
    const response = await request.get(`${API_URL}/v1/test/last-email-token`);

    if (response.ok()) {
      const data = await response.json();
      return data.token || null;
    }

    console.warn('⚠️  Endpoint de teste não disponível. O magic token não pode ser obtido automaticamente.');
    return null;
  } catch (error) {
    console.warn('⚠️  Erro ao obter magic token:', error);
    return null;
  }
}

/**
 * Tenta obter o magic token do último signup.
 *
 * Alternativa usando query direta no banco (se disponível em testes):
 */
export async function getSignupTokenFromDatabase(
  request: APIRequestContext,
  email: string
): Promise<string | null> {
  try {
    const response = await request.get(`${API_URL}/v1/test/signup-token`, {
      params: { email },
    });

    if (response.ok()) {
      const data = await response.json();
      return data.token || null;
    }

    return null;
  } catch {
    return null;
  }
}

/**
 * Realiza o fluxo completo de onboarding:
 * 1. Signup
 * 2. Obtém magic token
 * 3. Ativa conta
 * 4. Faz login
 *
 * @returns Credenciais do novo tenant
 */
export async function performFullOnboarding(
  page: Page,
  request: APIRequestContext,
  testData?: TenantSignupData
): Promise<{
  tenantSlug: string;
  adminEmail: string;
  success: boolean;
  error?: string;
}> {
  const data = testData || generateTestTenantData();

  // 1. Realizar signup
  const signupPage = new SignupPage(page);
  await signupPage.goto();
  await signupPage.signup(data);

  try {
    await signupPage.waitForSuccess();
  } catch {
    return {
      tenantSlug: data.slug,
      adminEmail: data.adminEmail,
      success: false,
      error: 'Signup falhou - mensagem de sucesso não apareceu',
    };
  }

  // 2. Tentar obter magic token
  const token = await getLastMagicToken(request);

  if (!token) {
    return {
      tenantSlug: data.slug,
      adminEmail: data.adminEmail,
      success: false,
      error: 'Magic token não disponível. Configure o endpoint de teste ou ative manualmente.',
    };
  }

  // 3. Ativar conta
  const magicActivatePage = new MagicActivatePage(page);
  await magicActivatePage.gotoWithToken(token);

  const activationResult = await magicActivatePage.waitForResult();

  if (activationResult !== 'success') {
    return {
      tenantSlug: data.slug,
      adminEmail: data.adminEmail,
      success: false,
      error: `Ativação falhou: ${activationResult}`,
    };
  }

  // 4. Ir para login (a senha temporária foi enviada por email)
  await magicActivatePage.goToLogin();

  return {
    tenantSlug: data.slug,
    adminEmail: data.adminEmail,
    success: true,
  };
}

/**
 * Helper para criar tenant via API diretamente (bypass UI)
 *
 * Útil para setup de testes que não precisam testar o fluxo de signup
 */
export async function createTenantViaApi(
  request: APIRequestContext,
  data: TenantSignupData
): Promise<{ id: string; slug: string } | null> {
  try {
    const response = await request.post(`${API_URL}/v1/signup/tenant`, {
      data: {
        razaoSocial: data.razaoSocial,
        slug: data.slug,
        adminEmail: data.adminEmail,
        adminNome: data.adminNome,
        cnpj: data.cnpj,
      },
    });

    if (response.ok()) {
      return response.json();
    }

    console.warn('Falha ao criar tenant via API:', await response.text());
    return null;
  } catch (error) {
    console.warn('Erro ao criar tenant via API:', error);
    return null;
  }
}

/**
 * Verifica se um slug está disponível
 */
export async function checkSlugAvailability(
  request: APIRequestContext,
  slug: string
): Promise<boolean> {
  try {
    const response = await request.get(`${API_URL}/v1/signup/check-slug`, {
      params: { slug },
    });

    if (response.ok()) {
      const data = await response.json();
      return data.available === true;
    }

    return false;
  } catch {
    return false;
  }
}
