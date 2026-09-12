import { test, expect } from '@playwright/test';

/**
 * Login por PROVEDOR EXTERNO (identity brokering).
 *
 * Usa o IdP OIDC de teste (`infra/keycloak-setup/add-idp-teste-dev.sh`), não o
 * Google: o Google bloqueia navegador automatizado, desafia IP de datacenter e
 * muda a própria tela sem aviso — um teste assim falha sem regressão nossa e
 * não distingue "nosso brokering quebrou" de "o Google desconfiou hoje". Para o
 * realm da aplicação, o IdP de teste é um provedor OIDC como outro qualquer:
 * exercita first broker login, post-broker (2FA/dispositivo confiável), a
 * sessão resultante e o logout federado.
 *
 * NÃO cobre peculiaridades do Google (formato do id_token, consent, claims) —
 * isso segue como verificação manual.
 *
 * Pré-requisito: o script acima já rodou no ambiente alvo. Sem o IdP, o teste
 * é pulado em vez de falhar (o CI não tem o provedor de teste configurado).
 */

const USUARIO = process.env.IDP_TESTE_USER || 'e2e@idp-teste.local';
const SENHA = process.env.IDP_TESTE_PASSWORD || 'e2e123';

test.describe('Login por provedor externo (IdP de teste)', () => {
  test('entra pelo provedor externo e a sessão vale no app', async ({ page }) => {
    await page.goto('/login');

    // O trampolim leva ao Keycloak; lá o provedor aparece como botão.
    await page.waitForURL(/\/realms\/[^/]+\/protocol\/openid-connect\/auth/, { timeout: 30000 });

    // O app só tem botão para o Google; e o tema só desenha os provedores na
    // SEGUNDA tela (identifier-first). kc_idp_hint na própria URL de autorização
    // salta direto para o provedor, preservando state e PKCE do NextAuth.
    const comHint = new URL(page.url());
    comHint.searchParams.set('kc_idp_hint', 'idp-teste');
    await page.goto(comHint.toString());

    const chegou = await page
      .waitForURL(/\/realms\/idp-teste\//, { timeout: 20000 })
      .then(() => true)
      .catch(() => false);
    test.skip(!chegou, 'IdP de teste não configurado aqui (rode infra/keycloak-setup/add-idp-teste-dev.sh)');
    await page.fill('#username', USUARIO);
    await page.fill('#password', SENHA);
    await page.click('#kc-login');

    // Volta para o app: first broker login cria/vincula a identidade e o
    // post-broker roda (sem 2FA cadastrado, a condição é falsa e ele passa).
    await page.waitForURL((url) => !url.pathname.includes('/realms/'), { timeout: 45000 });

    // A sessão do app tem que existir e ser a identidade que veio do provedor.
    const sessao = await page.request.get('/api/auth/session');
    const dados = (await sessao.json()) as { user?: { email?: string } } | null;
    expect(dados?.user?.email, 'sessão do app após o login federado').toBe(USUARIO);

    // Conta recém-criada pelo brokering não tem empresa: o destino legítimo é o
    // dashboard OU o aviso de "sem empresa" — o que importa é ter entrado.
    expect(page.url()).not.toMatch(/\/realms\//);
  });

  test('logout federado encerra a sessão vinda do provedor', async ({ page }) => {
    await page.goto('/login');
    await page.waitForURL(/\/realms\/[^/]+\/protocol\/openid-connect\/auth/, { timeout: 30000 });

    // O app só tem botão para o Google; e o tema só desenha os provedores na
    // SEGUNDA tela (identifier-first). kc_idp_hint na própria URL de autorização
    // salta direto para o provedor, preservando state e PKCE do NextAuth.
    const comHint = new URL(page.url());
    comHint.searchParams.set('kc_idp_hint', 'idp-teste');
    await page.goto(comHint.toString());

    const chegou = await page
      .waitForURL(/\/realms\/idp-teste\//, { timeout: 20000 })
      .then(() => true)
      .catch(() => false);
    test.skip(!chegou, 'IdP de teste não configurado aqui (rode infra/keycloak-setup/add-idp-teste-dev.sh)');
    await page.fill('#username', USUARIO);
    await page.fill('#password', SENHA);
    await page.click('#kc-login');
    await page.waitForURL((url) => !url.pathname.includes('/realms/'), { timeout: 45000 });

    // Mesmo critério do logout local: a sessão precisa morrer de verdade.
    await page.goto('/logout');
    await page.waitForURL((url) => !url.pathname.startsWith('/logout'), { timeout: 30000 });

    await expect
      .poll(
        async () => {
          const r = await page.request.get('/api/auth/session');
          const corpo = (await r.text()).trim();
          return corpo === 'null' || corpo === '{}';
        },
        { timeout: 15000, message: 'sessão federada continuou viva após o logout' },
      )
      .toBe(true);

    await page.waitForTimeout(3000);
    await expect(page).not.toHaveURL(/.*\/dashboard.*/);
  });
});
