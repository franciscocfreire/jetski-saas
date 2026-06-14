import { apiClient, getTenantId } from '../client'
import type { FechamentoDiarioResponse, FechamentoMensalResponse, DivergenciaResponse } from '../types'

const getBasePath = () => `/v1/tenants/${getTenantId()}/fechamentos`

export const fechamentosService = {
  // ============================================
  // Fechamento Diário
  // ============================================

  async consolidarDia(dtReferencia: string): Promise<FechamentoDiarioResponse> {
    const { data } = await apiClient.post<FechamentoDiarioResponse>(
      `${getBasePath()}/dia/consolidar`,
      { dtReferencia }
    )
    return data
  },

  async listarDiarios(dataInicio: string, dataFim: string): Promise<FechamentoDiarioResponse[]> {
    const { data } = await apiClient.get<FechamentoDiarioResponse[]>(
      `${getBasePath()}/dia`,
      { params: { dataInicio, dataFim } }
    )
    return data
  },

  async buscarDiarioPorId(id: string): Promise<FechamentoDiarioResponse> {
    const { data } = await apiClient.get<FechamentoDiarioResponse>(
      `${getBasePath()}/dia/${id}`
    )
    return data
  },

  async buscarDiarioPorData(data: string): Promise<FechamentoDiarioResponse> {
    const { data: response } = await apiClient.get<FechamentoDiarioResponse>(
      `${getBasePath()}/dia/data/${data}`
    )
    return response
  },

  async fecharDia(id: string): Promise<FechamentoDiarioResponse> {
    const { data } = await apiClient.post<FechamentoDiarioResponse>(
      `${getBasePath()}/dia/${id}/fechar`
    )
    return data
  },

  async aprovarDia(id: string): Promise<FechamentoDiarioResponse> {
    const { data } = await apiClient.post<FechamentoDiarioResponse>(
      `${getBasePath()}/dia/${id}/aprovar`
    )
    return data
  },

  async reabrirDia(id: string): Promise<FechamentoDiarioResponse> {
    const { data } = await apiClient.post<FechamentoDiarioResponse>(
      `${getBasePath()}/dia/${id}/reabrir`
    )
    return data
  },

  async forcarReabrirDia(id: string): Promise<FechamentoDiarioResponse> {
    const { data } = await apiClient.post<FechamentoDiarioResponse>(
      `${getBasePath()}/dia/${id}/forcar-reabrir`
    )
    return data
  },

  // Download de relatorios
  async downloadRelatorioDiario(id: string, formato: 'pdf' | 'excel'): Promise<Blob> {
    const { data } = await apiClient.get<Blob>(
      `${getBasePath()}/dia/${id}/relatorio`,
      {
        params: { formato },
        responseType: 'blob'
      }
    )
    return data
  },

  // Verificar divergencias entre valores consolidados e atuais
  async verificarDivergencias(dataInicio: string, dataFim: string): Promise<DivergenciaResponse[]> {
    const { data } = await apiClient.get<DivergenciaResponse[]>(
      `${getBasePath()}/dia/divergencias`,
      { params: { dataInicio, dataFim } }
    )
    return data
  },

  // ============================================
  // Fechamento Mensal
  // ============================================

  async consolidarMes(ano: number, mes: number): Promise<FechamentoMensalResponse> {
    const { data } = await apiClient.post<FechamentoMensalResponse>(
      `${getBasePath()}/mes/consolidar`,
      { ano, mes }
    )
    return data
  },

  async listarMensais(): Promise<FechamentoMensalResponse[]> {
    const { data } = await apiClient.get<FechamentoMensalResponse[]>(
      `${getBasePath()}/mes`
    )
    return data
  },

  async listarMensaisPorAno(ano: number): Promise<FechamentoMensalResponse[]> {
    const { data } = await apiClient.get<FechamentoMensalResponse[]>(
      `${getBasePath()}/mes/ano/${ano}`
    )
    return data
  },

  async buscarMensalPorId(id: string): Promise<FechamentoMensalResponse> {
    const { data } = await apiClient.get<FechamentoMensalResponse>(
      `${getBasePath()}/mes/${id}`
    )
    return data
  },

  async buscarMensalPorPeriodo(ano: number, mes: number): Promise<FechamentoMensalResponse> {
    const { data } = await apiClient.get<FechamentoMensalResponse>(
      `${getBasePath()}/mes/${ano}/${mes}`
    )
    return data
  },

  async fecharMes(id: string): Promise<FechamentoMensalResponse> {
    const { data } = await apiClient.post<FechamentoMensalResponse>(
      `${getBasePath()}/mes/${id}/fechar`
    )
    return data
  },

  async aprovarMes(id: string): Promise<FechamentoMensalResponse> {
    const { data } = await apiClient.post<FechamentoMensalResponse>(
      `${getBasePath()}/mes/${id}/aprovar`
    )
    return data
  },

  async reabrirMes(id: string): Promise<FechamentoMensalResponse> {
    const { data } = await apiClient.post<FechamentoMensalResponse>(
      `${getBasePath()}/mes/${id}/reabrir`
    )
    return data
  },

  async forcarReabrirMes(id: string): Promise<FechamentoMensalResponse> {
    const { data } = await apiClient.post<FechamentoMensalResponse>(
      `${getBasePath()}/mes/${id}/forcar-reabrir`
    )
    return data
  },

  // Download de relatórios
  async downloadRelatorioMensal(id: string, formato: 'pdf' | 'excel'): Promise<Blob> {
    const { data } = await apiClient.get<Blob>(
      `${getBasePath()}/mes/${id}/relatorio`,
      {
        params: { formato },
        responseType: 'blob'
      }
    )
    return data
  },
}
