import { apiClient, getTenantId } from '../client'

/** Fatura mensal da assinatura (billing manual assistido). */
export interface Fatura {
  id: string
  competencia: string
  planoNome: string
  valor: number
  status: 'ABERTA' | 'EM_CONFERENCIA' | 'PAGA' | 'CANCELADA'
  vencimento: string
  pixCopiaECola?: string
  txidInformado?: string
  pagoEm?: string
  observacao?: string
}

export interface PlanoEFaturas {
  plano: { plano: string; precoMensal: number; limites: string }
  uso: { jetskisAtivos: number; locacoesMes: number; usuariosAtivos: number }
  faturas: Fatura[]
}

const basePath = () => `/v1/tenants/${getTenantId()}/faturas`

export const faturasService = {
  /** Plano atual + uso × limites + faturas (página Plano e Faturas). */
  async minhas(): Promise<PlanoEFaturas> {
    const { data } = await apiClient.get<PlanoEFaturas>(basePath())
    return data
  },

  /** Informa o pagamento (txid do PIX) → EM_CONFERENCIA. */
  async informarPagamento(faturaId: string, txid: string): Promise<Fatura> {
    const { data } = await apiClient.post<Fatura>(
      `${basePath()}/${faturaId}/informar-pagamento`,
      { txid }
    )
    return data
  },
}
