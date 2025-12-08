import axios from 'axios'

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8090/api'

/**
 * Public API client - no authentication required
 * Used for marketplace endpoints
 */
const publicClient = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
})

/**
 * Media item for marketplace display (image or video)
 */
export interface MarketplaceMidia {
  id: string
  tipo: 'IMAGEM' | 'VIDEO'
  url: string
  thumbnailUrl?: string
  ordem: number
  principal: boolean
  titulo?: string
}

/**
 * DTO for marketplace model display
 */
export interface MarketplaceModelo {
  id: string
  nome: string
  fabricante?: string
  capacidadePessoas: number
  precoBaseHora: number
  precoPacote30min?: number
  fotoReferenciaUrl?: string
  empresaNome: string
  empresaWhatsapp?: string
  localizacao: string
  prioridade: number
  midias: MarketplaceMidia[]
}

/**
 * Marketplace service - public API calls
 * No authentication required
 */
export const marketplaceService = {
  /**
   * List all models available in the public marketplace
   * @returns List of models from all tenants with marketplace enabled
   */
  async listModelos(): Promise<MarketplaceModelo[]> {
    const { data } = await publicClient.get<MarketplaceModelo[]>('/v1/public/marketplace/modelos')
    return data
  },

  /**
   * Get a specific model from the marketplace
   * @param id Model UUID
   * @returns Model details or throws 404
   */
  async getModelo(id: string): Promise<MarketplaceModelo> {
    const { data } = await publicClient.get<MarketplaceModelo>(`/v1/public/marketplace/modelos/${id}`)
    return data
  },
}

/**
 * Helper to get the principal image from midias list
 * Falls back to fotoReferenciaUrl if no midias
 */
export function getPrincipalImage(modelo: MarketplaceModelo): string | undefined {
  const principal = modelo.midias?.find(m => m.principal && m.tipo === 'IMAGEM')
  if (principal) return principal.url

  const firstImage = modelo.midias?.find(m => m.tipo === 'IMAGEM')
  if (firstImage) return firstImage.url

  return modelo.fotoReferenciaUrl
}

/**
 * Helper to get all images from midias list
 */
export function getImages(modelo: MarketplaceModelo): MarketplaceMidia[] {
  return modelo.midias?.filter(m => m.tipo === 'IMAGEM') || []
}

/**
 * Helper to get all videos from midias list
 */
export function getVideos(modelo: MarketplaceModelo): MarketplaceMidia[] {
  return modelo.midias?.filter(m => m.tipo === 'VIDEO') || []
}
