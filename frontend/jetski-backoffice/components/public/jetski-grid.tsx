'use client'

import { useEffect, useState } from 'react'
import { JetskiCard, JetskiCardProps } from './jetski-card'
import { marketplaceService, MarketplaceModelo, getPrincipalImage } from '@/lib/api/services/marketplace'

// Lanchas fixas (por enquanto - dados estáticos)
const lanchasFixas: JetskiCardProps[] = [
  {
    id: 'lancha-1',
    modelo: 'Lancha Focker 265',
    tipo: 'LANCHA',
    empresa: 'Marina Premium',
    precoMeiaDiaria: 1800,
    precoDiaria: 3200,
    imagemUrl: 'https://images.unsplash.com/photo-1567899378494-47b22a2ae96a?w=800&h=600&fit=crop&q=80',
    localizacao: 'Jurerê, SC',
    avaliacao: 4.7,
    totalAvaliacoes: 56,
  },
  {
    id: 'lancha-2',
    modelo: 'Lancha NX 280',
    tipo: 'LANCHA',
    empresa: 'Náutica Elite',
    precoMeiaDiaria: 2200,
    precoDiaria: 3800,
    imagemUrl: 'https://images.unsplash.com/photo-1569263979104-865ab7cd8d13?w=800&h=600&fit=crop&q=80',
    localizacao: 'Itapema, SC',
    avaliacao: 4.8,
    totalAvaliacoes: 34,
  },
  {
    id: 'lancha-3',
    modelo: 'Lancha Phantom 303',
    tipo: 'LANCHA',
    empresa: 'Yacht Club Premium',
    precoMeiaDiaria: 3500,
    precoDiaria: 6000,
    imagemUrl: 'https://images.unsplash.com/photo-1567899378494-47b22a2ae96a?w=800&h=600&fit=crop&q=80',
    localizacao: 'Balneário Camboriú, SC',
    avaliacao: 4.9,
    totalAvaliacoes: 67,
  },
]

// Fallback jetskis estáticos (usados se a API não retornar dados)
const jetsikisFallback: JetskiCardProps[] = [
  {
    id: 'fallback-1',
    modelo: 'Sea-Doo GTI 130',
    tipo: 'JETSKI',
    empresa: 'Marina do Sol',
    precoHora: 250,
    precoPacote30min: 150,
    imagemUrl: 'https://sea-doo.brp.com/content/dam/global/en/sea-doo/my26/studio/recreation/gti/SEA-MY26-GTI-Standard-NoSS-M130-Bright-White-Neo-Mint-00038TB00-Studio-RSIDE-CU.png',
    localizacao: 'Florianópolis, SC',
    avaliacao: 4.8,
  },
  {
    id: 'fallback-2',
    modelo: 'Sea-Doo Spark 90',
    tipo: 'JETSKI',
    empresa: 'JetSki Praia',
    precoHora: 180,
    precoPacote30min: 100,
    imagemUrl: 'https://sea-doo.brp.com/content/dam/global/en/sea-doo/my26/studio/rec-lite/spark-trixx/SEA-MY26-SPARK-Trixx-1up-NoSS-M90-Gulfstream-Blue-Orange-Crush-00067TB00-Studio-RSIDE-CU.png',
    localizacao: 'Bombinhas, SC',
    avaliacao: 4.6,
  },
  {
    id: 'fallback-3',
    modelo: 'Sea-Doo RXT-X 325',
    tipo: 'JETSKI',
    empresa: 'Marina do Sol',
    precoHora: 380,
    precoPacote30min: 220,
    imagemUrl: 'https://sea-doo.brp.com/content/dam/global/en/sea-doo/my26/studio/performance/rxt-x/SEA-MY26-RXT-X-X-Integrated100W-M325-Gulfstream-Blue-Premium-00022TD00-Studio-RSIDE-CU.png',
    localizacao: 'Florianópolis, SC',
    avaliacao: 4.9,
  },
  {
    id: 'fallback-4',
    modelo: 'Sea-Doo GTX 170',
    tipo: 'JETSKI',
    empresa: 'Águas Claras',
    precoHora: 290,
    precoPacote30min: 170,
    imagemUrl: 'https://sea-doo.brp.com/content/dam/global/en/sea-doo/my26/studio/touring/gtx/SEA-MY26-GTX-Standard-Integrated100W-M170-Blue-Abyss-Gulfstream-Blue-00011TA00-Studio-RSIDE-CU.png',
    localizacao: 'Porto Belo, SC',
    avaliacao: 4.5,
  },
]

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

        if (modelos.length > 0) {
          const mapped = modelos.map(mapModeloToCard)
          setJetskis(mapped)
        } else {
          // Se não há dados da API, usar fallback
          setJetskis(jetsikisFallback)
        }
      } catch (err) {
        console.error('Erro ao carregar jetskis do marketplace:', err)
        setError('Erro ao carregar embarcações')
        // Em caso de erro, usar fallback
        setJetskis(jetsikisFallback)
      } finally {
        setLoading(false)
      }
    }

    fetchJetskis()
  }, [])

  // Combina jetskis (da API ou fallback) + lanchas fixas
  const allOfferings = [...jetskis, ...lanchasFixas]

  if (loading) {
    return <GridSkeleton />
  }

  return (
    <>
      {error && (
        <div className="mb-4 p-3 rounded-lg bg-yellow-500/10 border border-yellow-500/20 text-yellow-200 text-sm">
          {error} - Mostrando dados de demonstração
        </div>
      )}
      <div className="grid gap-6 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
        {allOfferings.map((offering) => (
          <JetskiCard key={offering.id} {...offering} />
        ))}
      </div>
    </>
  )
}
