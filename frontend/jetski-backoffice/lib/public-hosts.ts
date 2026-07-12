/**
 * Base absoluta de um subdomínio no mesmo domínio raiz da página atual
 * (meujet/pegaojet/jetsave). Fora desses domínios (ex.: localhost) retorna ''
 * — o chamador vira link relativo, aceitável em dev direto no container.
 *
 * Usado pelas superfícies públicas para mandar o visitante à superfície
 * certa: reserva/conta → cliente.*, institucional → www.
 */
export function subBase(sub: string): string {
  if (typeof window === 'undefined') return ''
  const m = /\.((?:meujet|pegaojet|jetsave)\.com\.br)$/.exec(window.location.hostname)
  return m ? `https://${sub}.${m[1]}` : ''
}
