import { apiClient, getTenantId } from '../client'
import type { Modelo, ModeloMidia, ModeloMidiaCreateRequest } from '../types'

const getBasePath = () => `/v1/tenants/${getTenantId()}/modelos`

export interface ModeloCreateRequest {
  nome: string
  fabricante?: string
  potenciaHp?: number
  capacidadePessoas: number
  precoBaseHora: number
  toleranciaMin?: number
  taxaHoraExtra?: number
  incluiCombustivel?: boolean
  caucao?: number
  fotoReferenciaUrl?: string
  descricao?: string
  /** Minutos; 0 = sem mínimo. */
  duracaoMinimaMin?: number
  exibirNoMarketplace?: boolean
}

export const modelosService = {
  // Backend returns a simple list, not a paginated response
  async list(params?: { includeInactive?: boolean }): Promise<Modelo[]> {
    const { data } = await apiClient.get<Modelo[]>(getBasePath(), { params })
    return data
  },

  async getById(id: string): Promise<Modelo> {
    const { data } = await apiClient.get<Modelo>(`${getBasePath()}/${id}`)
    return data
  },

  async create(request: ModeloCreateRequest): Promise<Modelo> {
    const { data } = await apiClient.post<Modelo>(getBasePath(), request)
    return data
  },

  async update(id: string, request: Partial<ModeloCreateRequest>): Promise<Modelo> {
    const { data } = await apiClient.put<Modelo>(`${getBasePath()}/${id}`, request)
    return data
  },

  // Toggle marketplace visibility
  async toggleMarketplace(id: string, exibir: boolean): Promise<Modelo> {
    const { data } = await apiClient.patch<Modelo>(`${getBasePath()}/${id}`, { exibirNoMarketplace: exibir })
    return data
  },

  // Deactivate modelo
  async deactivate(id: string): Promise<Modelo> {
    const { data } = await apiClient.delete<Modelo>(`${getBasePath()}/${id}`)
    return data
  },

  // Midia (Photos/Videos) endpoints
  midia: {
    async list(modeloId: string): Promise<ModeloMidia[]> {
      const { data } = await apiClient.get<ModeloMidia[]>(`${getBasePath()}/${modeloId}/midias`)
      return data
    },

    async add(modeloId: string, request: ModeloMidiaCreateRequest): Promise<ModeloMidia> {
      const { data } = await apiClient.post<ModeloMidia>(`${getBasePath()}/${modeloId}/midias`, request)
      return data
    },

    /** Envia uma imagem (arquivo já comprimido) — JPG, PNG ou WebP de até 5 MB. */
    async upload(
      modeloId: string,
      arquivo: File,
      opcoes: { titulo?: string; principal?: boolean } = {}
    ): Promise<ModeloMidia> {
      const form = new FormData()
      form.append('arquivo', arquivo)
      if (opcoes.titulo) form.append('titulo', opcoes.titulo)
      form.append('principal', String(!!opcoes.principal))
      const { data } = await apiClient.post<ModeloMidia>(`${getBasePath()}/${modeloId}/midias/upload`, form, {
        headers: { 'Content-Type': 'multipart/form-data' },
      })
      return data
    },

    async update(modeloId: string, midiaId: string, request: Partial<ModeloMidiaCreateRequest>): Promise<ModeloMidia> {
      const { data } = await apiClient.put<ModeloMidia>(`${getBasePath()}/${modeloId}/midias/${midiaId}`, request)
      return data
    },

    async delete(modeloId: string, midiaId: string): Promise<void> {
      await apiClient.delete(`${getBasePath()}/${modeloId}/midias/${midiaId}`)
    },

    async setPrincipal(modeloId: string, midiaId: string): Promise<ModeloMidia> {
      const { data } = await apiClient.post<ModeloMidia>(`${getBasePath()}/${modeloId}/midias/${midiaId}/principal`)
      return data
    },

    async reorder(modeloId: string, orderedIds: string[]): Promise<ModeloMidia[]> {
      const { data } = await apiClient.put<ModeloMidia[]>(`${getBasePath()}/${modeloId}/midias/reorder`, orderedIds)
      return data
    },
  },
}
