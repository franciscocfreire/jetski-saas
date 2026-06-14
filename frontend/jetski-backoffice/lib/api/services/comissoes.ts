import { apiClient } from '../client'
import type { Comissao } from '../types'

export const comissoesService = {
  /**
   * Lista comissões pendentes de aprovação
   */
  async listPendentes(tenantId: string): Promise<Comissao[]> {
    const response = await apiClient.get<Comissao[]>(`/v1/tenants/${tenantId}/comissoes/pendentes`)
    return response.data
  },

  /**
   * Lista comissões aprovadas aguardando pagamento
   */
  async listAguardandoPagamento(tenantId: string): Promise<Comissao[]> {
    const response = await apiClient.get<Comissao[]>(`/v1/tenants/${tenantId}/comissoes/aguardando-pagamento`)
    return response.data
  },

  /**
   * Lista comissões por período
   */
  async listPorPeriodo(tenantId: string, inicio: string, fim: string): Promise<Comissao[]> {
    const response = await apiClient.get<Comissao[]>(`/v1/tenants/${tenantId}/comissoes/periodo`, {
      params: { inicio, fim }
    })
    return response.data
  },

  /**
   * Lista comissões de um vendedor específico
   */
  async listPorVendedor(tenantId: string, vendedorId: string): Promise<Comissao[]> {
    const response = await apiClient.get<Comissao[]>(`/v1/tenants/${tenantId}/comissoes/vendedor/${vendedorId}`)
    return response.data
  },

  /**
   * Busca uma comissão por ID
   */
  async getById(tenantId: string, comissaoId: string): Promise<Comissao> {
    const response = await apiClient.get<Comissao>(`/v1/tenants/${tenantId}/comissoes/${comissaoId}`)
    return response.data
  },

  /**
   * Aprova uma comissão (muda status de PENDENTE para APROVADA)
   */
  async aprovar(tenantId: string, comissaoId: string): Promise<Comissao> {
    const response = await apiClient.post<Comissao>(`/v1/tenants/${tenantId}/comissoes/${comissaoId}/aprovar`, {})
    return response.data
  },

  /**
   * Aprova múltiplas comissões em lote
   */
  async aprovarLote(tenantId: string, comissaoIds: string[]): Promise<Comissao[]> {
    const results: Comissao[] = []
    for (const id of comissaoIds) {
      const result = await comissoesService.aprovar(tenantId, id)
      results.push(result)
    }
    return results
  },

  /**
   * Marca uma comissão como paga
   */
  async pagar(tenantId: string, comissaoId: string, referenciaPagamento: string): Promise<Comissao> {
    const response = await apiClient.post<Comissao>(`/v1/tenants/${tenantId}/comissoes/${comissaoId}/pagar`, {
      referenciaPagamento
    })
    return response.data
  },
}
