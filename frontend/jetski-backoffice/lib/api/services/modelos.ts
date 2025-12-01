import { apiClient, getTenantId } from '../client'
import type { Modelo } from '../types'

const getBasePath = () => `/v1/tenants/${getTenantId()}/modelos`

export interface ModeloCreateRequest {
  nome: string
  fabricante?: string
  potenciaHp?: number
  capacidadePessoas: number
  precoBaseHora: number
  toleranciaMin?: number
  taxaHoraExtra?: number
  incluiCombustivel?: boolean
  caucao?: number
  fotoReferenciaUrl?: string
}

export const modelosService = {
  // Backend returns a simple list, not a paginated response
  async list(params?: { includeInactive?: boolean }): Promise<Modelo[]> {
    const { data } = await apiClient.get<Modelo[]>(getBasePath(), { params })
    return data
  },

  async getById(id: string): Promise<Modelo> {
    const { data } = await apiClient.get<Modelo>(`${getBasePath()}/${id}`)
    return data
  },

  async create(request: ModeloCreateRequest): Promise<Modelo> {
    const { data } = await apiClient.post<Modelo>(getBasePath(), request)
    return data
  },

  async update(id: string, request: Partial<ModeloCreateRequest>): Promise<Modelo> {
    const { data } = await apiClient.put<Modelo>(`${getBasePath()}/${id}`, request)
    return data
  },
}
