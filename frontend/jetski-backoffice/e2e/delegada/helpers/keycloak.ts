import { request } from '@playwright/test';
import {
  CLIENT_ROPC,
  KEYCLOAK_ADMIN_PASSWORD,
  KEYCLOAK_ADMIN_USER,
  KEYCLOAK_REALM,
  KEYCLOAK_URL,
} from './ambiente';

/**
 * Keycloak de DEV: admin API (criar o operador do e2e) e ROPC no client público de teste.
 * O ROPC só serve para quem NÃO tem 2FA: o fluxo "direct grant" padrão tem Conditional OTP.
 *
 * No dev o Keycloak é reiniciado junto com rebuilds (o compose recria dependências). Por
 * isso toda chamada espera o serviço responder e repete quando a conexão cai no meio
 * ("socket hang up" / ECONNRESET) — erro de rede, nunca erro HTTP.
 */

const ERRO_DE_REDE = /socket hang up|ECONNRESET|ECONNREFUSED|EPIPE|ETIMEDOUT|other side closed/i;
const esperar = (ms: number) => new Promise((r) => setTimeout(r, ms));

/** Espera o realm responder (Keycloak subindo depois de um restart). */
export async function aguardarKeycloak(timeoutMs = 120_000): Promise<void> {
  const ctx = await request.newContext();
  const limite = Date.now() + timeoutMs;
  let ultimo = '';
  try {
    while (Date.now() < limite) {
      try {
        const res = await ctx.get(`${KEYCLOAK_URL}/realms/${KEYCLOAK_REALM}/.well-known/openid-configuration`, {
          timeout: 10_000,
        });
        if (res.ok()) return;
        ultimo = `HTTP ${res.status()}`;
      } catch (e) {
        ultimo = (e as Error).message.split('\n')[0];
      }
      await esperar(2_000);
    }
  } finally {
    await ctx.dispose();
  }
  throw new Error(`Keycloak não respondeu em ${timeoutMs / 1000}s (${KEYCLOAK_URL}): ${ultimo}`);
}

/** Repete a operação só em erro de rede, esperando o Keycloak voltar entre tentativas. */
async function comRetry<T>(oque: string, fn: () => Promise<T>, tentativas = 4): Promise<T> {
  for (let i = 1; ; i++) {
    try {
      return await fn();
    } catch (e) {
      const msg = (e as Error).message;
      if (i >= tentativas || !ERRO_DE_REDE.test(msg)) throw e;
      console.warn(`[delegada] ${oque}: conexão caiu (tentativa ${i}/${tentativas}), aguardando o Keycloak…`);
      await aguardarKeycloak();
      await esperar(1_000 * i);
    }
  }
}

async function tokenAdmin(): Promise<string> {
  return comRetry('token admin do Keycloak', async () => {
    const ctx = await request.newContext();
    try {
      const res = await ctx.post(`${KEYCLOAK_URL}/realms/master/protocol/openid-connect/token`, {
        form: {
          grant_type: 'password',
          client_id: 'admin-cli',
          username: KEYCLOAK_ADMIN_USER,
          password: KEYCLOAK_ADMIN_PASSWORD,
        },
      });
      if (!res.ok()) throw new Error(`Keycloak admin token ${res.status()}: ${await res.text()}`);
      return (await res.json()).access_token as string;
    } finally {
      await ctx.dispose();
    }
  });
}

/** Cria (se não existir) um usuário com senha definitiva e devolve o id do Keycloak. */
export async function garantirUsuario(email: string, senha: string, nome: string): Promise<string> {
  await aguardarKeycloak();
  const admin = await tokenAdmin();
  return comRetry(`garantir usuário ${email}`, async () => {
    const ctx = await request.newContext({
      baseURL: `${KEYCLOAK_URL}/admin/realms/${KEYCLOAK_REALM}/`,
      extraHTTPHeaders: { Authorization: `Bearer ${admin}` },
    });
    try {
      const criar = await ctx.post('users', {
        data: {
          username: email,
          email,
          firstName: nome,
          lastName: 'E2E',
          enabled: true,
          emailVerified: true,
          credentials: [{ type: 'password', value: senha, temporary: false }],
        },
      });
      if (!criar.ok() && criar.status() !== 409) {
        throw new Error(`Keycloak criar usuário ${criar.status()}: ${await criar.text()}`);
      }
      const busca = await ctx.get('users', { params: { username: email, exact: 'true' } });
      const [usuario] = (await busca.json()) as Array<{ id: string }>;
      if (!usuario) throw new Error(`Keycloak: usuário ${email} não encontrado após criar`);
      return usuario.id;
    } finally {
      await ctx.dispose();
    }
  });
}

/** Token de acesso por ROPC (client público de teste, só dev/CI). Expira em minutos: peça um por uso. */
export async function tokenRopc(email: string, senha: string): Promise<string> {
  return comRetry(`ROPC de ${email}`, async () => {
    const ctx = await request.newContext();
    try {
      const res = await ctx.post(`${KEYCLOAK_URL}/realms/${KEYCLOAK_REALM}/protocol/openid-connect/token`, {
        form: { grant_type: 'password', client_id: CLIENT_ROPC, username: email, password: senha },
      });
      if (!res.ok()) {
        throw new Error(
          `ROPC ${CLIENT_ROPC} para ${email}: ${res.status()} ${await res.text()} ` +
            '(o usuário tem 2FA? o direct grant exige OTP de quem tem)',
        );
      }
      return (await res.json()).access_token as string;
    } finally {
      await ctx.dispose();
    }
  });
}
