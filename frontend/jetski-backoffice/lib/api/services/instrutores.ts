import { apiClient, getTenantId } from '../client'
import type { Instrutor, InstrutorCreateRequest } from '../types'

const getBasePath = () => `/v1/tenants/${getTenantId()}/instrutores`

export const instrutoresService = {
  async list(params?: { includeInactive?: boolean }): Promise<Instrutor[]> {
    const { data } = await apiClient.get<Instrutor[]>(getBasePath(), { params })
    return data
  },

  async getById(id: string): Promise<Instrutor> {
    const { data } = await apiClient.get<Instrutor>(`${getBasePath()}/${id}`)
    return data
  },

  async create(request: InstrutorCreateRequest): Promise<Instrutor> {
    const { data } = await apiClient.post<Instrutor>(getBasePath(), request)
    return data
  },

  async update(id: string, request: Partial<InstrutorCreateRequest>): Promise<Instrutor> {
    const { data } = await apiClient.put<Instrutor>(`${getBasePath()}/${id}`, request)
    return data
  },

  async deactivate(id: string): Promise<Instrutor> {
    const { data } = await apiClient.delete<Instrutor>(`${getBasePath()}/${id}`)
    return data
  },

  async reactivate(id: string): Promise<Instrutor> {
    const { data } = await apiClient.post<Instrutor>(`${getBasePath()}/${id}/reativar`)
    return data
  },
}
