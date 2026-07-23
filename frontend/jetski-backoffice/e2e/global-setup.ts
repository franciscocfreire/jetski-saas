import { chromium, FullConfig, request } from '@playwright/test';
import * as fs from 'fs';
import * as path from 'path';

/**
 * Global setup that runs before all tests.
 *
 * Strategy:
 * 1. If credentials are configured → use them directly
 * 2. If not → create a new test tenant automatically via signup
 *
 * Auto-signup flow:
 * 1. Call POST /v1/signup/tenant to create tenant + admin
 * 2. Call GET /v1/test/last-email to get magic token
 * 3. Call POST /v1/signup/magic-activate to activate account
 * 4. Login via Keycloak and save auth state
 */

interface TestTenantData {
  tenantId: string;
  tenantSlug: string;
  adminEmail: string;
  adminNome: string;
  temporaryPassword: string;
  createdAt: string;
}

async function globalSetup(config: FullConfig) {
  const baseURL = process.env.PLAYWRIGHT_BASE_URL || 'https://pegaojet.com.br';
  const apiURL = process.env.PLAYWRIGHT_API_URL || `${baseURL}/api`;
  const testEmail = process.env.TEST_USER_EMAIL;
  const testPassword = process.env.TEST_USER_PASSWORD;

  // Ensure .auth directory exists
  const authDir = path.join(__dirname, '.auth');
  if (!fs.existsSync(authDir)) {
    fs.mkdirSync(authDir, { recursive: true });
  }

  // Path to store test tenant data for cleanup
  const tenantDataPath = path.join(authDir, 'test-tenant.json');

  // If credentials are already configured, use traditional login
  if (testEmail && testPassword) {
    console.log('🔐 Credenciais configuradas - usando login tradicional...');
    const accessToken = await performTraditionalLogin(baseURL, testEmail, testPassword, authDir);
    // Note: Don't create test data for pre-configured credentials (they should have their own data)
    return;
  }

  // Auto-create test tenant
  console.log('🚀 Credenciais não configuradas - criando tenant de teste automaticamente...');

  try {
    const testTenant = await createTestTenant(apiURL);

    // Save tenant data for cleanup in teardown
    fs.writeFileSync(tenantDataPath, JSON.stringify(testTenant, null, 2));
    console.log('   💾 Dados do tenant salvo para cleanup');

    // Set env vars for tests to use
    process.env.TEST_USER_EMAIL = testTenant.adminEmail;
    process.env.TEST_USER_PASSWORD = testTenant.temporaryPassword;
    process.env.TEST_TENANT_SLUG = testTenant.tenantSlug;
    process.env.TEST_TENANT_ID = testTenant.tenantId;

    // Perform login with new credentials
    const accessToken = await performTraditionalLogin(
      baseURL,
      testTenant.adminEmail,
      testTenant.temporaryPassword,
      authDir
    );

    console.log('🎉 Tenant de teste criado e autenticado com sucesso!');
    console.log(`   📧 Email: ${testTenant.adminEmail}`);
    console.log(`   🏢 Tenant: ${testTenant.tenantSlug}`);

    // Create test data (modelo + jetski) for E2E tests
    if (accessToken) {
      await createTestData(apiURL, testTenant.tenantId, accessToken);
    } else {
      console.log('   ⚠️  Dados de teste não criados (sem access token)');
    }

  } catch (error) {
    console.error('❌ Erro ao criar tenant de teste:', error);

    // Create empty auth state so tests can still run (they'll be skipped)
    const emptyState = { cookies: [], origins: [] };
    fs.writeFileSync(
      path.join(authDir, 'user.json'),
      JSON.stringify(emptyState, null, 2)
    );

    console.log('   ⚠️  Testes que requerem autenticação serão pulados');
  }

  console.log('🎉 Setup global concluído!');
}

/**
 * Create a test tenant via API automatically.
 */
