import { apiClient, getTenantId } from '../client'
import type { Manutencao, ManutencaoCreateRequest, ManutencaoFinishRequest, ManutencaoStatus } from '../types'

const getBasePath = () => `/v1/tenants/${getTenantId()}/manutencoes`

export const manutencoesService = {
  // Backend returns a simple list, not a paginated response
  async list(params?: {
    status?: ManutencaoStatus
    jetskiId?: string
  }): Promise<Manutencao[]> {
    const { data } = await apiClient.get<Manutencao[]>(getBasePath(), { params })
    return data
  },

  async getById(id: string): Promise<Manutencao> {
    const { data } = await apiClient.get<Manutencao>(`${getBasePath()}/${id}`)
    return data
  },

  async create(request: ManutencaoCreateRequest): Promise<Manutencao> {
    const { data } = await apiClient.post<Manutencao>(getBasePath(), request)
    return data
  },

  async update(id: string, request: Partial<ManutencaoCreateRequest>): Promise<Manutencao> {
    const { data } = await apiClient.put<Manutencao>(`${getBasePath()}/${id}`, request)
    return data
  },

  // Status transitions
  async start(id: string): Promise<Manutencao> {
    const { data } = await apiClient.post<Manutencao>(`${getBasePath()}/${id}/start`)
    return data
  },

  async waitForParts(id: string): Promise<Manutencao> {
    const { data } = await apiClient.post<Manutencao>(`${getBasePath()}/${id}/wait-for-parts`)
    return data
  },

  async resume(id: string): Promise<Manutencao> {
    const { data } = await apiClient.post<Manutencao>(`${getBasePath()}/${id}/resume`)
    return data
  },

  async finish(id: string, request: ManutencaoFinishRequest): Promise<Manutencao> {
    const { data } = await apiClient.post<Manutencao>(`${getBasePath()}/${id}/finish`, request)
    return data
  },

  async cancel(id: string): Promise<Manutencao> {
    const { data } = await apiClient.delete<Manutencao>(`${getBasePath()}/${id}`)
    return data
  },
}
