import { apiClient, getTenantId } from '../client'
import type { Habilitacao, HabilitacaoRequest } from '../types'

const path = (reservaId: string) =>
  `/v1/tenants/${getTenantId()}/reservas/${reservaId}/habilitacao`

export const habilitacaoService = {
  /** Registra/atualiza a habilitação do condutor (CHA ou EMA+GRU). */
  async registrar(reservaId: string, req: HabilitacaoRequest): Promise<Habilitacao> {
    const { data } = await apiClient.put<Habilitacao>(path(reservaId), req)
    return data
  },

  /** Habilitação atual da reserva (null se ainda não registrada). */
  async get(reservaId: string): Promise<Habilitacao | null> {
    try {
      const { data } = await apiClient.get<Habilitacao>(path(reservaId))
      return data
    } catch {
      return null
    }
  },
}
