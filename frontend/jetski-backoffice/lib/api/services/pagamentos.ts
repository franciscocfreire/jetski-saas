import { apiClient } from '../client'
import type {
  PendenciasPagamento,
  DetalhesPendencias,
  RegistrarPagamentoRequest,
  PagamentoVendedor,
} from '../types'

/**
 * Service for seller payments management
 */
export const pagamentosService = {
  /**
   * List all sellers with pending payments (approved commissions + unpaid diárias)
   */
  async listPendencias(tenantId: string): Promise<PendenciasPagamento[]> {
    const response = await apiClient.get<PendenciasPagamento[]>(
      `/v1/tenants/${tenantId}/pagamentos/pendencias`
    )
    return response.data
  },

  /**
   * Get pending payments for a specific seller
   */
  async getPendenciasVendedor(
    tenantId: string,
    vendedorId: string
  ): Promise<PendenciasPagamento> {
    const response = await apiClient.get<PendenciasPagamento>(
      `/v1/tenants/${tenantId}/pagamentos/pendencias/${vendedorId}`
    )
    return response.data
  },

  /**
   * Get detailed pending items for partial payment selection
   */
  async getDetalhesPendencias(
    tenantId: string,
    vendedorId: string
  ): Promise<DetalhesPendencias> {
    const response = await apiClient.get<DetalhesPendencias>(
      `/v1/tenants/${tenantId}/pagamentos/pendencias/${vendedorId}/detalhes`
    )
    return response.data
  },

  /**
   * Register a bulk payment for a seller
   * Marks all approved commissions as PAGA and all unpaid diárias as paid
   */
  async registrarPagamento(
    tenantId: string,
    vendedorId: string,
    request: RegistrarPagamentoRequest
  ): Promise<PagamentoVendedor> {
    const response = await apiClient.post<PagamentoVendedor>(
      `/v1/tenants/${tenantId}/pagamentos/vendedores/${vendedorId}/pagar`,
      request
    )
    return response.data
  },

  /**
   * List payment history for all sellers
   */
  async listHistorico(tenantId: string): Promise<PagamentoVendedor[]> {
    const response = await apiClient.get<PagamentoVendedor[]>(
      `/v1/tenants/${tenantId}/pagamentos/historico`
    )
    return response.data
  },

  /**
   * List payment history for a specific seller
   */
  async listHistoricoVendedor(
    tenantId: string,
    vendedorId: string
  ): Promise<PagamentoVendedor[]> {
    const response = await apiClient.get<PagamentoVendedor[]>(
      `/v1/tenants/${tenantId}/pagamentos/vendedores/${vendedorId}/historico`
    )
    return response.data
  },
}

export default pagamentosService
