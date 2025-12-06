import { apiClient, getTenantId } from '../client'
import type { ItemOpcional, ItemOpcionalCreateRequest } from '../types'

const getBasePath = () => `/v1/tenants/${getTenantId()}/itens-opcionais`

export const itensOpcionaisService = {
  async list(params?: { includeInactive?: boolean }): Promise<ItemOpcional[]> {
    const { data } = await apiClient.get<ItemOpcional[]>(getBasePath(), { params })
    return data
  },

  async getById(id: string): Promise<ItemOpcional> {
    const { data } = await apiClient.get<ItemOpcional>(`${getBasePath()}/${id}`)
    return data
  },

  async create(request: ItemOpcionalCreateRequest): Promise<ItemOpcional> {
    const { data } = await apiClient.post<ItemOpcional>(getBasePath(), request)
    return data
  },

  async update(id: string, request: Partial<ItemOpcionalCreateRequest>): Promise<ItemOpcional> {
    const { data } = await apiClient.put<ItemOpcional>(`${getBasePath()}/${id}`, request)
    return data
  },

  async delete(id: string): Promise<ItemOpcional> {
    const { data } = await apiClient.delete<ItemOpcional>(`${getBasePath()}/${id}`)
    return data
  },
}
