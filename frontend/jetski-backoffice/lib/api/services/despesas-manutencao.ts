import { apiClient, getTenantId } from '../client'
import type {
  DespesaManutencao,
  GerarDespesaManutencaoRequest,
  PagarDespesaManutencaoRequest,
  RejeitarDespesaRequest,
  StatusDespesaManutencao,
} from '../types'

const getBasePath = () => `/v1/tenants/${getTenantId()}`

export const despesasManutencaoService = {
  // Listar despesas por periodo
  async list(params: {
    dataInicio: string
    dataFim: string
  }): Promise<DespesaManutencao[]> {
    const { data } = await apiClient.get<DespesaManutencao[]>(
      `${getBasePath()}/despesas-manutencao`,
      { params }
    )
    return data
  },

  // Buscar por ID
  async getById(id: string): Promise<DespesaManutencao> {
    const { data } = await apiClient.get<DespesaManutencao>(
      `${getBasePath()}/despesas-manutencao/${id}`
    )
    return data
  },

  // Listar despesas de uma OS especifica
  async listByOS(osId: string): Promise<DespesaManutencao[]> {
    const { data } = await apiClient.get<DespesaManutencao[]>(
      `${getBasePath()}/despesas-manutencao/os/${osId}`
    )
    return data
  },

  // Listar por status
  async listByStatus(status: StatusDespesaManutencao): Promise<DespesaManutencao[]> {
    const { data } = await apiClient.get<DespesaManutencao[]>(
      `${getBasePath()}/despesas-manutencao/status/${status}`
    )
    return data
  },

  // Listar pendentes de aprovacao
  async listPendentes(): Promise<DespesaManutencao[]> {
    const { data } = await apiClient.get<DespesaManutencao[]>(
      `${getBasePath()}/despesas-manutencao/pendentes`
    )
    return data
  },

  // Listar aguardando pagamento
  async listAguardandoPagamento(): Promise<DespesaManutencao[]> {
    const { data } = await apiClient.get<DespesaManutencao[]>(
      `${getBasePath()}/despesas-manutencao/aguardando-pagamento`
    )
    return data
  },

  // Verificar se ja existem despesas para uma OS
  async temDespesa(osId: string): Promise<boolean> {
    const { data } = await apiClient.get<boolean>(
      `${getBasePath()}/manutencoes/${osId}/tem-despesa`
    )
    return data
  },

  // Gerar despesas parceladas para uma OS
  async gerarDespesas(
    osId: string,
    request: GerarDespesaManutencaoRequest
  ): Promise<DespesaManutencao[]> {
    const { data } = await apiClient.post<DespesaManutencao[]>(
      `${getBasePath()}/manutencoes/${osId}/gerar-despesa`,
      request
    )
    return data
  },

  // Aprovar despesa
  async aprovar(id: string): Promise<DespesaManutencao> {
    const { data } = await apiClient.post<DespesaManutencao>(
      `${getBasePath()}/despesas-manutencao/${id}/aprovar`
    )
    return data
  },

  // Rejeitar despesa
  async rejeitar(id: string, motivo?: string): Promise<DespesaManutencao> {
    const request: RejeitarDespesaRequest = { motivo }
    const { data } = await apiClient.post<DespesaManutencao>(
      `${getBasePath()}/despesas-manutencao/${id}/rejeitar`,
      request
    )
    return data
  },

  // Marcar como paga
  async pagar(id: string, referenciaPagamento?: string): Promise<DespesaManutencao> {
    const request: PagarDespesaManutencaoRequest = { referenciaPagamento }
    const { data } = await apiClient.post<DespesaManutencao>(
      `${getBasePath()}/despesas-manutencao/${id}/pagar`,
      request
    )
    return data
  },

  // Cancelar despesa
  async cancelar(id: string, motivo?: string): Promise<DespesaManutencao> {
    const request: RejeitarDespesaRequest = { motivo }
    const { data } = await apiClient.post<DespesaManutencao>(
      `${getBasePath()}/despesas-manutencao/${id}/cancelar`,
      request
    )
    return data
  },
}
