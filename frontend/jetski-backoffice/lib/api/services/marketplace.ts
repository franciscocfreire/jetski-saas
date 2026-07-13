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
  /** Cidade/UF crus para os filtros em cascata (localizacao é a versão formatada). */
  cidade?: string
  uf?: string
  /** Praia/ponto de encontro da loja — usada na busca por praia. */
  praia?: string
  prioridade: number
  midias: MarketplaceMidia[]
}

/**
 * Public data of a loja (vitrine header) — /v1/public/lojas/{slug}
 */
export interface MarketplaceLoja {
  tenantId: string
  slug: string
  nome: string
  cidade?: string
  uf?: string
  whatsapp?: string
  vitrineDescricao?: string
  vitrineEndereco?: string
  vitrinePraia?: string
  vitrineHorario?: string
  vitrineInstagram?: string
  vitrineSite?: string
}

/** Branding público da loja (white-label): cores + logo como data URL. */
export interface MarketplaceLojaBranding {
  corPrimaria?: string | null
  corSecundaria?: string | null
  logoDataUrl?: string | null
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

  /**
   * Public loja data for the per-company vitrine ({slug}.meujet.com.br).
   * 404 = loja inexistente, inativa ou sem o módulo LOJA_ONLINE no plano.
   */
  async getLoja(slug: string): Promise<MarketplaceLoja> {
    const { data } = await publicClient.get<MarketplaceLoja>(`/v1/public/lojas/${slug}`)
    return data
  },

  /**
   * Visible models of a single loja (same visibility rules as the marketplace)
   */
  async listModelosByLoja(slug: string): Promise<MarketplaceModelo[]> {
    const { data } = await publicClient.get<MarketplaceModelo[]>(`/v1/public/lojas/${slug}/modelos`)
    return data
  },

  /** Branding público (logo/cores) — best-effort na vitrine. */
  async getLojaBranding(slug: string): Promise<MarketplaceLojaBranding> {
    const { data } = await publicClient.get<MarketplaceLojaBranding>(`/v1/public/lojas/${slug}/branding`)
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
