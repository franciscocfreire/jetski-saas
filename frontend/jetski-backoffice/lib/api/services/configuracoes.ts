import { apiClient, getTenantId } from '../client'
import type {
  ComissaoConfig,
  ComissaoConfigRequest,
  TenantGeralConfig,
  TenantGeralConfigRequest,
} from '../types'

const getBasePath = () => `/v1/tenants/${getTenantId()}/config`

/**
 * Service for tenant configuration operations.
 * Manages commission percentages and bonus settings.
 */
export const configuracoesService = {
  /**
   * Get the current commission and bonus configuration for the tenant.
   * @returns ComissaoConfig with all settings
   */
  async getComissaoConfig(): Promise<ComissaoConfig> {
    const { data } = await apiClient.get<ComissaoConfig>(`${getBasePath()}/comissao`)
    return data
  },

  /**
   * Update the commission and bonus configuration for the tenant.
   * Requires ADMIN_TENANT or GERENTE role.
   * @param request Configuration update request
   * @returns Updated ComissaoConfig
   */
  async updateComissaoConfig(request: ComissaoConfigRequest): Promise<ComissaoConfig> {
    const { data } = await apiClient.put<ComissaoConfig>(`${getBasePath()}/comissao`, request)
    return data
  },

  /** Dados gerais/e-mail da empresa (tenant). */
  async getTenantConfig(): Promise<TenantGeralConfig> {
    const { data } = await apiClient.get<TenantGeralConfig>(`${getBasePath()}/geral`)
    return data
  },

  async updateTenantConfig(request: TenantGeralConfigRequest): Promise<TenantGeralConfig> {
    const { data } = await apiClient.put<TenantGeralConfig>(`${getBasePath()}/geral`, request)
    return data
  },
}
