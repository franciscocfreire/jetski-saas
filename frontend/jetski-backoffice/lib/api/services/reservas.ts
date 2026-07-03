import { apiClient, getTenantId } from '../client'
import type {
  Reserva,
  ReservaCreateRequest,
  ReservaStatus,
  ConfirmarPagamentoRequest,
  ResultadoEmissao,
  PagamentoPendente,
  ReservaComprovante,
} from '../types'

const getBasePath = () => `/v1/tenants/${getTenantId()}/reservas`

export const reservasService = {
  // Backend returns a simple list, not a paginated response
  /** Fila de validação: reservas com comprovante enviado (EM_ANALISE). */
  async pagamentosPendentes(): Promise<PagamentoPendente[]> {
    const { data } = await apiClient.get<PagamentoPendente[]>(`${getBasePath()}/pagamentos-pendentes`)
    return data
  },

  /** Comprovantes anexados à reserva (URL de download temporária). */
  async comprovantes(id: string): Promise<ReservaComprovante[]> {
    const { data } = await apiClient.get<ReservaComprovante[]>(`${getBasePath()}/${id}/comprovantes`)
    return data
  },

  async list(params?: {
    status?: ReservaStatus
    dataInicio?: string
    dataFim?: string
  }): Promise<Reserva[]> {
    const { data } = await apiClient.get<Reserva[]>(getBasePath(), { params })
    return data
  },

  async getById(id: string): Promise<Reserva> {
    const { data } = await apiClient.get<Reserva>(`${getBasePath()}/${id}`)
    return data
  },

  async create(request: ReservaCreateRequest): Promise<Reserva> {
    const { data } = await apiClient.post<Reserva>(getBasePath(), request)
    return data
  },

  /** Cria a reserva como RASCUNHO (balcão): sem cobrança, não bloqueia jetski. */
  async criarRascunho(request: ReservaCreateRequest): Promise<Reserva> {
    const { data } = await apiClient.post<Reserva>(`${getBasePath()}/rascunho`, request)
    return data
  },

  /** Atualiza a reserva (modelo/duração só enquanto RASCUNHO). */
  async atualizar(
    id: string,
    request: { modeloId?: string; dataInicio?: string; dataFimPrevista?: string; observacoes?: string }
  ): Promise<Reserva> {
    const { data } = await apiClient.put<Reserva>(`${getBasePath()}/${id}`, request)
    return data
  },

  async confirmar(id: string): Promise<Reserva> {
    const { data } = await apiClient.post<Reserva>(`${getBasePath()}/${id}/confirmar`)
    return data
  },

  /** Cancela a reserva (soft-cancel → CANCELADA). Permite RASCUNHO/PENDENTE/CONFIRMADA. */
  async cancelar(id: string): Promise<Reserva> {
    const { data } = await apiClient.delete<Reserva>(`${getBasePath()}/${id}`)
    return data
  },

  async alocarJetski(id: string, jetskiId: string): Promise<Reserva> {
    const { data } = await apiClient.post<Reserva>(`${getBasePath()}/${id}/alocar-jetski`, {
      jetskiId,
    })
    return data
  },

  /**
   * Confirma/valida o pagamento (sinal ou total). Backend espera `valorSinal`
   * como valor pago (independe do tipo). Endpoint: POST /{id}/confirmar-sinal.
   */
  async confirmarPagamento(id: string, req: ConfirmarPagamentoRequest): Promise<Reserva> {
    const { data } = await apiClient.post<Reserva>(`${getBasePath()}/${id}/confirmar-sinal`, {
      tipo: req.tipo,
      valorSinal: req.valorPago,
    })
    return data
  },

  /** Recusa o pagamento (comprovante inválido), com motivo obrigatório. */
  async recusarPagamento(id: string, motivo: string): Promise<Reserva> {
    const { data } = await apiClient.post<Reserva>(`${getBasePath()}/${id}/recusar-pagamento`, {
      motivo,
    })
    return data
  },

  /** Emite os documentos consolidados (PDF + Marinha + cliente). */
  async emitirDocumentos(id: string): Promise<ResultadoEmissao> {
    const { data } = await apiClient.post<ResultadoEmissao>(
      `${getBasePath()}/${id}/emitir-documentos`
    )
    return data
  },

  /**
   * Prévia (sem enviar nem persistir) do PDF que um destino receberá, respeitando
   * a parametrização do tenant. Carimba RASCUNHO enquanto houver pendências.
   */
  async previewDocumento(id: string, destino: 'MARINHA' | 'CLIENTE'): Promise<Blob> {
    const res = await apiClient.get(`${getBasePath()}/${id}/emitir-documentos/preview`, {
      params: { destino },
      responseType: 'blob',
    })
    return res.data as Blob
  },

  /** Link temporário (uso único) p/ abrir a prévia por URL — compatível com iOS. */
  async previewDocumentoLink(id: string, destino: 'MARINHA' | 'CLIENTE'): Promise<{ url: string }> {
    const { data } = await apiClient.get<{ url: string }>(
      `${getBasePath()}/${id}/emitir-documentos/preview-link`,
      { params: { destino } }
    )
    return data
  },
}
