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

export interface ModeloApi {
  id: string;
  nome: string;
  fabricante?: string | null;
  potenciaHp?: number | null;
  capacidadePessoas?: number | null;
  precoBaseHora: number;
  taxaHoraExtra?: number | null;
  toleranciaMin?: number | null;
  caucao?: number | null;
  ativo?: boolean;
}

export interface JetskiApi {
  id: string;
  modeloId: string;
  serie: string;
  ano?: number | null;
  horimetroAtual?: number | null;
  status?: string;
  ativo?: boolean;
}

export interface InstrutorApi {
  id: string;
  nome: string;
  rg?: string | null;
  orgaoEmissor?: string | null;
  cpf?: string | null;
  cha?: string | null;
  dataEmissao?: string | null;
  temAssinatura?: boolean;
  ativo?: boolean;
}

/** Listagens podem vir como array puro ou paginadas ({ content: [...] }). */
function lista<T>(corpo: unknown): T[] {
  if (Array.isArray(corpo)) return corpo as T[];
  const c = (corpo as { content?: T[] } | null)?.content;
  return Array.isArray(c) ? c : [];
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
    listarModelos: async () => lista<ModeloApi>(await chamar('get', 'modelos', 'listar modelos')),
    listarJetskis: async () => lista<JetskiApi>(await chamar('get', 'jetskis', 'listar jetskis')),
    criarJetski: (modeloId: string, serie: string, ano = 2024) =>
      chamar<JetskiApi>('post', 'jetskis', `criar jetski ${serie}`, { modeloId, serie, ano, horimetroAtual: 0 }),
    listarInstrutores: async () =>
      lista<InstrutorApi>(await chamar('get', 'instrutores?includeInactive=true', 'listar instrutores')),
    listarVinculos: () => chamar<Vinculo[]>('get', 'vinculos-emissao', 'listar vínculos'),
    /** Convite pela API com resposta crua: o teste negativo precisa do 400 e da mensagem. */
    convidarCru: async (parceiroSlug: string, papel: 'OPERADORA' | 'EMISSORA') => {
      const ctx = await contexto({ Authorization: `Bearer ${await token()}`, 'X-Tenant-Id': tenantId });
      const res = await ctx.post(`v1/tenants/${tenantId}/vinculos-emissao`, { data: { parceiroSlug, papel } });
      const corpo = await res.text();
      await ctx.dispose();
      return { status: res.status(), corpo: corpo ? JSON.parse(corpo) : null };
    },
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
    /** Instrutor da demonstração na habilitação (o balcão faz o mesmo ao escolher no dropdown). */
    definirInstrutor: (reservaId: string, instrutorId: string) =>
      chamar('put', `reservas/${reservaId}/habilitacao`, 'habilitação instrutor', { via: 'EMA', instrutorId }),
    /** Modo de emissão decidido pelo backend (§8.M): a parceria em vigor manda, não o plano. */
    modoEmissao: () =>
      chamar<{ modo: 'PROPRIA' | 'DELEGADA' | 'SEM_EMISSAO'; vinculoId: string | null; vinculoStatus: string | null }>(
        'get',
        'vinculos-emissao/modo',
        'modo de emissão',
      ),
    /** Instrutores disponíveis na emissão delegada: da EAMA ou da operadora aprovados pela EAMA. */
    instrutoresParceiro: () =>
      chamar<Array<{ id: string; nome: string; origem: 'EAMA' | 'OPERADORA' }>>(
        'get',
        'vinculos-emissao/instrutores-parceiro',
        'instrutores do parceiro',
      ),
    perfilEmissora: () => chamar<{ emissoraHabilitada: boolean }>('get', 'config/emissora', 'perfil emissora'),
  };
}

export type ApiEmpresa = Awaited<ReturnType<typeof empresa>>;