async function createTestTenant(apiURL: string): Promise<TestTenantData> {
  const timestamp = Date.now();
  const random = Math.random().toString(36).substring(2, 6);

  const tenantData = {
    razaoSocial: `E2E Test Company ${timestamp}`,
    slug: `e2e-test-${random}-${timestamp}`,
    adminNome: `E2E Admin ${random}`,
    adminEmail: `e2e.test.${timestamp}@example.com`,
  };

  console.log('   📝 Criando tenant via API...');
  console.log(`      Slug: ${tenantData.slug}`);
  console.log(`      Email: ${tenantData.adminEmail}`);

  // Create API request context
  const apiContext = await request.newContext({
    baseURL: apiURL,
  });

  try {
    // 1. Create tenant via signup
    const signupUrl = `${apiURL}/v1/signup/tenant`;
    const signupResponse = await apiContext.post(signupUrl, {
      data: tenantData,
    });

    if (!signupResponse.ok()) {
      const errorText = await signupResponse.text();
      throw new Error(`Signup failed: ${signupResponse.status()} - ${errorText}`);
    }

    const signupResult = await signupResponse.json();
    console.log(`   ✅ Tenant criado: ${signupResult.tenantId}`);

    // 2. Get magic token from test endpoint
    // Wait a bit for email to be "sent"
    await new Promise(resolve => setTimeout(resolve, 500));

    const emailUrl = `${apiURL}/v1/test/last-email`;
    const emailResponse = await apiContext.get(emailUrl);

    if (!emailResponse.ok()) {
      throw new Error(`Failed to get email token: ${emailResponse.status()}`);
    }

    const emailData = await emailResponse.json();

    if (!emailData.success || !emailData.magicToken) {
      throw new Error('Magic token not found in email data');
    }

    console.log('   ✅ Token de ativação obtido');

    // 3. Activate account via magic link
    const activateUrl = `${apiURL}/v1/signup/magic-activate`;
    const activateResponse = await apiContext.post(activateUrl, {
      data: {
        magicToken: emailData.magicToken,
      },
    });

    if (!activateResponse.ok()) {
      const errorText = await activateResponse.text();
      throw new Error(`Activation failed: ${activateResponse.status()} - ${errorText}`);
    }

    console.log('   ✅ Conta ativada com sucesso');

    return {
      tenantId: signupResult.tenantId,
      tenantSlug: tenantData.slug,
      adminEmail: tenantData.adminEmail,
      adminNome: tenantData.adminNome,
      temporaryPassword: emailData.temporaryPassword,
      createdAt: new Date().toISOString(),
    };

  } finally {
    await apiContext.dispose();
  }
}

/**
 * Create test data (modelo + jetski) via API.
 * This needs to be called after login when we have an access token.
 */
