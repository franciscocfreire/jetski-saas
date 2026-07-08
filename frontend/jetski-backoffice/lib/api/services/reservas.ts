import { apiClient, getTenantId } from '../client'
import type {
  Reserva,
  ReservaCreateRequest,
  ReservaStatus,
  ConfirmarPagamentoRequest,
  RegistrarPagamentoReservaRequest,
  RegistrarEstornoRequest,
  FolioExtrato,
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

  /** Reservas do dia (ou período com `ate`) com prontidão — grade/semana. */
  async agendaDoDia(data: string, ate?: string): Promise<import('../types').AgendaReserva[]> {
    const { data: rows } = await apiClient.get<import('../types').AgendaReserva[]>(
      `${getBasePath()}/agenda`, { params: { data, ate } })
    return rows
  },

  /** Busca do módulo Reservas: filtros server-side, nomes resolvidos, top 200. */
  async buscar(params?: {
    status?: string
    canal?: 'BALCAO' | 'PORTAL'
    clienteId?: string
    de?: string
    ate?: string
  }): Promise<import('../types').ReservaBusca[]> {
    const { data } = await apiClient.get<import('../types').ReservaBusca[]>(
      `${getBasePath()}/busca`, { params })
    return data
  },

  /** Ficha completa (página de detalhe): cliente, extrato, habilitação, aceite, docs. */
  async ficha(id: string): Promise<import('../types').ReservaFicha> {
    const { data } = await apiClient.get<import('../types').ReservaFicha>(
      `${getBasePath()}/${id}/ficha`)
    return data
  },

  /** Link temporário (uso único) do PDF da ficha — padrão iOS-safe. */
  async fichaDownloadLink(id: string): Promise<{ url: string }> {
    const { data } = await apiClient.get<{ url: string }>(
      `${getBasePath()}/${id}/ficha/download-link`)
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

  /**
   * Registra o pagamento presencial INTEGRAL do balcão (dinheiro/PIX/cartão):
   * confirma o pagamento e grava o lançamento financeiro da reserva.
   */
  async registrarPagamento(id: string, req: RegistrarPagamentoReservaRequest): Promise<Reserva> {
    const { data } = await apiClient.post<Reserva>(
      `${getBasePath()}/${id}/registrar-pagamento`,
      req
    )
    return data
  },

  /** Marca não comparecimento (NO_SHOW) — reserva viva com início já passado. */
  async marcarNoShow(id: string): Promise<Reserva> {
    const { data } = await apiClient.post<Reserva>(`${getBasePath()}/${id}/no-show`)
    return data
  },

  /** Registra estorno (devolução) de reserva paga — GERENTE/FINANCEIRO/ADMIN. */
  async registrarEstorno(id: string, req: RegistrarEstornoRequest): Promise<Reserva> {
    const { data } = await apiClient.post<Reserva>(`${getBasePath()}/${id}/registrar-estorno`, req)
    return data
  },

  /** Extrato financeiro (folio) da reserva. */
  async extrato(id: string): Promise<FolioExtrato> {
    const { data } = await apiClient.get<FolioExtrato>(`${getBasePath()}/${id}/extrato`)
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
