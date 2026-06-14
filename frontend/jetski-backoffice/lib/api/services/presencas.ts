import { apiClient } from '../client'
import type {
  RegistrarPresencasRequest,
  ResumoDiariasResponse,
  PresencaVendedorResponse,
  PresencaVendedorRequest,
  Vendedor,
} from '../types'

const BASE_PATH = (tenantId: string) => `/v1/tenants/${tenantId}/presencas`

/**
 * Service para gerenciamento de presenças de vendedores (diárias)
 */
export const presencasService = {
  /**
   * Registrar presenças do dia (batch)
   */
  async registrarPresencas(
    tenantId: string,
    request: RegistrarPresencasRequest
  ): Promise<ResumoDiariasResponse> {
    const { data } = await apiClient.post<ResumoDiariasResponse>(
      `${BASE_PATH(tenantId)}/dia`,
      request
    )
    return data
  },

  /**
   * Buscar resumo de diárias de um dia
   */
  async getResumoDiarias(
    tenantId: string,
    dtReferencia: string
  ): Promise<ResumoDiariasResponse> {
    const { data } = await apiClient.get<ResumoDiariasResponse>(
      `${BASE_PATH(tenantId)}/dia/${dtReferencia}`
    )
    return data
  },

  /**
   * Listar vendedores ativos para registro de presença
   */
  async getVendedoresParaPresenca(tenantId: string): Promise<Vendedor[]> {
    const { data } = await apiClient.get<Vendedor[]>(
      `${BASE_PATH(tenantId)}/vendedores`
    )
    return data
  },

  /**
   * Buscar histórico de presenças de um vendedor
   */
  async getPresencasByVendedor(
    tenantId: string,
    vendedorId: string,
    dtInicio: string,
    dtFim: string
  ): Promise<PresencaVendedorResponse[]> {
    const { data } = await apiClient.get<PresencaVendedorResponse[]>(
      `${BASE_PATH(tenantId)}/vendedor/${vendedorId}`,
      {
        params: { dtInicio, dtFim },
      }
    )
    return data
  },

  /**
   * Atualizar uma presença específica
   */
  async updatePresenca(
    tenantId: string,
    presencaId: string,
    request: PresencaVendedorRequest
  ): Promise<PresencaVendedorResponse> {
    const { data } = await apiClient.put<PresencaVendedorResponse>(
      `${BASE_PATH(tenantId)}/${presencaId}`,
      request
    )
    return data
  },

  /**
   * Excluir uma presença específica
   */
  async deletePresenca(tenantId: string, presencaId: string): Promise<void> {
    await apiClient.delete(`${BASE_PATH(tenantId)}/${presencaId}`)
  },
}

export default presencasService
