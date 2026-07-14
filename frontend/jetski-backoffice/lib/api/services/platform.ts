import { apiClient } from '../client'
import type { TenantStatusResult, TenantSummary } from '../types'

/**
 * Service: Platform admin (super admin global).
 *
 * Aprovação/bloqueio de empresas. As ações em uma empresa específica enviam
 * X-Tenant-Id da empresa alvo (super admin opera "tenant por tenant"), enquanto
 * a listagem usa o tenant atual da sessão (qualquer um — acesso irrestrito).
 */
export const platformService = {
  /** Lista TODAS as empresas (qualquer status) — visão completa do super admin. */
  async listAllTenants(): Promise<TenantSummary[]> {
    const { data } = await apiClient.get<TenantSummary[]>('/v1/platform/tenants')
    return data
  },

  /** Aprova uma empresa pendente (→ ATIVO + trial). */
  async approve(tenantId: string): Promise<TenantStatusResult> {
    const { data } = await apiClient.post<TenantStatusResult>(
      `/v1/platform/tenants/${tenantId}/approve`,
      undefined,
      { headers: { 'X-Tenant-Id': tenantId } }
    )
    return data
  },

  /** Suspende uma empresa ativa (→ SUSPENSO). */
  async suspend(tenantId: string, motivo?: string): Promise<TenantStatusResult> {
    const { data } = await apiClient.post<TenantStatusResult>(
      `/v1/platform/tenants/${tenantId}/suspend`,
      { motivo: motivo ?? null },
      { headers: { 'X-Tenant-Id': tenantId } }
    )
    return data
  },

  /** Reativa uma empresa suspensa (→ ATIVO). */
  async reactivate(tenantId: string): Promise<TenantStatusResult> {
    const { data } = await apiClient.post<TenantStatusResult>(
      `/v1/platform/tenants/${tenantId}/reactivate`,
      undefined,
      { headers: { 'X-Tenant-Id': tenantId } }
    )
    return data
  },

  /** Dry-run do reset: contagem por tabela do que o nível apagaria. */
  async resetPreview(tenantId: string, nivel: ResetNivel): Promise<Record<string, number>> {
    const { data } = await apiClient.get<Record<string, number>>(
      `/v1/platform/tenants/${tenantId}/reset-preview`,
      { params: { nivel }, headers: { 'X-Tenant-Id': tenantId } }
    )
    return data
  },

  /** RESET da empresa (zona de perigo) — exige o slug digitado. */
  async resetEmpresa(
    tenantId: string,
    nivel: ResetNivel,
    confirmacaoSlug: string
  ): Promise<ResetResult> {
    const { data } = await apiClient.post<ResetResult>(
      `/v1/platform/tenants/${tenantId}/reset`,
      { nivel, confirmacaoSlug },
      { headers: { 'X-Tenant-Id': tenantId } }
    )
    return data
  },

  /** EXCLUSÃO da empresa: CARENCIA (suspende + expurgo em 30d) ou IMEDIATO. */
  async excluirEmpresa(
    tenantId: string,
    modo: 'CARENCIA' | 'IMEDIATO',
    confirmacaoSlug: string
  ): Promise<{ modo: string; expurgoEm?: string; totalLinhas?: number }> {
    const { data } = await apiClient.post(
      `/v1/platform/tenants/${tenantId}/excluir`,
      { modo, confirmacaoSlug },
      { headers: { 'X-Tenant-Id': tenantId }, timeout: 300_000 }
    )
    return data
  },

  /** Cancela uma exclusão agendada (a empresa segue suspensa). */
  async cancelarExclusao(tenantId: string): Promise<void> {
    await apiClient.post(
      `/v1/platform/tenants/${tenantId}/cancelar-exclusao`,
      undefined,
      { headers: { 'X-Tenant-Id': tenantId } }
    )
  },

  /** Gera o export de arquivamento (.zip com dados + arquivos) da empresa. */
  async exportTenant(tenantId: string): Promise<TenantExport> {
    const { data } = await apiClient.post<TenantExport>(
      `/v1/platform/tenants/${tenantId}/export`,
      undefined,
      { headers: { 'X-Tenant-Id': tenantId }, timeout: 300_000 }
    )
    return data
  },

  /** Baixa um export (.zip) como blob. */
  async downloadExport(tenantId: string, key: string): Promise<Blob> {
    const { data } = await apiClient.get<Blob>(
      `/v1/platform/tenants/${tenantId}/exports/download`,
      {
        params: { key },
        headers: { 'X-Tenant-Id': tenantId },
        responseType: 'blob',
        timeout: 300_000,
      }
    )
    return data
  },

  /** Fila global de faturas EM_CONFERENCIA (billing manual assistido). */
  async faturasPendentes(): Promise<FaturaPendente[]> {
    const { data } = await apiClient.get<FaturaPendente[]>('/v1/platform/faturas/pendentes')
    return data
  },

  /** Confirma a fatura conferida no extrato → PAGA. */
  async confirmarFatura(tenantId: string, faturaId: string): Promise<void> {
    await apiClient.post(`/v1/platform/faturas/${tenantId}/${faturaId}/confirmar`, undefined,
      { headers: { 'X-Tenant-Id': tenantId } })
  },

  /** Cancela a fatura (cortesia/erro) — observação obrigatória. */
  async cancelarFatura(tenantId: string, faturaId: string, observacao: string): Promise<void> {
    await apiClient.post(`/v1/platform/faturas/${tenantId}/${faturaId}/cancelar`, { observacao },
      { headers: { 'X-Tenant-Id': tenantId } })
  },

  /** Planos disponíveis (seletor de troca de plano). */
  async planos(): Promise<PlanoInfo[]> {
    const { data } = await apiClient.get<PlanoInfo[]>('/v1/platform/planos')
    return data
  },

  /** Troca o plano do tenant (contratação pós-trial / upgrade). */
  async mudarPlano(tenantId: string, planoId: string): Promise<void> {
    await apiClient.post(`/v1/platform/tenants/${tenantId}/plano`, { planoId },
      { headers: { 'X-Tenant-Id': tenantId } })
  },

  /** Catálogo de módulos gateáveis por plano (enum ModuloPlano do backend). */
  async modulos(): Promise<ModuloCatalogo[]> {
    const { data } = await apiClient.get<ModuloCatalogo[]>('/v1/platform/modulos')
    return data
  },

  /** Define os módulos incluídos no plano (controle de oferta). */
  async salvarModulosDoPlano(planoId: string, modulos: string[]): Promise<void> {
    await apiClient.put(`/v1/platform/planos/${planoId}/modulos`, { modulos })
  },

  /** Re-cifra os segredos de todos os tenants com a chave atual (rotação de chave). */
  async reencryptSecrets(): Promise<ReencryptResult> {
    const { data } = await apiClient.post<ReencryptResult>('/v1/platform/secrets/reencrypt')
    return data
  },

  // ===== Emissão delegada (V047): habilitação de EAMA + catálogo de capitanias =====

  /** Habilita a empresa como EAMA emissora (exige capitania + registro declarados). */
  async habilitarEmissora(tenantId: string): Promise<void> {
    await apiClient.post(`/v1/platform/tenants/${tenantId}/habilitar-emissora`, undefined,
      { headers: { 'X-Tenant-Id': tenantId } })
  },

  /** Remove a habilitação de emissora (revalidação/irregularidade). */
  async desabilitarEmissora(tenantId: string): Promise<void> {
    await apiClient.post(`/v1/platform/tenants/${tenantId}/desabilitar-emissora`, undefined,
      { headers: { 'X-Tenant-Id': tenantId } })
  },

  /** Catálogo completo de capitanias (inclusive inativas). */
  async listCapitanias(): Promise<PlatformCapitania[]> {
    const { data } = await apiClient.get<PlatformCapitania[]>('/v1/platform/capitanias')
    return data
  },

  async criarCapitania(req: CapitaniaEditRequest): Promise<PlatformCapitania> {
    const { data } = await apiClient.post<PlatformCapitania>('/v1/platform/capitanias', req)
    return data
  },

  async atualizarCapitania(id: string, req: CapitaniaEditRequest): Promise<PlatformCapitania> {
    const { data } = await apiClient.put<PlatformCapitania>(`/v1/platform/capitanias/${id}`, req)
    return data
  },
}