async function createTestData(
  apiURL: string,
  tenantId: string,
  accessToken: string
): Promise<void> {
  console.log('📦 Criando dados de teste (modelo + jetski)...');

  const apiContext = await request.newContext({
    baseURL: apiURL,
    extraHTTPHeaders: {
      'Authorization': `Bearer ${accessToken}`,
      'X-Tenant-Id': tenantId,
      'Content-Type': 'application/json',
    },
  });

  try {
    // 1. Create modelo (based on ModeloCreateRequest DTO)
    const modeloData = {
      nome: 'Sea-Doo GTI SE 170',  // required
      fabricante: 'Sea-Doo',
      potenciaHp: 170,
      capacidadePessoas: 3,
      precoBaseHora: 350.00,  // required
      toleranciaMin: 5,
      taxaHoraExtra: 0,
      incluiCombustivel: true,
      caucao: 500.00,
    };

    const modeloResponse = await apiContext.post(`${apiURL}/v1/tenants/${tenantId}/modelos`, {
      data: modeloData,
    });

    if (!modeloResponse.ok()) {
      console.log(`   ⚠️  Erro ao criar modelo: ${modeloResponse.status()}`);
      const errorText = await modeloResponse.text();
      console.log(`      ${errorText.substring(0, 200)}`);
      return;
    }

    const modelo = await modeloResponse.json();
    console.log(`   ✅ Modelo criado: ${modelo.nome} (ID: ${modelo.id})`);

    // 2. Create jetski (based on JetskiCreateRequest DTO)
    const jetskiData = {
      modeloId: modelo.id,  // required
      serie: 'E2E-JETSKI-001',  // required - número de série
      ano: 2024,
      horimetroAtual: 100.0,
      status: 'DISPONIVEL',
    };

    const jetskiResponse = await apiContext.post(`${apiURL}/v1/tenants/${tenantId}/jetskis`, {
      data: jetskiData,
    });

    if (!jetskiResponse.ok()) {
      console.log(`   ⚠️  Erro ao criar jetski: ${jetskiResponse.status()}`);
      const errorText = await jetskiResponse.text();
      console.log(`      ${errorText.substring(0, 200)}`);
      return;
    }

    const jetski = await jetskiResponse.json();
    console.log(`   ✅ Jetski criado: ${jetski.serie} (ID: ${jetski.id})`);

    console.log('📦 Dados de teste criados com sucesso!');

  } catch (error) {
    console.log(`   ⚠️  Erro ao criar dados de teste: ${error}`);
  } finally {
    await apiContext.dispose();
  }
}

/**
 * Perform traditional login via Keycloak.
 * Returns the access token for API calls.
 */
