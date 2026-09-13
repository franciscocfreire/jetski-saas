import type { Browser, BrowserContext, Page } from '@playwright/test';
import { BASE_URL } from './ambiente';

/**
 * Login de um admin de empresa num contexto de navegador PRÓPRIO (um por empresa).
 * Base: performTraditionalLogin do global-setup.ts — identifier-first e troca da senha
 * temporária no primeiro acesso. Admins criados pelo signup não têm 2FA.
 */

export interface Sessao {
  context: BrowserContext;
  page: Page;
  accessToken: string;
  /** Senha válida depois do login (muda se o Keycloak exigiu a troca). */
  senha: string;
}

const KEYCLOAK_AUTH = /\/realms\/.*\/protocol\/openid-connect\/auth/;

export async function entrar(browser: Browser, email: string, senha: string): Promise<Sessao> {
  const context = await browser.newContext({ baseURL: BASE_URL, ignoreHTTPSErrors: true });
  const page = await context.newPage();
  let senhaAtual = senha;

  await page.goto('/login');
  await page.waitForURL(KEYCLOAK_AUTH, { timeout: 30_000 });

  await page.locator('#identifier').fill(email);
  await page.locator('#mj-send-code').click();
  await page.locator('#password').fill(senha);
  await page.locator('#mj-login-password').click();

  // Primeiro acesso: o Keycloak pode exigir a troca da senha temporária.
  const troca = page.locator('#password-new');
  const precisaTrocar = await troca
    .waitFor({ state: 'visible', timeout: 5_000 })
    .then(() => true)
    .catch(() => false);
  if (precisaTrocar) {
    senhaAtual = `E2e-${Date.now()}-Senha!`;
    await troca.fill(senhaAtual);
    await page.locator('#password-confirm').fill(senhaAtual);
    await page.locator('input[type="submit"], button[type="submit"]').first().click();
  }

  await page.waitForURL((url) => !KEYCLOAK_AUTH.test(url.href) && /\/dashboard/.test(url.pathname), {
    timeout: 45_000,
  });

  const sessao = await page.evaluate(async () => {
    const res = await fetch('/api/auth/session', { credentials: 'include' });
    return res.json();
  });
  if (!sessao?.accessToken) {
    throw new Error(`Login de ${email}: sessão sem accessToken (${JSON.stringify(sessao).slice(0, 200)})`);
  }
  return { context, page, accessToken: sessao.accessToken as string, senha: senhaAtual };
}
