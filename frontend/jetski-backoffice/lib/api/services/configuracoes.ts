import { apiClient, getTenantId } from '../client'
import type {
  AssinaturaConfig,
  Branding,
  ComissaoConfig,
  ComissaoConfigRequest,
  DocumentoConfig,
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

  /** Parametrização de emissão: o que vai para Marinha vs Cliente. */
  async getDocumentoConfig(): Promise<DocumentoConfig> {
    const { data } = await apiClient.get<DocumentoConfig>(`${getBasePath()}/documento`)
    return data
  },

  async updateDocumentoConfig(request: DocumentoConfig): Promise<DocumentoConfig> {
    const { data } = await apiClient.put<DocumentoConfig>(`${getBasePath()}/documento`, request)
    return data
  },

  /** Reforço jurídico da assinatura (página de auditoria + carimbo de tempo). */
  async getAssinaturaConfig(): Promise<AssinaturaConfig> {
    const { data } = await apiClient.get<AssinaturaConfig>(`${getBasePath()}/assinatura`)
    return data
  },

  async updateAssinaturaConfig(request: AssinaturaConfig): Promise<AssinaturaConfig> {
    const { data } = await apiClient.put<AssinaturaConfig>(`${getBasePath()}/assinatura`, request)
    return data
  },

  /** Branding white-label (cores + logo como data URL). */
  async getBrandingConfig(): Promise<Branding> {
    const { data } = await apiClient.get<Branding>(`${getBasePath()}/branding`)
    return data
  },

  async updateBrandingConfig(request: { corPrimaria?: string | null; corSecundaria?: string | null }): Promise<Branding> {
    const { data } = await apiClient.put<Branding>(`${getBasePath()}/branding`, request)
    return data
  },

  async uploadBrandingLogo(file: File): Promise<Branding> {
    const form = new FormData()
    form.append('file', file)
    const { data } = await apiClient.post<Branding>(`${getBasePath()}/branding/logo`, form, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
    return data
  },

  async deleteBrandingLogo(): Promise<Branding> {
    const { data } = await apiClient.delete<Branding>(`${getBasePath()}/branding/logo`)
    return data
  },
}
