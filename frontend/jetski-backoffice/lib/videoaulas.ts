import type { VideoaulaIdioma } from '@/lib/api/types'

/**
 * Videoaulas oficiais da Marinha do Brasil (CHA-MTA-E / NORMAM-212) — fonte única
 * dos ids (player do balcão, links de fallback no step Termos).
 */
export type Videoaula = { id: string; idioma: VideoaulaIdioma; rotulo: string }

export const VIDEOAULAS: readonly Videoaula[] = [
  { id: 'Tjoj0eb-yj8', idioma: 'pt', rotulo: 'Português' },
  { id: 'W3rextGEmKM', idioma: 'en', rotulo: 'English' },
  { id: 'xbYgwNqBpys', idioma: 'es', rotulo: 'Español' },
] as const

export const videoaulaUrl = (v: Videoaula) => `https://youtu.be/${v.id}`

export const rotuloIdioma = (idioma?: VideoaulaIdioma | null) =>
  VIDEOAULAS.find((v) => v.idioma === idioma)?.rotulo ?? idioma?.toUpperCase() ?? ''
