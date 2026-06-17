import { apiClient, getTenantId } from '../client'
import type { ClaimResult } from '../types'

const path = (clienteId: string) =>
  `/v1/tenants/${getTenantId()}/clientes/${clienteId}/claim`

export const claimService = {
  /** Gera e envia o claim-token de ativação ao cliente. */
  async gerar(clienteId: string, canais?: string): Promise<ClaimResult> {
    const { data } = await apiClient.post<ClaimResult>(path(clienteId), null, {
      params: canais ? { canais } : undefined,
    })
    return data
  },

  /** Reenvia o claim-token (desativa o anterior e emite outro). */
  async reenviar(clienteId: string, canais?: string): Promise<ClaimResult> {
    const { data } = await apiClient.post<ClaimResult>(`${path(clienteId)}/reenviar`, null, {
      params: canais ? { canais } : undefined,
    })
    return data
  },
}
