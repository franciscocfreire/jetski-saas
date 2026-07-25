import { Suspense } from 'react'
import { ResgateCliente } from './resgate-cliente'

/**
 * Chegada do handoff console → backoffice.
 *
 * Server component só para o limite de Suspense: o resgate lê `?codigo=` com
 * useSearchParams, que no Next 15 exige Suspense para não quebrar o prerender.
 */
export default function ResgatarSuporte() {
  return (
    <Suspense fallback={null}>
      <ResgateCliente />
    </Suspense>
  )
}
