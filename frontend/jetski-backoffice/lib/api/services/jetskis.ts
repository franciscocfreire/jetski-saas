import { apiClient, getTenantId } from '../client'
import type { Jetski, JetskiCreateRequest, JetskiUpdateRequest, JetskiStatus } from '../types'

const getBasePath = () => `/v1/tenants/${getTenantId()}/jetskis`

export const jetskisService = {
  // Backend returns a simple list, not a paginated response
  async list(params?: { status?: JetskiStatus; includeInactive?: boolean }): Promise<Jetski[]> {
    const { data } = await apiClient.get<Jetski[]>(getBasePath(), { params })
    return data
  },

  async getById(id: string): Promise<Jetski> {
    const { data } = await apiClient.get<Jetski>(`${getBasePath()}/${id}`)
    return data
  },

  async create(request: JetskiCreateRequest): Promise<Jetski> {
    const { data } = await apiClient.post<Jetski>(getBasePath(), request)
    return data
  },

  async update(id: string, request: JetskiUpdateRequest): Promise<Jetski> {
    const { data } = await apiClient.put<Jetski>(`${getBasePath()}/${id}`, request)
    return data
  },

  async reactivate(id: string): Promise<Jetski> {
    const { data } = await apiClient.post<Jetski>(`${getBasePath()}/${id}/reactivate`)
    return data
  },

  async updateStatus(id: string, status: JetskiStatus): Promise<Jetski> {
    const { data } = await apiClient.patch<Jetski>(`${getBasePath()}/${id}/status`, null, { params: { status } })
    return data
  },

  async deactivate(id: string): Promise<Jetski> {
    const { data } = await apiClient.delete<Jetski>(`${getBasePath()}/${id}`)
    return data
  },
}
