import { apiClient, getTenantId } from '../client'
import type { EmissaoMensal, PlatformEmissaoTenant } from '../types'

/**
 * Metering de emissões (base da cobrança futura por plano).
 * Prévias não são cobráveis — são sinal de acompanhamento.
 */
export const meteringService = {
  /** Série mensal do tenant corrente (últimos N meses, máx. 24). */
  async getEmissoesMensais(meses = 6): Promise<EmissaoMensal[]> {
    const { data } = await apiClient.get<EmissaoMensal[]>(
      `/v1/tenants/${getTenantId()}/metering/emissoes`,
      { params: { meses } }
    )
    return data
  },

  /** Visão do super admin: emissões por empresa na competência (YYYY-MM). */
  async getPlatformEmissoes(competencia?: string): Promise<PlatformEmissaoTenant[]> {
    const { data } = await apiClient.get<PlatformEmissaoTenant[]>(
      '/v1/platform/metering/emissoes',
      { params: competencia ? { competencia } : {} }
    )
    return data
  },
}
