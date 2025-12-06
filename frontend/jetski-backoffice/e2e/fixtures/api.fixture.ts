import { test as base, APIRequestContext, request } from '@playwright/test';

/**
 * Fixture que fornece um cliente de API para setup/teardown de dados de teste
 */
export interface ApiFixture {
  /**
   * Cliente de API autenticado
   */
  client: APIRequestContext;

  /**
   * Tenant ID para testes
   */
  tenantId: string;

  /**
   * Cria dados de teste via API
   */
  createTestData: <T>(endpoint: string, data: object) => Promise<T>;

  /**
   * Deleta dados de teste via API
   */
  deleteTestData: (endpoint: string, id: string) => Promise<void>;
}

export const test = base.extend<{ api: ApiFixture }>({
  api: async ({ }, use) => {
    const baseURL = process.env.PLAYWRIGHT_API_URL || 'https://pegaojet.com.br/api';
    const tenantId = process.env.TEST_TENANT_ID || '';

    // Criar contexto de API
    // Em produção, precisamos obter o token do estado de autenticação salvo
    let accessToken = '';

    try {
      // Tentar carregar estado de autenticação
      const fs = await import('fs');
      const authState = JSON.parse(
        fs.readFileSync('./e2e/.auth/user.json', 'utf-8')
      );

      // Extrair token do localStorage ou cookies
      const localStorage = authState.origins?.[0]?.localStorage || [];
      const tokenEntry = localStorage.find((entry: { name: string }) =>
        entry.name.includes('accessToken') || entry.name.includes('token')
      );
      if (tokenEntry) {
        accessToken = tokenEntry.value;
      }
    } catch (error) {
      console.log('⚠️  Não foi possível carregar estado de autenticação para API');
    }

    const apiContext = await request.newContext({
      baseURL,
      extraHTTPHeaders: {
        'Content-Type': 'application/json',
        'X-Tenant-Id': tenantId,
        ...(accessToken && { 'Authorization': `Bearer ${accessToken}` }),
      },
    });

    const apiFixture: ApiFixture = {
      client: apiContext,
      tenantId,

      async createTestData<T>(endpoint: string, data: object): Promise<T> {
        const response = await apiContext.post(endpoint, { data });
        if (!response.ok()) {
          throw new Error(`Falha ao criar dados: ${response.status()} ${await response.text()}`);
        }
        return response.json();
      },

      async deleteTestData(endpoint: string, id: string): Promise<void> {
        const response = await apiContext.delete(`${endpoint}/${id}`);
        if (!response.ok() && response.status() !== 404) {
          console.warn(`Aviso: Falha ao deletar ${endpoint}/${id}: ${response.status()}`);
        }
      },
    };

    await use(apiFixture);

    // Cleanup: fechar contexto de API
    await apiContext.dispose();
  },
});

export { expect } from '@playwright/test';
