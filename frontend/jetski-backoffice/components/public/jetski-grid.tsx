'use client'

import { useEffect, useMemo, useState } from 'react'
import { Search, X } from 'lucide-react'
import { JetskiCard, JetskiCardProps } from './jetski-card'
import { marketplaceService, MarketplaceModelo, getPrincipalImage } from '@/lib/api/services/marketplace'

/**
 * Map API model to card props
 */
type CardComLocal = JetskiCardProps & { praia?: string; cidade?: string; uf?: string }

function mapModeloToCard(modelo: MarketplaceModelo): CardComLocal {
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
    cidade: modelo.cidade,
    uf: modelo.uf,
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
  const [jetskis, setJetskis] = useState<CardComLocal[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [busca, setBusca] = useState('')
  // Filtros em cascata: muitas praias do Brasil repetem nome — o contexto UF/cidade desambigua
  const [ufAtiva, setUfAtiva] = useState('')
  const [cidadeAtiva, setCidadeAtiva] = useState('')
  const [praiaAtiva, setPraiaAtiva] = useState('')

  const distintos = (valores: (string | undefined)[]) =>
    [...new Set(valores.filter((v): v is string => !!v))].sort((a, b) => a.localeCompare(b, 'pt-BR'))

  const ufs = useMemo(() => distintos(jetskis.map((j) => j.uf)), [jetskis])
  const cidades = useMemo(
    () => distintos(jetskis.filter((j) => !ufAtiva || j.uf === ufAtiva).map((j) => j.cidade)),
    [jetskis, ufAtiva]
  )
  const praias = useMemo(
    () =>
      distintos(
        jetskis
          .filter((j) => (!ufAtiva || j.uf === ufAtiva) && (!cidadeAtiva || j.cidade === cidadeAtiva))
          .map((j) => j.praia)
      ),
    [jetskis, ufAtiva, cidadeAtiva]
  )

  const filtrados = useMemo(() => {
    let lista = jetskis
    if (ufAtiva) lista = lista.filter((j) => j.uf === ufAtiva)
    if (cidadeAtiva) lista = lista.filter((j) => j.cidade === cidadeAtiva)
    if (praiaAtiva) lista = lista.filter((j) => j.praia === praiaAtiva)
    if (busca.trim()) {
      const q = normalizar(busca)
      lista = lista.filter((j) =>
        [j.modelo, j.empresa, j.localizacao, j.praia ?? '', j.cidade ?? '', j.uf ?? ''].some((campo) =>
          normalizar(campo).includes(q)
        )
      )
    }
    return lista
  }, [jetskis, busca, ufAtiva, cidadeAtiva, praiaAtiva])

  const temFiltro = !!(ufAtiva || cidadeAtiva || praiaAtiva || busca.trim())
  const limparFiltros = () => {
    setUfAtiva('')
    setCidadeAtiva('')
    setPraiaAtiva('')
    setBusca('')
  }

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
      {/* Busca por localização: Estado → Cidade → Praia (em cascata) + texto livre */}
      <div className="mb-10 flex flex-wrap items-center gap-3">
        <div className="relative min-w-0 flex-1 basis-64">
          <Search className="absolute left-4 top-1/2 h-4 w-4 -translate-y-1/2 text-white/40" />
          <input
            type="search"
            value={busca}
            onChange={(e) => setBusca(e.target.value)}
            placeholder="Buscar por praia, cidade, modelo ou locadora…"
            className="w-full rounded-full border border-white/15 bg-white/[0.05] py-3 pl-11 pr-4 text-sm text-white placeholder:text-white/40 outline-none transition-colors focus:border-gold/50"
          />
        </div>
        <select
          value={ufAtiva}
          onChange={(e) => {
            setUfAtiva(e.target.value)
            setCidadeAtiva('')
            setPraiaAtiva('')
          }}
          aria-label="Filtrar por estado"
          className="min-w-0 rounded-full border border-white/15 bg-white/[0.05] px-4 py-3 text-sm text-white outline-none transition-colors focus:border-gold/50 [&>option]:bg-slate-900"
        >
          <option value="">Estado</option>
          {ufs.map((u) => (
            <option key={u} value={u}>{u}</option>
          ))}
        </select>
        <select
          value={cidadeAtiva}
          onChange={(e) => {
            setCidadeAtiva(e.target.value)
            setPraiaAtiva('')
          }}
          disabled={cidades.length === 0}
          aria-label="Filtrar por cidade"
          className="min-w-0 rounded-full border border-white/15 bg-white/[0.05] px-4 py-3 text-sm text-white outline-none transition-colors focus:border-gold/50 disabled:opacity-40 [&>option]:bg-slate-900"
        >
          <option value="">Cidade</option>
          {cidades.map((c) => (
            <option key={c} value={c}>{c}</option>
          ))}
        </select>
        <select
          value={praiaAtiva}
          onChange={(e) => setPraiaAtiva(e.target.value)}
          disabled={praias.length === 0}
          aria-label="Filtrar por praia"
          className="min-w-0 rounded-full border border-white/15 bg-white/[0.05] px-4 py-3 text-sm text-white outline-none transition-colors focus:border-gold/50 disabled:opacity-40 [&>option]:bg-slate-900"
        >
          <option value="">Praia</option>
          {praias.map((p) => (
            <option key={p} value={p}>{p}</option>
          ))}
        </select>
        {temFiltro && (
          <button
            onClick={limparFiltros}
            className="inline-flex items-center gap-1.5 rounded-full border border-white/15 px-4 py-2.5 text-sm text-white/60 transition-colors hover:border-gold/40 hover:text-gold"
          >
            <X className="h-3.5 w-3.5" /> Limpar
          </button>
        )}
      </div>

      {filtrados.length === 0 ? (
        <div className="rounded-lg border border-white/10 bg-white/[0.03] p-10 text-center text-white/50">
          Nenhuma embarcação encontrada com esses filtros.{' '}
          <button onClick={limparFiltros} className="text-gold/70 underline-offset-2 hover:text-gold hover:underline">
            Limpar filtros
          </button>
        </div>
      ) : (
        <div className="grid gap-6 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
          {filtrados.map(({ praia: _praia, cidade: _cidade, uf: _uf, ...offering }) => (
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
