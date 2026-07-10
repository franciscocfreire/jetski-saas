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

  /** Re-cifra os segredos de todos os tenants com a chave atual (rotação de chave). */
  async reencryptSecrets(): Promise<ReencryptResult> {
    const { data } = await apiClient.post<ReencryptResult>('/v1/platform/secrets/reencrypt')
    return data
  },
}

export interface ReencryptResult {
  comSegredo: number
  recifrados: number
  falhas: number
  criptografiaAtiva: boolean
}
