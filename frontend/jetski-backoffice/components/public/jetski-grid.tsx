'use client'

import { useEffect, useMemo, useState } from 'react'
import { Search, X } from 'lucide-react'
import { JetskiCard, JetskiCardProps } from './jetski-card'
import { marketplaceService, MarketplaceModelo, getPrincipalImage } from '@/lib/api/services/marketplace'

/**
 * Map API model to card props
 */
type CardComPraia = JetskiCardProps & { praia?: string }

function mapModeloToCard(modelo: MarketplaceModelo): CardComPraia {
  return {
    id: modelo.id,
    modelo: modelo.nome,
    tipo: 'JETSKI',
    empresa: modelo.empresaNome,
    precoHora: modelo.precoBaseHora,
    precoPacote30min: modelo.precoPacote30min,
    imagemUrl: getPrincipalImage(modelo),
    // A praia é o que o cliente busca — vai em destaque na localização do card
    localizacao: modelo.praia ? `${modelo.praia} · ${modelo.localizacao}` : modelo.localizacao,
    praia: modelo.praia,
    // Avaliação virá em versões futuras
  }
}

/** Normaliza para busca sem acento/caixa ("Praia do Forte" casa com "forte"). */
function normalizar(v: string): string {
  return v.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase()
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
  const [jetskis, setJetskis] = useState<CardComPraia[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [busca, setBusca] = useState('')
  const [praiaAtiva, setPraiaAtiva] = useState<string | null>(null)

  // Chips com as praias realmente publicadas (deduplicadas, ordem alfabética)
  const praias = useMemo(
    () => [...new Set(jetskis.map((j) => j.praia).filter((p): p is string => !!p))].sort((a, b) => a.localeCompare(b, 'pt-BR')),
    [jetskis]
  )

  const filtrados = useMemo(() => {
    let lista = jetskis
    if (praiaAtiva) lista = lista.filter((j) => j.praia === praiaAtiva)
    if (busca.trim()) {
      const q = normalizar(busca)
      lista = lista.filter((j) =>
        [j.modelo, j.empresa, j.localizacao, j.praia ?? ''].some((campo) => normalizar(campo).includes(q))
      )
    }
    return lista
  }, [jetskis, busca, praiaAtiva])

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
      {/* Busca por praia — o jeito que o cliente procura */}
      <div className="mb-10 space-y-4">
        <div className="relative max-w-md">
          <Search className="absolute left-4 top-1/2 h-4 w-4 -translate-y-1/2 text-white/40" />
          <input
            type="search"
            value={busca}
            onChange={(e) => setBusca(e.target.value)}
            placeholder="Buscar por praia, cidade, modelo ou locadora…"
            className="w-full rounded-full border border-white/15 bg-white/[0.05] py-3 pl-11 pr-4 text-sm text-white placeholder:text-white/40 outline-none transition-colors focus:border-gold/50"
          />
        </div>
        {praias.length > 0 && (
          <div className="flex flex-wrap items-center gap-2">
            <span className="text-xs uppercase tracking-wider text-white/40">Praias:</span>
            {praias.map((p) => (
              <button
                key={p}
                onClick={() => setPraiaAtiva(praiaAtiva === p ? null : p)}
                className={`inline-flex items-center gap-1.5 rounded-full border px-4 py-1.5 text-sm transition-colors ${
                  praiaAtiva === p
                    ? 'border-gold bg-gold/15 text-gold'
                    : 'border-white/15 text-white/60 hover:border-gold/40 hover:text-gold'
                }`}
              >
                {p}
                {praiaAtiva === p && <X className="h-3.5 w-3.5" />}
              </button>
            ))}
          </div>
        )}
      </div>

      {filtrados.length === 0 ? (
        <div className="rounded-lg border border-white/10 bg-white/[0.03] p-10 text-center text-white/50">
          Nenhuma embarcação encontrada
          {praiaAtiva ? ` na ${praiaAtiva}` : ''}
          {busca.trim() ? ` para “${busca.trim()}”` : ''}
          . Limpe os filtros para ver todas.
        </div>
      ) : (
        <div className="grid gap-6 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
          {filtrados.map(({ praia: _praia, ...offering }) => (
            <JetskiCard key={offering.id} {...offering} />
          ))}
        </div>
      )}
      {/* Contagem real — sem números inventados */}
      <p className="mt-16 text-center text-white/30 text-sm">
        Exibindo {filtrados.length} de {jetskis.length} embarcaç{jetskis.length === 1 ? 'ão' : 'ões'} de locadoras parceiras
      </p>
    </>
  )
}
