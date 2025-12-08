import { apiClient, getTenantId } from '../client'
import type { Auditoria, AuditoriaFilters, Page } from '../types'

const getBasePath = () => `/v1/tenants/${getTenantId()}/auditoria`

export interface AuditoriaListParams extends AuditoriaFilters {
  page?: number
  size?: number
}

export const auditoriaService = {
  /**
   * List audit entries with filters and pagination
   */
  async list(params?: AuditoriaListParams): Promise<Page<Auditoria>> {
    const queryParams: Record<string, string> = {}

    if (params?.acao) queryParams.acao = params.acao
    if (params?.entidade) queryParams.entidade = params.entidade
    if (params?.entidadeId) queryParams.entidadeId = params.entidadeId
    if (params?.usuarioId) queryParams.usuarioId = params.usuarioId
    if (params?.dataInicio) queryParams.dataInicio = params.dataInicio
    if (params?.dataFim) queryParams.dataFim = params.dataFim
    if (params?.page !== undefined) queryParams.page = params.page.toString()
    if (params?.size !== undefined) queryParams.size = params.size.toString()

    const { data } = await apiClient.get<Page<Auditoria>>(getBasePath(), { params: queryParams })
    return data
  },

  /**
   * Get single audit entry by ID
   */
  async getById(id: string): Promise<Auditoria> {
    const { data } = await apiClient.get<Auditoria>(`${getBasePath()}/${id}`)
    return data
  },

  /**
   * Export audit entries to CSV
   * Returns a Blob that can be downloaded
   */
  async exportCsv(params?: AuditoriaFilters): Promise<Blob> {
    const queryParams: Record<string, string> = {}

    if (params?.acao) queryParams.acao = params.acao
    if (params?.entidade) queryParams.entidade = params.entidade
    if (params?.dataInicio) queryParams.dataInicio = params.dataInicio
    if (params?.dataFim) queryParams.dataFim = params.dataFim

    const { data } = await apiClient.get(`${getBasePath()}/export`, {
      params: queryParams,
      responseType: 'blob'
    })
    return data
  },

  /**
   * Download CSV file (helper that triggers browser download)
   */
  async downloadCsv(params?: AuditoriaFilters): Promise<void> {
    const blob = await this.exportCsv(params)
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `auditoria_${new Date().toISOString().split('T')[0]}.csv`
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(url)
  },

  /**
   * Get audit history for a specific entity (e.g., locacao)
   */
  async getHistoryByEntity(entidade: string, entidadeId: string): Promise<Page<Auditoria>> {
    return this.list({ entidade, entidadeId, size: 50 })
  }
}
