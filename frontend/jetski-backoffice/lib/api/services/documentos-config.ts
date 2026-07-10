import { apiClient, getTenantId } from '../client'
import type { ImagemCompressaoConfig } from '../types'

/**
 * Config de compressão de imagem por tipo de documento.
 * - Tenant (leitura, aplicada no upload): GET /v1/tenants/{t}/documentos/imagem-config
 * - Super admin (leitura/escrita): /v1/platform/documentos/imagem-config
 */
export const documentosConfigService = {
  /** Presets vigentes lidos pelo backoffice do tenant (aplicados no navegador). */
  async getImagemConfig(): Promise<ImagemCompressaoConfig> {
    const { data } = await apiClient.get<ImagemCompressaoConfig>(
      `/v1/tenants/${getTenantId()}/documentos/imagem-config`
    )
    return data
  },

  /** Config vigente na visão do super admin (plataforma). */
  async getPlatformImagemConfig(): Promise<ImagemCompressaoConfig> {
    const { data } = await apiClient.get<ImagemCompressaoConfig>('/v1/platform/documentos/imagem-config')
    return data
  },

  /** Grava a config (super admin). */
  async atualizarImagemConfig(config: ImagemCompressaoConfig): Promise<ImagemCompressaoConfig> {
    const { data } = await apiClient.put<ImagemCompressaoConfig>(
      '/v1/platform/documentos/imagem-config',
      config
    )
    return data
  },
}
