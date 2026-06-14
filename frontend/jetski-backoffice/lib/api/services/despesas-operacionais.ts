import { apiClient, getTenantId } from '../client'
import type {
  DespesaOperacional,
  DespesaOperacionalCreateRequest,
  DespesaOperacionalUpdateRequest,
  PagarDespesaRequest,
  CategoriaDespesa,
  StatusDespesa,
} from '../types'

const getBasePath = () => `/v1/tenants/${getTenantId()}/despesas-operacionais`

export const despesasOperacionaisService = {
  // ============================================
  // CRUD Operations
  // ============================================

  async criar(request: DespesaOperacionalCreateRequest): Promise<DespesaOperacional> {
    const { data } = await apiClient.post<DespesaOperacional>(
      getBasePath(),
      request
    )
    return data
  },

  async buscarPorId(id: string): Promise<DespesaOperacional> {
    const { data } = await apiClient.get<DespesaOperacional>(
      `${getBasePath()}/${id}`
    )
    return data
  },

  async atualizar(id: string, request: DespesaOperacionalUpdateRequest): Promise<DespesaOperacional> {
    const { data } = await apiClient.put<DespesaOperacional>(
      `${getBasePath()}/${id}`,
      request
    )
    return data
  },

  async excluir(id: string): Promise<void> {
    await apiClient.delete(`${getBasePath()}/${id}`)
  },

  // ============================================
  // Listing Operations
  // ============================================

  async listarPorPeriodo(dataInicio: string, dataFim: string): Promise<DespesaOperacional[]> {
    const { data } = await apiClient.get<DespesaOperacional[]>(
      getBasePath(),
      { params: { dataInicio, dataFim } }
    )
    return data
  },

  async listarPorDia(data: string): Promise<DespesaOperacional[]> {
    const { data: response } = await apiClient.get<DespesaOperacional[]>(
      `${getBasePath()}/dia/${data}`
    )
    return response
  },

  async listarPorCategoria(categoria: CategoriaDespesa): Promise<DespesaOperacional[]> {
    const { data } = await apiClient.get<DespesaOperacional[]>(
      `${getBasePath()}/categoria/${categoria}`
    )
    return data
  },

  async listarPorStatus(status: StatusDespesa): Promise<DespesaOperacional[]> {
    const { data } = await apiClient.get<DespesaOperacional[]>(
      `${getBasePath()}/status/${status}`
    )
    return data
  },

  async listarPendentesAprovacao(): Promise<DespesaOperacional[]> {
    const { data } = await apiClient.get<DespesaOperacional[]>(
      `${getBasePath()}/pendentes`
    )
    return data
  },

  async listarAguardandoPagamento(): Promise<DespesaOperacional[]> {
    const { data } = await apiClient.get<DespesaOperacional[]>(
      `${getBasePath()}/aguardando-pagamento`
    )
    return data
  },

  // ============================================
  // Workflow Operations
  // ============================================

  async aprovar(id: string): Promise<DespesaOperacional> {
    const { data } = await apiClient.post<DespesaOperacional>(
      `${getBasePath()}/${id}/aprovar`
    )
    return data
  },

  async rejeitar(id: string, observacoes?: string): Promise<DespesaOperacional> {
    const { data } = await apiClient.post<DespesaOperacional>(
      `${getBasePath()}/${id}/rejeitar`,
      null,
      { params: observacoes ? { observacoes } : undefined }
    )
    return data
  },

  async pagar(id: string, request?: PagarDespesaRequest): Promise<DespesaOperacional> {
    const { data } = await apiClient.post<DespesaOperacional>(
      `${getBasePath()}/${id}/pagar`,
      request || {}
    )
    return data
  },
}
