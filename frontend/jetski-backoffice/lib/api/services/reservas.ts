import { apiClient, getTenantId } from '../client'
import type { Reserva, ReservaCreateRequest, ReservaStatus } from '../types'

const getBasePath = () => `/v1/tenants/${getTenantId()}/reservas`

export const reservasService = {
  // Backend returns a simple list, not a paginated response
  async list(params?: {
    status?: ReservaStatus
    dataInicio?: string
    dataFim?: string
  }): Promise<Reserva[]> {
    const { data } = await apiClient.get<Reserva[]>(getBasePath(), { params })
    return data
  },

  async getById(id: string): Promise<Reserva> {
    const { data } = await apiClient.get<Reserva>(`${getBasePath()}/${id}`)
    return data
  },

  async create(request: ReservaCreateRequest): Promise<Reserva> {
    const { data } = await apiClient.post<Reserva>(getBasePath(), request)
    return data
  },

  async confirmar(id: string): Promise<Reserva> {
    const { data } = await apiClient.post<Reserva>(`${getBasePath()}/${id}/confirmar`)
    return data
  },

  async alocarJetski(id: string, jetskiId: string): Promise<Reserva> {
    const { data } = await apiClient.post<Reserva>(`${getBasePath()}/${id}/alocar-jetski`, {
      jetskiId,
    })
    return data
  },
}
