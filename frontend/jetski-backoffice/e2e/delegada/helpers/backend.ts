import { request, type APIRequestContext, type APIResponse } from '@playwright/test';
import { API_URL } from './ambiente';

/**
 * Cliente mínimo da API do backend para o preparo e as verificações do e2e.
 *
 * Atenção ao baseURL: ele termina em "/api/" e os caminhos NÃO começam com "/". Um
 * caminho absoluto descartaria o "/api" (regra de resolução de URL do Playwright).
 */

async function contexto(headers: Record<string, string> = {}): Promise<APIRequestContext> {
  return request.newContext({
    baseURL: `${API_URL}/`,
    ignoreHTTPSErrors: true,
    extraHTTPHeaders: headers,
  });
}

async function exigirOk(res: APIResponse, oque: string): Promise<APIResponse> {
  if (!res.ok()) {
    throw new Error(`${oque}: HTTP ${res.status()} — ${(await res.text()).slice(0, 500)}`);
  }
  return res;
}

async function json<T>(res: APIResponse, oque: string): Promise<T> {
  await exigirOk(res, oque);
  const texto = await res.text();
  return (texto ? JSON.parse(texto) : undefined) as T;
}

// ---------------------------------------------------------------------------
// Público (signup) — sem autenticação
// ---------------------------------------------------------------------------

export interface SignupRequest {
  razaoSocial: string;
  slug: string;
  adminEmail: string;
  adminNome: string;
}

export interface UltimoEmail {
  success: boolean;
  to?: string;
  magicToken?: string;
  temporaryPassword?: string;
}

export async function publico() {
  const ctx = await contexto();
  return {
    signup: async (req: SignupRequest) =>
      json<{ tenantId: string }>(await ctx.post('v1/signup/tenant', { data: req }), `signup ${req.slug}`),
    /** Endpoint de teste (perfis dev/test): dados do último e-mail de ativação. */
    ultimoEmail: async () => json<UltimoEmail>(await ctx.get('v1/test/last-email'), 'last-email'),
    ativar: async (magicToken: string) =>
      exigirOk(await ctx.post('v1/signup/magic-activate', { data: { magicToken } }), 'magic-activate'),
    dispose: () => ctx.dispose(),
  };
}

// ---------------------------------------------------------------------------
// Plataforma — token do operador do e2e, sem X-Tenant-Id (o alvo vai no path)
// ---------------------------------------------------------------------------

export async function plataforma(token: string) {
  const ctx = await contexto({ Authorization: `Bearer ${token}` });
  return {
    listarPlanos: async () =>
      json<Array<{ id: number; nome: string }>>(await ctx.get('v1/platform/planos'), 'listar planos'),
    aprovar: async (tenantId: string) =>
      exigirOk(await ctx.post(`v1/platform/tenants/${tenantId}/approve`), `aprovar ${tenantId}`),
    habilitarEmissora: async (tenantId: string) =>
      exigirOk(
        await ctx.post(`v1/platform/tenants/${tenantId}/habilitar-emissora`),
        `habilitar emissora ${tenantId}`,
      ),
    /** A aprovação cria a Trial (todos os módulos): trocar o plano SEMPRE depois dela. */
    mudarPlano: async (tenantId: string, planoId: number) =>
      exigirOk(
        await ctx.post(`v1/platform/tenants/${tenantId}/plano`, { data: { planoId: String(planoId) } }),
        `mudar plano ${tenantId}`,
      ),
    lancarCreditos: async (tenantId: string, quantidade: number, motivo: string) =>
      exigirOk(
        await ctx.post(`v1/platform/creditos/${tenantId}`, { data: { quantidade, motivo } }),
        `lançar créditos ${tenantId}`,
      ),
    /** IMEDIATO expurga na hora (o export de arquivamento roda antes). */
    excluir: async (tenantId: string, slug: string) =>
      exigirOk(
        await ctx.post(`v1/platform/tenants/${tenantId}/excluir`, {
          data: { modo: 'IMEDIATO', confirmacaoSlug: slug },
        }),
        `excluir ${slug}`,
      ),
    dispose: () => ctx.dispose(),
  };
}

