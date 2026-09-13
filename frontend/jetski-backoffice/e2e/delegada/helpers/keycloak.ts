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
 */

async function tokenAdmin(): Promise<string> {
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
}

/** Cria (se não existir) um usuário com senha definitiva e devolve o id do Keycloak. */
export async function garantirUsuario(email: string, senha: string, nome: string): Promise<string> {
  const admin = await tokenAdmin();
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
}

/** Token de acesso por ROPC (client público de teste, só dev/CI). */
export async function tokenRopc(email: string, senha: string): Promise<string> {
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
}
