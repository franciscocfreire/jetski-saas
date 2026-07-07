import { apiClient, getTenantId } from '../client'
import type { Gru } from '../types'

/**
 * Módulo GRUs: ciclo das GRUs emitidas via EMA — geração, pagamento, emissão,
 * envio à Marinha e confirmação pela devolutiva.
 */
export const grusService = {
  async list(): Promise<Gru[]> {
    const { data } = await apiClient.get<Gru[]>(`/v1/tenants/${getTenantId()}/grus`)
    return data
  },
}
