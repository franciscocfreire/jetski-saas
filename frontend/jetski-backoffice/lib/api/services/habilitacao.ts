import { apiClient, getTenantId } from '../client'
import type {
  Habilitacao,
  HabilitacaoGruBoletoResponse,
  HabilitacaoGruResponse,
  HabilitacaoRequest,
} from '../types'

const path = (reservaId: string) =>
  `/v1/tenants/${getTenantId()}/reservas/${reservaId}/habilitacao`

export const habilitacaoService = {
  /** Registra/atualiza a habilitação do condutor (CHA ou EMA+GRU). */
  async registrar(reservaId: string, req: HabilitacaoRequest): Promise<Habilitacao> {
    const { data } = await apiClient.put<Habilitacao>(path(reservaId), req)
    return data
  },

  /** Habilitação atual da reserva (null se ainda não registrada). */
  async get(reservaId: string): Promise<Habilitacao | null> {
    try {
      const { data } = await apiClient.get<Habilitacao>(path(reservaId))
      return data
    } catch {
      return null
    }
  },

  /** Gera a GRU + PIX automaticamente (Marinha/PagTesouro). Fallback manual se sucesso=false. */
  async gerarGru(reservaId: string): Promise<HabilitacaoGruResponse> {
    const { data } = await apiClient.post<HabilitacaoGruResponse>(`${path(reservaId)}/gru`)
    return data
  },

  /** Gera o boleto da GRU (PDF). Fallback manual se sucesso=false. */
  async gerarBoleto(reservaId: string): Promise<HabilitacaoGruBoletoResponse> {
    const { data } = await apiClient.post<HabilitacaoGruBoletoResponse>(`${path(reservaId)}/gru/boleto`)
    return data
  },

  /** Baixa o PDF do boleto já gerado (stream autenticado via Bearer). */
  async baixarBoleto(reservaId: string): Promise<Blob> {
    const res = await apiClient.get(`${path(reservaId)}/gru/boleto/download`, { responseType: 'blob' })
    return res.data as Blob
  },
}
