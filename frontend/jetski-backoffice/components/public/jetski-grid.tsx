'use client'

import { useEffect, useState } from 'react'
import { JetskiCard, JetskiCardProps } from './jetski-card'
import { marketplaceService, MarketplaceModelo, getPrincipalImage } from '@/lib/api/services/marketplace'

/**
 * Map API model to card props
 */
function mapModeloToCard(modelo: MarketplaceModelo): JetskiCardProps {
  return {
    id: modelo.id,
    modelo: modelo.nome,
    tipo: 'JETSKI',
    empresa: modelo.empresaNome,
    precoHora: modelo.precoBaseHora,
    precoPacote30min: modelo.precoPacote30min,
    imagemUrl: getPrincipalImage(modelo),
    localizacao: modelo.localizacao,
    // Avaliação virá em versões futuras
  }
}

/**
 * Loading skeleton for grid
 */
function GridSkeleton() {
  return (
    <div className="grid gap-6 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
      {[...Array(8)].map((_, i) => (
        <div key={i} className="animate-pulse">
          <div className="aspect-[4/3] rounded-2xl bg-white/[0.05]" />
          <div className="p-5 space-y-3">
            <div className="h-5 bg-white/[0.05] rounded w-3/4" />
            <div className="h-4 bg-white/[0.05] rounded w-1/2" />
            <div className="h-px bg-white/10 my-4" />
            <div className="h-4 bg-white/[0.05] rounded w-2/3" />
          </div>
        </div>
      ))}
    </div>
  )
}

export function JetskiGrid() {
  const [jetskis, setJetskis] = useState<JetskiCardProps[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    async function fetchJetskis() {
      try {
        const modelos = await marketplaceService.listModelos()
        setJetskis(modelos.map(mapModeloToCard))
      } catch (err) {
        console.error('Erro ao carregar embarcações do marketplace:', err)
        setError('Não foi possível carregar as embarcações agora. Tente novamente em instantes.')
      } finally {
        setLoading(false)
      }
    }

    fetchJetskis()
  }, [])

  if (loading) {
    return <GridSkeleton />
  }

  if (error) {
    return (
      <div className="rounded-lg border border-yellow-500/20 bg-yellow-500/10 p-6 text-center text-sm text-yellow-200">
        {error}
      </div>
    )
  }

  // Só embarcações reais publicadas pelas locadoras — sem dados de demonstração.
  if (jetskis.length === 0) {
    return (
      <div className="rounded-lg border border-white/10 bg-white/[0.03] p-10 text-center text-white/50">
        Nenhuma embarcação publicada no momento — as locadoras parceiras estão preparando a vitrine.
      </div>
    )
  }

  return (
    <>
      <div className="grid gap-6 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
        {jetskis.map((offering) => (
          <JetskiCard key={offering.id} {...offering} />
        ))}
      </div>
      {/* Contagem real — sem números inventados */}
      <p className="mt-16 text-center text-white/30 text-sm">
        Exibindo {jetskis.length} embarcaç{jetskis.length === 1 ? 'ão' : 'ões'} de locadoras parceiras
      </p>
    </>
  )
}
