import { apiClient, getTenantId } from '../client'
import type { Cliente, ClienteCreateRequest } from '../types'

const getBasePath = () => `/v1/tenants/${getTenantId()}/clientes`

export const clientesService = {
  // Backend returns a simple list, not a paginated response
  async list(params?: { includeInactive?: boolean }): Promise<Cliente[]> {
    const { data } = await apiClient.get<Cliente[]>(getBasePath(), { params })
    return data
  },

  async getById(id: string): Promise<Cliente> {
    const { data } = await apiClient.get<Cliente>(`${getBasePath()}/${id}`)
    return data
  },

  async create(request: ClienteCreateRequest): Promise<Cliente> {
    const { data } = await apiClient.post<Cliente>(getBasePath(), request)
    return data
  },

  async update(id: string, request: Partial<ClienteCreateRequest>): Promise<Cliente> {
    const { data } = await apiClient.put<Cliente>(`${getBasePath()}/${id}`, request)
    return data
  },
}
