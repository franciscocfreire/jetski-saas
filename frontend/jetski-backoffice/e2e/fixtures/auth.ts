import { test as base, Page, BrowserContext } from '@playwright/test';
import * as fs from 'fs';
import * as path from 'path';

/**
 * Extended test fixture with authenticated page.
 *
 * This fixture handles the login flow automatically.
 * Uses the test tenant created in global-setup.
 */

// Read test tenant data from global setup
function getTestTenantData() {
  const tenantDataPath = path.join(__dirname, '..', '.auth', 'test-tenant.json');
  if (fs.existsSync(tenantDataPath)) {
    return JSON.parse(fs.readFileSync(tenantDataPath, 'utf-8'));
  }
  return null;
}

// Read stored credentials
export function getStoredCredentials() {
  const tenantData = getTestTenantData();
  if (tenantData) {
    return {
      email: tenantData.adminEmail,
      password: tenantData.temporaryPassword, // Updated during global-setup if Keycloak forces change
      tenantId: tenantData.tenantId,
      tenantSlug: tenantData.tenantSlug,
    };
  }
  return {
    email: process.env.TEST_USER_EMAIL || '',
    password: process.env.TEST_USER_PASSWORD || '',
    tenantId: process.env.TEST_TENANT_ID || '',
    tenantSlug: process.env.TEST_TENANT_SLUG || '',
  };
}

type AuthFixtures = {
  authenticatedPage: Page;
  apiToken: string;
  tenantId: string;
};

