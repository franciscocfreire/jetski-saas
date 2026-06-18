import { apiClient, getTenantId } from '../client'
import type { DocumentoEmitido } from '../types'

const getBasePath = () => `/v1/tenants/${getTenantId()}/documentos`

export const documentosService = {
  /** Lista documentos emitidos; filtra por cliente quando clienteId é informado. */
  async list(clienteId?: string): Promise<DocumentoEmitido[]> {
    const { data } = await apiClient.get<DocumentoEmitido[]>(getBasePath(), {
      params: clienteId ? { clienteId } : undefined,
    })
    return data
  },
}
