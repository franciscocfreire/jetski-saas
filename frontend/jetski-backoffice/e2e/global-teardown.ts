import { FullConfig, request } from '@playwright/test';
import * as fs from 'fs';
import * as path from 'path';

/**
 * Global teardown that runs after all tests.
 *
 * Responsibilities:
 * 1. Log test tenant info for manual cleanup if needed
 * 2. Clear test email data from backend
 * 3. Clean up local auth files
 */
async function globalTeardown(config: FullConfig) {
  console.log('🧹 Iniciando limpeza global...');

  const authDir = path.join(__dirname, '.auth');
  const tenantDataPath = path.join(authDir, 'test-tenant.json');

  // Check if we created a test tenant
  if (fs.existsSync(tenantDataPath)) {
    try {
      const tenantData = JSON.parse(fs.readFileSync(tenantDataPath, 'utf-8'));

      console.log('   📋 Tenant de teste criado durante esta execução:');
      console.log(`      ID: ${tenantData.tenantId}`);
      console.log(`      Slug: ${tenantData.tenantSlug}`);
      console.log(`      Email: ${tenantData.adminEmail}`);
      console.log(`      Criado em: ${tenantData.createdAt}`);

      // Try to clear email data on backend
      const apiURL = process.env.PLAYWRIGHT_API_URL || 'https://pegaojet.com.br/api';

      try {
        const apiContext = await request.newContext({ baseURL: apiURL });

        const clearResponse = await apiContext.delete('/v1/test/last-email');
        if (clearResponse.ok()) {
          console.log('   ✅ Dados de email de teste limpos no backend');
        }

        await apiContext.dispose();
      } catch (e) {
        // Backend might not be available, that's ok
        console.log('   ⚠️  Não foi possível limpar dados de email no backend (endpoint pode não estar disponível)');
      }

      // Note about cleanup
      console.log('');
      console.log('   ℹ️  NOTA: O tenant de teste NÃO é deletado automaticamente.');
      console.log('      Para limpar, delete manualmente no banco de dados:');
      console.log(`      DELETE FROM tenant WHERE id = '${tenantData.tenantId}';`);
      console.log('');
      console.log('      Ou configure um job de limpeza para tenants com slug "e2e-test-*"');

      // Remove tenant data file
      fs.unlinkSync(tenantDataPath);
      console.log('   🗑️  Arquivo test-tenant.json removido');

    } catch (error) {
      console.error('   ⚠️  Erro ao processar dados do tenant de teste:', error);
    }
  } else {
    console.log('   ℹ️  Nenhum tenant de teste foi criado nesta execução');
  }

  // Clean up error screenshots if tests passed
  const errorScreenshot = path.join(authDir, 'login-error.png');
  if (fs.existsSync(errorScreenshot)) {
    // Keep for debugging - don't delete automatically
    console.log('   📸 Screenshot de erro mantido em: ./e2e/.auth/login-error.png');
  }

  console.log('🎉 Teardown global concluído!');
}

export default globalTeardown;
