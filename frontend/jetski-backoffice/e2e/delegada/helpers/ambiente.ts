/**
 * Endereços e guarda de ambiente do e2e da emissão delegada.
 *
 * Esta suíte CRIA empresas, concede papel de plataforma, grava SMTP e dispara e-mails.
 * Por isso ela não tem default: sem PLAYWRIGHT_BASE_URL explícito, ou com um host fora
 * da lista permitida, ela aborta antes de tocar em qualquer coisa. O config geral do
 * Playwright aponta para PRODUÇÃO por padrão — foi exatamente isso que motivou a guarda.
 */

const HOSTS_PERMITIDOS_PADRAO = ['localhost', '127.0.0.1', 'app.pegaojet.com.br'];

export const BASE_URL = (process.env.PLAYWRIGHT_BASE_URL ?? '').replace(/\/$/, '');
export const API_URL = (process.env.PLAYWRIGHT_API_URL ?? `${BASE_URL}/api`).replace(/\/$/, '');

/** Keycloak visto do host que roda o teste (ROPC e admin API). */
export const KEYCLOAK_URL = (process.env.E2E_KEYCLOAK_URL ?? 'http://localhost:8080').replace(/\/$/, '');
export const KEYCLOAK_REALM = process.env.E2E_KEYCLOAK_REALM ?? 'jetski-saas';

/** API do Mailpit vista do host. */
export const MAILPIT_URL = (process.env.MAILPIT_URL ?? 'http://localhost:8025').replace(/\/$/, '');

/** SMTP do Mailpit visto DE DENTRO do backend (rede do compose), para o SMTP das empresas. */
export const MAILPIT_SMTP_HOST = process.env.E2E_MAILPIT_SMTP_HOST ?? 'mailpit';
export const MAILPIT_SMTP_PORT = Number(process.env.E2E_MAILPIT_SMTP_PORT ?? '1025');

/** Container do Postgres de dev (seeds que não têm endpoint: plano dedicado e operador). */
export const POSTGRES_CONTAINER = process.env.E2E_POSTGRES_CONTAINER ?? 'jetski-postgres';
export const POSTGRES_USER = process.env.E2E_POSTGRES_USER ?? 'jetski';
export const POSTGRES_DB = process.env.E2E_POSTGRES_DB ?? 'jetski_dev';

/** Admin do Keycloak de DEV (mesmos valores de infra/keycloak-setup/setup-keycloak-dev.sh). */
export const KEYCLOAK_ADMIN_USER = process.env.E2E_KEYCLOAK_ADMIN_USER ?? 'admin';
export const KEYCLOAK_ADMIN_PASSWORD = process.env.E2E_KEYCLOAK_ADMIN_PASSWORD ?? 'Mazuca@123';

/** Operador de plataforma dedicado ao e2e (sem 2FA — o direct grant exige OTP de quem tem). */
export const OPERADOR_PLATAFORMA_EMAIL = process.env.E2E_PLATAFORMA_EMAIL ?? 'e2e.plataforma@meujet.test';
export const OPERADOR_PLATAFORMA_SENHA = process.env.E2E_PLATAFORMA_SENHA ?? 'E2e-Plataforma-2026!';

/** Client público com direct grant (só dev/CI). */
export const CLIENT_ROPC = process.env.E2E_ROPC_CLIENT ?? 'jetski-test';

/** Capitania comum às duas empresas. */
export const CAPITANIA_CODIGO = process.env.E2E_CAPITANIA ?? 'CPSP';

/** Nome do plano sem EMISSAO_PROPRIA (criado pelo global-setup, nunca um plano existente). */
export const PLANO_SO_DELEGADA = 'E2E Só delegada';

export function hostsPermitidos(): string[] {
  const extra = (process.env.E2E_HOSTS_PERMITIDOS ?? '')
    .split(',')
    .map((h) => h.trim())
    .filter(Boolean);
  return [...HOSTS_PERMITIDOS_PADRAO, ...extra];
}

/** Aborta se o alvo não for um ambiente descartável. Chamada no global-setup e na config. */
export function exigirAmbienteDeTeste(): void {
  if (!BASE_URL) {
    throw new Error(
      'E2E emissão delegada: defina PLAYWRIGHT_BASE_URL (ex.: https://app.pegaojet.com.br). ' +
        'Esta suíte cria empresas e dispara e-mails — não há default.',
    );
  }
  const alvos = [BASE_URL, API_URL, KEYCLOAK_URL, MAILPIT_URL].map((u) => new URL(u).hostname);
  const permitidos = hostsPermitidos();
  const proibido = alvos.find((h) => /(^|\.)meujet\.com\.br$/i.test(h) || !permitidos.includes(h));
  if (proibido) {
    throw new Error(
      `E2E emissão delegada: host "${proibido}" não é um ambiente de teste permitido ` +
        `(${permitidos.join(', ')}). Produção (*.meujet.com.br) é sempre recusada; ` +
        'para outro host de dev, inclua-o em E2E_HOSTS_PERMITIDOS.',
    );
  }
}

/** Sufixo único da execução: separa dados e caixas de e-mail entre rodadas. */
export function sufixoExecucao(): string {
  const agora = new Date();
  const p = (n: number) => String(n).padStart(2, '0');
  const carimbo = `${p(agora.getMonth() + 1)}${p(agora.getDate())}${p(agora.getHours())}${p(agora.getMinutes())}${p(agora.getSeconds())}`;
  return `${carimbo}${Math.random().toString(36).slice(2, 5)}`;
}