async function performTraditionalLogin(
  baseURL: string,
  email: string,
  password: string,
  authDir: string
): Promise<string | null> {
  console.log('🔐 Iniciando autenticação via Keycloak...');

  const browser = await chromium.launch();
  const context = await browser.newContext();
  const page = await context.newPage();

  try {
    // 1. Navigate to login page — /login redireciona direto ao Keycloak
    // (desde 5b8b1c5 não há mais botão "Entrar" intermediário; mantemos o
    // clique como fallback para ambientes com a tela antiga)
    await page.goto(`${baseURL}/login`);
    console.log('   ➡️  Navegou para /login');

    const keycloakUrlPattern = /.*\/realms\/.*\/protocol\/openid-connect\/auth.*/;

    // 2. Wait for Keycloak (redirect automático) ou clicar no botão legado
    const chegouNoKeycloak = await page
      .waitForURL(keycloakUrlPattern, { timeout: 15000 })
      .then(() => true)
      .catch(() => false);

    if (!chegouNoKeycloak) {
      const loginButton = page.locator('button:has-text("Entrar"), button:has-text("Login"), button:has-text("Keycloak")');
      await loginButton.waitFor({ state: 'visible', timeout: 10000 });
      await loginButton.click();
      console.log('   ➡️  Clicou no botão de login (tela legada)');
      await page.waitForURL(keycloakUrlPattern, { timeout: 15000 });
    }
    console.log('   ➡️  Redirecionado para Keycloak');

    // 4/5. Fill credentials + submit — tema meujet (identifier-first, duas
    // etapas: e-mail → Continuar → senha) ou tema Keycloak clássico
    const identifierInput = page.locator('#identifier');
    const temaNovo = await identifierInput
      .waitFor({ state: 'visible', timeout: 5000 })
      .then(() => true)
      .catch(() => false);

    if (temaNovo) {
      await identifierInput.fill(email);
      await page.click('#mj-send-code');
      console.log('   ➡️  Informou identificador (tema meujet)');
      await page.fill('#password', password);
      await page.click('#mj-login-password');
    } else {
      await page.fill('#username', email);
      await page.fill('#password', password);
      await page.click('#kc-login');
    }
    console.log('   ➡️  Submeteu login');

    // 6. Handle password update if required (first login)
    try {
      // Check if Keycloak asks for password update
      const updatePasswordForm = page.locator('#kc-passwd-update-form, form[action*="login-actions/required-action"]');
      const needsPasswordUpdate = await updatePasswordForm.isVisible({ timeout: 3000 }).catch(() => false);

      if (needsPasswordUpdate) {
        console.log('   🔑 Keycloak requer atualização de senha (primeiro login)...');

        // Fill new password (use same password for simplicity in tests)
        const newPassword = `E2E_${Date.now()}_Test!`;
        await page.fill('#password-new', newPassword);
        await page.fill('#password-confirm', newPassword);
        await page.click('input[type="submit"], button[type="submit"]');

        // Update env var with new password
        process.env.TEST_USER_PASSWORD = newPassword;

        // Also update the test-tenant.json with new password
        const tenantFilePath = path.join(authDir, 'test-tenant.json');
        if (fs.existsSync(tenantFilePath)) {
          const tenantData = JSON.parse(fs.readFileSync(tenantFilePath, 'utf-8'));
          tenantData.temporaryPassword = newPassword;
          fs.writeFileSync(tenantFilePath, JSON.stringify(tenantData, null, 2));
        }

        console.log('   ✅ Senha atualizada');
      }
    } catch (e) {
      // No password update required, continue
    }

    // 7. Wait for redirect back to application
    await page.waitForURL(/.*\/(dashboard|home).*/, {
      timeout: 30000,
    });
    console.log('   ✅ Login bem-sucedido! Redirecionado para dashboard');

    // 8. Get access token from session
    let accessToken: string | null = null;
    try {
      const sessionResponse = await page.evaluate(async () => {
        const res = await fetch('/api/auth/session', { credentials: 'include' });
        return res.json();
      });
      accessToken = sessionResponse?.accessToken || null;
      if (accessToken) {
        console.log('   🔑 Access token obtido para criar dados de teste');
      }
    } catch (e) {
      console.log('   ⚠️  Não foi possível obter access token');
    }

    // 9. Save auth state
    const statePath = path.join(authDir, 'user.json');
    await context.storageState({ path: statePath });

    // 10. Fix __Host- and __Secure- prefixed cookies
    // These cookies should NOT have a domain attribute set
    // See: https://developer.mozilla.org/en-US/docs/Web/HTTP/Cookies#cookie_prefixes
    // Playwright needs either url OR domain/path, so we replace domain with url
    const stateContent = fs.readFileSync(statePath, 'utf-8');
    const state = JSON.parse(stateContent);

    let fixedCount = 0;
    state.cookies = state.cookies.map((cookie: any) => {
      // __Host- cookies MUST NOT have Domain attribute
      // __Secure- cookies work better without explicit domain in some browsers
      if (cookie.name.startsWith('__Host-') || cookie.name.startsWith('__Secure-')) {
        fixedCount++;
        // Replace domain/path with url for Playwright compatibility
        // When using url, we should NOT have domain or path
        const { domain, path, ...cookieWithoutDomainPath } = cookie;
        return {
          ...cookieWithoutDomainPath,
          url: `https://${domain}${path || '/'}`,
        };
      }
      return cookie;
    });

    fs.writeFileSync(statePath, JSON.stringify(state, null, 2));
    console.log(`   💾 Estado de autenticação salvo (${fixedCount} cookies corrigidos)`);

    return accessToken;

  } catch (error) {
    console.error('❌ Erro durante autenticação:', error);
    await page.screenshot({ path: path.join(authDir, 'login-error.png') });
    console.log('   📸 Screenshot de erro salvo em ./e2e/.auth/login-error.png');

    // Create empty state to not block other tests
    const emptyState = { cookies: [], origins: [] };
    fs.writeFileSync(
      path.join(authDir, 'user.json'),
      JSON.stringify(emptyState, null, 2)
    );
    throw error;
  } finally {
    await browser.close();
  }

  return null;
}

export default globalSetup;
