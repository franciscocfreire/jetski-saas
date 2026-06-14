import { apiClient, getTenantId } from '../client'
import type {
  Vendedor,
  VendedorCreateRequest,
  VendedorResumo,
  VendedorDetalhe,
  Comissao,
  BonusVendedor,
  PagamentoLoteRequest,
  PagamentoLoteResponse,
} from '../types'

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

  // New endpoints for commission management
  async listWithSummary(params?: { includeInactive?: boolean }): Promise<VendedorResumo[]> {
    const { data } = await apiClient.get<VendedorResumo[]>(`${getBasePath()}/resumo`, { params })
    return data
  },

  async getDetails(id: string): Promise<VendedorDetalhe> {
    const { data } = await apiClient.get<VendedorDetalhe>(`${getBasePath()}/${id}/detalhes`)
    return data
  },

  async listComissoes(vendedorId: string, params?: { status?: string }): Promise<Comissao[]> {
    const { data } = await apiClient.get<Comissao[]>(`${getBasePath()}/${vendedorId}/comissoes`, { params })
    return data
  },

  async pagarLote(vendedorId: string, request: PagamentoLoteRequest): Promise<PagamentoLoteResponse> {
    const { data } = await apiClient.post<PagamentoLoteResponse>(
      `${getBasePath()}/${vendedorId}/comissoes/pagar-lote`,
      request
    )
    return data
  },

  async listBonus(vendedorId: string): Promise<BonusVendedor[]> {
    const { data } = await apiClient.get<BonusVendedor[]>(`${getBasePath()}/${vendedorId}/bonus`)
    return data
  },
}