export type ApiPlataforma = Awaited<ReturnType<typeof plataforma>>;

// ---------------------------------------------------------------------------
// Empresa — token do admin (lido da sessão a cada chamada: a jornada é longa)
// ---------------------------------------------------------------------------

export interface ConfigGeral {
  marinhaEmail?: string;
  emailRemetente?: string;
  responsavelNome?: string;
  telefone?: string;
  emailOficial?: string;
  smtpHost?: string;
  smtpPort?: number;
  smtpUsername?: string;
  smtpPassword?: string;
  smtpFrom?: string;
  smtpStarttls?: boolean;
}

export interface Vinculo {
  id: string;
  papel: 'OPERADORA' | 'EMISSORA';
  parceiroTenantId: string;
  parceiroNome: string | null;
  status: 'CONVIDADO' | 'ATIVO' | 'BLOQUEADO' | 'REVOGADO';
  aguardandoMeuAceite: boolean;
}

export async function empresa(tenantId: string, token: () => Promise<string>) {
  const chamar = async <T>(
    metodo: 'get' | 'post' | 'put',
    caminho: string,
    oque: string,
    data?: unknown,
  ): Promise<T> => {
    const ctx = await contexto({ Authorization: `Bearer ${await token()}`, 'X-Tenant-Id': tenantId });
    try {
      return await json<T>(await ctx[metodo](`v1/tenants/${tenantId}/${caminho}`, { data }), oque);
    } finally {
      await ctx.dispose();
    }
  };

  return {
    tenantId,
    salvarPerfilEmissora: (capitaniaId: string, eamaRegistro?: string) =>
      chamar('put', 'config/emissora', 'perfil emissora', { capitaniaId, eamaRegistro }),
    salvarConfigGeral: (cfg: ConfigGeral) => chamar('put', 'config/geral', 'config geral', cfg),
    criarInstrutor: (dados: { nome: string; cpf: string; rg: string; orgaoEmissor: string; cha: string }) =>
      chamar<{ id: string; nome: string }>('post', 'instrutores', 'criar instrutor', dados),
    criarModelo: (nome: string) =>
      chamar<{ id: string; nome: string }>('post', 'modelos', 'criar modelo', {
        nome,
        fabricante: 'Sea-Doo',
        potenciaHp: 90,
        capacidadePessoas: 2,
        precoBaseHora: 120,
        toleranciaMin: 5,
        incluiCombustivel: true,
      }),
    saldo: async () => (await chamar<{ saldo: number }>('get', 'creditos/saldo', 'saldo')).saldo,
    listarVinculos: () => chamar<Vinculo[]>('get', 'vinculos-emissao', 'listar vínculos'),
    /** Resposta crua: os testes de bloqueio precisam do status 400 e da mensagem. */
    emitirCru: async (reservaId: string, reemitir = false) => {
      const ctx = await contexto({ Authorization: `Bearer ${await token()}`, 'X-Tenant-Id': tenantId });
      const res = await ctx.post(
        `v1/tenants/${tenantId}/reservas/${reservaId}/emitir-documentos?reemitir=${reemitir}`,
      );
      const corpo = await res.text();
      await ctx.dispose();
      return { status: res.status(), corpo: corpo ? JSON.parse(corpo) : null };
    },
    /** GRU paga sem o robô da Marinha (a UI nunca envia gruPago; só a API aceita). */
    registrarGruPaga: (reservaId: string, gruNumero: string) =>
      chamar('put', `reservas/${reservaId}/habilitacao`, 'habilitação GRU paga', {
        via: 'EMA',
        gruNumero,
        gruValor: 23.13,
        gruPago: true,
      }),
  };
}

export type ApiEmpresa = Awaited<ReturnType<typeof empresa>>;
