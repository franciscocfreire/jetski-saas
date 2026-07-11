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

  /** Re-cifra os segredos de todos os tenants com a chave atual (rotação de chave). */
  async reencryptSecrets(): Promise<ReencryptResult> {
    const { data } = await apiClient.post<ReencryptResult>('/v1/platform/secrets/reencrypt')
    return data
  },
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