export const test = base.extend<AuthFixtures>({
  /**
   * Override page fixture to automatically authenticate.
   *
   * Due to Playwright limitations with __Host-/__Secure- prefixed cookies,
   * we perform login once and capture the session data, then mock the
   * /api/auth/session endpoint on subsequent requests.
   */
  authenticatedPage: async ({ page, context }, use) => {
    const baseURL = process.env.PLAYWRIGHT_BASE_URL || 'https://pegaojet.com.br';
    const creds = getStoredCredentials();

    if (!creds.email || !creds.password) {
      throw new Error('Test credentials not available');
    }

    console.log('🔐 Performing login...');

    // Retry logic for login (handles concurrent worker issues with Keycloak)
    const maxRetries = 3;
    let lastError: Error | null = null;

    for (let attempt = 1; attempt <= maxRetries; attempt++) {
      try {
        // Navigate to login first
        await page.goto(`${baseURL}/login`);
        await page.waitForLoadState('networkidle');

        // Click login button
        const loginButton = page.locator('button:has-text("Entrar")');
        await loginButton.waitFor({ state: 'visible', timeout: 10000 });
        await loginButton.click();

        // Wait for Keycloak
        await page.waitForURL(/.*\/realms\/.*\/protocol\/openid-connect\/auth.*/, { timeout: 15000 });
        console.log('   ➡️  Redirected to Keycloak');

        // Fill credentials
        await page.fill('#username', creds.email);
        await page.fill('#password', creds.password);
        await page.click('#kc-login');
        console.log('   ➡️  Submitted credentials');

        // Wait for redirect back to app (not on Keycloak anymore)
        // Check for error on Keycloak page first
        const errorOnKeycloak = await page.locator('.alert-error, #kc-content-wrapper .alert').isVisible({ timeout: 2000 }).catch(() => false);
        if (errorOnKeycloak) {
          const errorText = await page.locator('.alert-error, #kc-content-wrapper .alert').textContent().catch(() => 'Unknown error');
          throw new Error(`Keycloak login error: ${errorText}`);
        }

        await page.waitForURL((url) => !url.href.includes('/realms/'), { timeout: 30000 });
        break; // Success, exit retry loop
      } catch (e) {
        lastError = e as Error;
        console.log(`   ⚠️  Login attempt ${attempt}/${maxRetries} failed: ${lastError.message}`);
        if (attempt < maxRetries) {
          console.log(`   🔄 Retrying in 2 seconds...`);
          await page.waitForTimeout(2000);
        }
      }
    }

    if (lastError && !page.url().includes('/dashboard') && !page.url().includes('/api/auth/callback')) {
      throw lastError;
    }

    let currentUrl = page.url();
    console.log(`   ✅ Auth callback complete, landed on: ${currentUrl}`);

    // Get cookies immediately after OAuth callback
    const cookies = await context.cookies();
    const sessionTokens = cookies.filter(c => c.name.includes('session-token'));
    const hasSecureCookies = sessionTokens.some(c => c.name.startsWith('__Secure-'));
    console.log(`   🍪 Cookies: ${sessionTokens.map(c => c.name).join(', ')}`);
    console.log(`   🔐 Using secure cookies: ${hasSecureCookies}`);

    // Capture session via API
    let sessionData = await page.evaluate(async () => {
      const res = await fetch('/api/auth/session', { credentials: 'include' });
      return res.json();
    });

    if (!sessionData?.user) {
      throw new Error('Login failed - no session returned from /api/auth/session');
    }
    console.log(`   📋 Session valid: ${sessionData.user.email}`);

    // Wait for any redirects to settle
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(1000);
    currentUrl = page.url();

    // Check if we're on dashboard or if we got redirected to login
    if (currentUrl.includes('/dashboard') || currentUrl.includes('/home')) {
      // Great! Auth worked properly (likely running with NEXTAUTH_E2E_TESTING=true)
      console.log(`   ✅ Authentication successful, on dashboard`);
      await use(page);
      return;
    }

    // If we're on login page, it means __Secure- cookies aren't working with SSR
    if (currentUrl.includes('/login') && hasSecureCookies) {
      console.log(`   ⚠️  Auth SSR failed due to __Secure- cookie limitation`);
      console.log(`   ℹ️  Session is valid but SSR can't read secure cookies in Playwright`);
      console.log(`   💡 To fix: Set NEXTAUTH_E2E_TESTING=true in your deployment`);

      // Set up route mock for session endpoint - helps with client-side checks
      await page.route('**/api/auth/session', async route => {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify(sessionData),
        });
      });

      // Store session data on page for tests that can use it
      await page.evaluate((session) => {
        (window as any).__e2eSession = session;
        (window as any).__e2eAccessToken = session.accessToken;
      }, sessionData);

      // Try navigating - the route mock might help
      await page.goto(`${baseURL}/dashboard`);
      await page.waitForLoadState('networkidle');
      await page.waitForTimeout(2000);
      currentUrl = page.url();

      if (!currentUrl.includes('/login')) {
        console.log(`   ✅ Navigation with route mock worked`);
        await use(page);
        return;
      }

      // Still on login - this is a known limitation
      throw new Error(
        `Authentication works but SSR session check fails due to __Secure- cookie limitations in Playwright. ` +
        `To run authenticated E2E tests, deploy the app with NEXTAUTH_E2E_TESTING=true ` +
        `or test against a local dev server. Session data is captured and available for API testing.`
      );
    }

    // Unexpected state
    throw new Error(`Unexpected state after login: URL is ${currentUrl}`);
  },

  /**
   * Provides an API token for direct backend calls.
   */
  apiToken: async ({ authenticatedPage }, use) => {
    const session = await authenticatedPage.evaluate(async () => {
      const res = await fetch('/api/auth/session');
      return res.json();
    });
    await use(session.accessToken || '');
  },

  /**
   * Provides the current tenant ID.
   */
  tenantId: async ({}, use) => {
    const creds = getStoredCredentials();
    await use(creds.tenantId);
  },
});

/**
 * Perform login via Keycloak.
 */
async function performLogin(page: Page, baseURL: string): Promise<void> {
  const creds = getStoredCredentials();

  if (!creds.email || !creds.password) {
    throw new Error('Test credentials not available. Make sure global-setup ran successfully.');
  }

  console.log(`   📧 Email: ${creds.email}`);

  // Click login button
  const loginButton = page.locator('button:has-text("Entrar"), button:has-text("Login")');
  await loginButton.waitFor({ state: 'visible', timeout: 10000 });
  await loginButton.click();
  console.log('   ➡️  Clicked login button');

  // Wait for Keycloak
  await page.waitForURL(/.*\/realms\/.*\/protocol\/openid-connect\/auth.*/, {
    timeout: 15000,
  });
  console.log('   ➡️  Redirected to Keycloak');

  // Fill credentials
  await page.fill('#username', creds.email);
  await page.fill('#password', creds.password);
  await page.click('#kc-login');
  console.log('   ➡️  Submitted credentials');

  // Wait for redirect back to app
  await page.waitForURL(/.*\/(dashboard|home).*/, {
    timeout: 30000,
  });
  console.log('   ✅ Login successful');

  // Wait for session to stabilize
  await page.waitForLoadState('networkidle');
  await page.waitForTimeout(2000);
}

export { expect } from '@playwright/test';
