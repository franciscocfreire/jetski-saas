// Cliente das APIs do Meu Jet — as MESMAS que o console e o backoffice chamam.
// Nenhum atalho de teste: se a persona consegue, é porque a API pública permite.

import { ErroHttp, pedir, type Resposta } from './http.ts';

export class Plataforma {
  private readonly baseUrl: string;

  constructor(baseUrl: string) {
    this.baseUrl = baseUrl;
  }

  private async chamar(metodo: 'GET' | 'POST', caminho: string, token?: string, json?: unknown, tenantId?: string): Promise<Resposta> {
    const cabecalhos: Record<string, string> = { Accept: 'application/json' };
    if (token) cabecalhos.Authorization = `Bearer ${token}`;
    if (tenantId) cabecalhos['X-Tenant-Id'] = tenantId;
    return pedir(`${this.baseUrl}${caminho}`, { metodo, cabecalhos, json });
  }

  /** Chamada de staff no escopo de uma empresa (`/v1/tenants/{id}/...`); devolve o JSON ou lança. */
  async naEmpresa<T>(metodo: 'GET' | 'POST', token: string, tenantId: string, caminho: string, json?: unknown): Promise<T> {
    const r = await this.chamar(metodo, `/v1/tenants/${tenantId}${caminho}`, token, json, tenantId);
    if (r.status >= 300) throw new ErroHttp(`${metodo} ${caminho}`, r);
    return (r.corpo ? JSON.parse(r.corpo) : undefined) as T;
  }

  // ---- públicas (cadastro) --------------------------------------------------

  async cadastrarEmpresa(e: { razaoSocial: string; slug: string; adminEmail: string; adminNome: string }): Promise<string> {
    const r = await this.chamar('POST', '/v1/signup/tenant', undefined, e);
    if (r.status >= 300) throw new ErroHttp(`cadastro de ${e.slug}`, r);
    return (JSON.parse(r.corpo) as { tenantId: string }).tenantId;
  }

  /** Auto-cadastro do cliente final no portal. O Keycloak envia o e-mail de verificação. */
  async cadastrarCliente(c: { nome: string; email: string; senha: string }): Promise<void> {
    const r = await this.chamar('POST', '/v1/public/customers/signup', undefined, c);
    if (r.status >= 300) throw new ErroHttp(`cadastro do cliente ${c.email}`, r);
  }

  /** Quem sou eu, no escopo de cliente — prova que o token do portal vale na API. */
  async clienteLogado(token: string): Promise<Resposta> {
    return this.chamar('GET', '/v1/customers/self', token);
  }

  async ativarConta(magicToken: string): Promise<void> {
    const r = await this.chamar('POST', '/v1/signup/magic-activate', undefined, { magicToken });
    if (r.status >= 300) throw new ErroHttp('ativação de conta', r);
  }

  // ---- operador de plataforma (sem X-Tenant-Id: o alvo vai no caminho) ------

  /** true se o token tem papel de plataforma. 403 = ainda não promovido. */
  async ehOperador(token: string): Promise<boolean> {
    const r = await this.chamar('GET', '/v1/platform/operadores', token);
    if (r.status === 200) return true;
    if (r.status === 403 || r.status === 401) return false;
    throw new ErroHttp('checagem de operador', r);
  }

  /** Aprova a empresa. Devolve false se ela já não estava pendente (409) — idempotente. */
  async aprovarEmpresa(token: string, tenantId: string): Promise<boolean> {
    const r = await this.chamar('POST', `/v1/platform/tenants/${tenantId}/approve`, token, {});
    if (r.status === 409) return false;
    if (r.status >= 300) throw new ErroHttp(`aprovação de ${tenantId}`, r);
    return true;
  }

  async listarPlanos(token: string): Promise<{ id: number; nome: string }[]> {
    const r = await this.chamar('GET', '/v1/platform/planos', token);
    if (r.status !== 200) throw new ErroHttp('lista de planos', r);
    return JSON.parse(r.corpo) as { id: number; nome: string }[];
  }

  async mudarPlano(token: string, tenantId: string, planoId: number): Promise<void> {
    const r = await this.chamar('POST', `/v1/platform/tenants/${tenantId}/plano`, token, { planoId: String(planoId) });
    if (r.status >= 300) throw new ErroHttp(`mudança de plano de ${tenantId}`, r);
  }

  // ---- admin da empresa -------------------------------------------------------

  async criarModelo(token: string, tenantId: string, modelo: Record<string, unknown>): Promise<string> {
    const r = await this.chamar('POST', `/v1/tenants/${tenantId}/modelos`, token, modelo, tenantId);
    if (r.status >= 300) throw new ErroHttp('criação de modelo', r);
    return (JSON.parse(r.corpo) as { id: string }).id;
  }

  async listarJetskis(token: string, tenantId: string): Promise<{ id: string; serie: string }[]> {
    const r = await this.chamar('GET', `/v1/tenants/${tenantId}/jetskis`, token, undefined, tenantId);
    if (r.status !== 200) throw new ErroHttp('lista de jetskis', r);
    return JSON.parse(r.corpo) as { id: string; serie: string }[];
  }

  async listarModelos(token: string, tenantId: string): Promise<{ id: string; nome: string }[]> {
    const r = await this.chamar('GET', `/v1/tenants/${tenantId}/modelos`, token, undefined, tenantId);
    if (r.status !== 200) throw new ErroHttp('lista de modelos', r);
    return JSON.parse(r.corpo) as { id: string; nome: string }[];
  }

  async criarJetski(token: string, tenantId: string, jetski: Record<string, unknown>): Promise<void> {
    const r = await this.chamar('POST', `/v1/tenants/${tenantId}/jetskis`, token, jetski, tenantId);
    if (r.status >= 300) throw new ErroHttp(`criação do jetski ${String(jetski.serie)}`, r);
  }
}
