import { apiClient, getTenantId } from '../client'
import type {
  Locacao,
  LocacaoStatus,
  CheckInFromReservaRequest,
  CheckInWalkInRequest,
  CheckOutRequest,
} from '../types'

const getBasePath = () => `/v1/tenants/${getTenantId()}/locacoes`

export const locacoesService = {
  // Backend returns a simple list, not a paginated response
  async list(params?: {
    status?: LocacaoStatus
    jetskiId?: string
    clienteId?: string
  }): Promise<Locacao[]> {
    const { data } = await apiClient.get<Locacao[]>(getBasePath(), { params })
    return data
  },

  async getById(id: string): Promise<Locacao> {
    const { data } = await apiClient.get<Locacao>(`${getBasePath()}/${id}`)
    return data
  },

  async checkInFromReserva(request: CheckInFromReservaRequest): Promise<Locacao> {
    const { data } = await apiClient.post<Locacao>(`${getBasePath()}/check-in/reserva`, request)
    return data
  },

  async checkInWalkIn(request: CheckInWalkInRequest): Promise<Locacao> {
    const { data } = await apiClient.post<Locacao>(`${getBasePath()}/check-in/walk-in`, request)
    return data
  },

  async checkOut(id: string, request: CheckOutRequest): Promise<Locacao> {
    const { data } = await apiClient.post<Locacao>(`${getBasePath()}/${id}/check-out`, request)
    return data
  },

  async updateDataCheckIn(id: string, dataCheckIn: string): Promise<Locacao> {
    const { data } = await apiClient.patch<Locacao>(`${getBasePath()}/${id}/data-check-in`, { dataCheckIn })
    return data
  },
}
