import { apiClient, getTenantId } from '../client'
import type { Vendedor, VendedorCreateRequest } from '../types'

const getBasePath = () => `/v1/tenants/${getTenantId()}/vendedores`

export const vendedoresService = {
  // Backend returns a simple list, not a paginated response
  async list(params?: { includeInactive?: boolean }): Promise<Vendedor[]> {
    const { data } = await apiClient.get<Vendedor[]>(getBasePath(), { params })
    return data
  },

  async getById(id: string): Promise<Vendedor> {
    const { data } = await apiClient.get<Vendedor>(`${getBasePath()}/${id}`)
    return data
  },

  async create(request: VendedorCreateRequest): Promise<Vendedor> {
    const { data } = await apiClient.post<Vendedor>(getBasePath(), request)
    return data
  },

  async update(id: string, request: Partial<VendedorCreateRequest>): Promise<Vendedor> {
    const { data } = await apiClient.put<Vendedor>(`${getBasePath()}/${id}`, request)
    return data
  },
}
