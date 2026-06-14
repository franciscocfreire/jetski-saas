import { apiClient, getTenantId } from '../client'
import type {
  CalendarioFinanceiroResponse,
  ReceitaDespesaDia,
  DRESimplificado,
  RegistrosPendentes,
} from '../types'

const getBasePath = () => `/v1/tenants/${getTenantId()}/dashboard/financeiro`

export const dashboardFinanceiroService = {
  // ============================================
  // Financial Calendar
  // ============================================

  /**
   * Get monthly financial calendar data.
   * Returns daily data for all days of the specified month.
   */
  async getCalendario(ano: number, mes: number): Promise<CalendarioFinanceiroResponse> {
    const { data } = await apiClient.get<CalendarioFinanceiroResponse>(
      `${getBasePath()}/calendario/${ano}/${mes}`
    )
    return data
  },

  // ============================================
  // Revenue vs Expenses Chart
  // ============================================

  /**
   * Get revenue vs expenses chart data for a date range.
   */
  async getReceitasDespesas(dataInicio: string, dataFim: string): Promise<ReceitaDespesaDia[]> {
    const { data } = await apiClient.get<ReceitaDespesaDia[]>(
      `${getBasePath()}/receitas-despesas`,
      { params: { dataInicio, dataFim } }
    )
    return data
  },

  // ============================================
  // Simplified DRE (Income Statement)
  // ============================================

  /**
   * Get simplified DRE for a month.
   */
  async getDRE(ano: number, mes: number): Promise<DRESimplificado> {
    const { data } = await apiClient.get<DRESimplificado>(
      `${getBasePath()}/dre/${ano}/${mes}`
    )
    return data
  },

  // ============================================
  // Pending Records
  // ============================================

  /**
   * Get pending records that need attention.
   */
  async getPendentes(): Promise<RegistrosPendentes> {
    const { data } = await apiClient.get<RegistrosPendentes>(
      `${getBasePath()}/pendentes`
    )
    return data
  },
}