export interface PlatformCapitania {
  id: string
  codigo: string
  nome: string
  uf: string | null
  emailOficial: string | null
  ativa: boolean
}

export interface CapitaniaEditRequest {
  codigo: string
  nome: string
  uf?: string | null
  emailOficial?: string | null
  ativa?: boolean | null
}

export interface FaturaPendente {
  fatura: {
    id: string
    competencia: string
    planoNome: string
    valor: number
    txidInformado?: string
    vencimento: string
  }
  tenantId: string
  slug: string
  razaoSocial: string
}

export interface PlanoInfo {
  id: string
  nome: string
  precoMensal: number
  limites: string
  /** JSON array de chaves de módulos (texto) ou null = todos. */
  modulos?: string | null
}

export interface ModuloCatalogo {
  key: string
  rotulo: string
  descricao: string
}

export interface TenantExport {
  key: string
  bytes: number
  tabelas: number
  arquivos: number
}

/** Níveis do reset — cada um é superconjunto do anterior. */
export type ResetNivel = 'OPERACIONAL' | 'FROTA' | 'TOTAL'

export interface ResetResult {
  nivel: ResetNivel
  apagados: Record<string, number>
  totalLinhas: number
}

export interface ReencryptResult {
  comSegredo: number
  recifrados: number
  falhas: number
  criptografiaAtiva: boolean
}
