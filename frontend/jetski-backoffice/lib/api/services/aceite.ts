import { apiClient, getTenantId } from '../client'
import type { Aceite, AceiteRequest } from '../types'

const path = (reservaId: string) =>
  `/v1/tenants/${getTenantId()}/reservas/${reservaId}/aceite`

export const aceiteService = {
  /** Grava o aceite/assinatura (a imagem PNG vai em base64 no corpo). */
  async registrar(reservaId: string, req: AceiteRequest): Promise<Aceite> {
    const { data } = await apiClient.post<Aceite>(path(reservaId), req)
    return data
  },

  /** Aceite atual (o mais recente) ou null. */
  async get(reservaId: string): Promise<Aceite | null> {
    try {
      const { data } = await apiClient.get<Aceite>(path(reservaId))
      return data
    } catch {
      return null
    }
  },
}
