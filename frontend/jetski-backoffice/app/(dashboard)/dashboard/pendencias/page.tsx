'use client'

import { useMemo, useState } from 'react'
import { useQuery, useQueries } from '@tanstack/react-query'
import { Check, ChevronRight, ClipboardList, Circle, Search } from 'lucide-react'
import { useTenantStore } from '@/lib/store/tenant-store'
import {
  reservasService,
  clientesService,
  modelosService,
  jetskisService,
  habilitacaoService,
  aceiteService,
} from '@/lib/api/services'
import { Badge } from '@/components/ui/badge'
import { Input } from '@/components/ui/input'
import { ReservaDetailSheet } from '@/components/agenda/reserva-detail-sheet'
import { cn } from '@/lib/utils'
import type { Reserva, ReservaStatus, Habilitacao } from '@/lib/api/types'


const statusBadge: Record<ReservaStatus, 'success' | 'warning' | 'secondary'> = {
  RASCUNHO: 'secondary',
  PENDENTE: 'warning',
  CONFIRMADA: 'success',
  CANCELADA: 'secondary',
  FINALIZADA: 'secondary',
  EXPIRADA: 'secondary',
}

const fmtData = (iso: string) =>
  new Date(iso).toLocaleString('pt-BR', {
    day: '2-digit',
    month: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })

/** Um estágio do atendimento, na mesma ordem do drawer (Estágio). */
type Etapa = { chave: string; label: string; ok: boolean; hint?: string }

/** Detalhe do que falta na habilitação EMA (vai no tooltip do chip). */
function faltaHabilitacao(hab: Habilitacao | null | undefined): string {
  if (!hab) return 'não registrada'
  if (hab.resolvida) return hab.via === 'EMA' ? 'GRU paga' : 'CHA informada'
  if (hab.via === 'EMA') {
    const falta: string[] = []
    if (!hab.gruPago) falta.push('GRU')
    if (!hab.anexoSaude) falta.push('5-C')
    if (!hab.anexoRegras) falta.push('regras')
    if (!hab.anexoResidencia) falta.push('residência')
    if (!hab.instrutorId) falta.push('instrutor')
    return falta.length ? `falta: ${falta.join(', ')}` : 'pendente'
  }
  return 'pendente'
}

function etapasDe(
  r: Reserva,
  hab: Habilitacao | null | undefined,
  aceite: { aceitoEm?: string } | null | undefined
): Etapa[] {
  const ema = hab?.via === 'EMA'
  const habLabel = ema ? 'GRU' : hab?.via === 'CHA' ? 'CHA' : 'Habilitação'
  return [
    { chave: 'termos', label: 'Termos', ok: !!aceite, hint: aceite ? 'assinados' : 'pendentes' },
    { chave: 'hab', label: habLabel, ok: !!hab?.resolvida, hint: faltaHabilitacao(hab) },
    { chave: 'docs', label: 'Documentos', ok: !!r.documentoEmitidoEm, hint: r.documentoEmitidoEm ? 'emitidos' : 'a emitir' },
  ]
}

function ChipEtapa({ etapa, proxima }: { etapa: Etapa; proxima: boolean }) {
  return (
    <span
      title={`${etapa.label} — ${etapa.hint ?? (etapa.ok ? 'ok' : 'pendente')}`}
      className={cn(
        'inline-flex items-center gap-1 rounded-full border px-2 py-0.5 text-xs font-medium transition-colors',
        etapa.ok
          ? 'border-transparent bg-emerald-50 text-emerald-700 dark:bg-emerald-950/40 dark:text-emerald-300'
          : 'border-amber-300 bg-amber-50 text-amber-800 dark:border-amber-700 dark:bg-amber-950/40 dark:text-amber-200',
        proxima && 'ring-2 ring-amber-400 ring-offset-1 dark:ring-offset-background'
      )}
    >
      {etapa.ok ? (
        <Check className="h-3 w-3 shrink-0" />
      ) : (
        <Circle className="h-3 w-3 shrink-0" />
      )}
      {etapa.label}
    </span>
  )
}

export default function PendenciasPage() {
  const { currentTenant } = useTenantStore()
  const [q, setQ] = useState('')
  const [detail, setDetail] = useState<Reserva | null>(null)
  const [sheetOpen, setSheetOpen] = useState(false)

  const { data: reservas, isLoading } = useQuery({
    queryKey: ['reservas', currentTenant?.id],
    queryFn: () => reservasService.list(),
    enabled: !!currentTenant,
  })
  const { data: clientes } = useQuery({
    queryKey: ['clientes', currentTenant?.id],
    queryFn: () => clientesService.list(),
    enabled: !!currentTenant,
  })
  const { data: modelos } = useQuery({
    queryKey: ['modelos', currentTenant?.id],
    queryFn: () => modelosService.list(),
    enabled: !!currentTenant,
  })
  const { data: jetskis } = useQuery({
    queryKey: ['jetskis', currentTenant?.id],
    queryFn: () => jetskisService.list(),
    enabled: !!currentTenant,
  })

  // Base estável (sem o filtro de busca) — define as reservas cujas etapas buscamos.
  const base = useMemo<Reserva[]>(() => {
    if (!reservas) return []
    const cMap = new Map((clientes ?? []).map((c) => [c.id, c]))
    const mMap = new Map((modelos ?? []).map((m) => [m.id, m]))
    const jMap = new Map((jetskis ?? []).map((j) => [j.id, j]))
    return reservas
      // Pendência = reserva PENDENTE (finalizada com algo faltando, ou portal aguardando).
      // RASCUNHO (atendimento em aberto) e CONFIRMADA (completa) ficam fora.
      .filter((r) => r.status === 'PENDENTE')
      .map((r) => ({
        ...r,
        cliente: cMap.get(r.clienteId),
        modelo: mMap.get(r.modeloId),
        jetski: r.jetskiId ? jMap.get(r.jetskiId) : undefined,
      }))
      .sort((a, b) => a.dataInicio.localeCompare(b.dataInicio))
  }, [reservas, clientes, modelos, jetskis])

  // Carrega habilitação + aceite de cada reserva pendente (mesmas chaves do drawer:
  // aquece o cache, então abrir o detalhe é instantâneo). A lista do balcão é pequena.
  const habQueries = useQueries({
    queries: base.map((r) => ({
      queryKey: ['habilitacao', r.id],
      queryFn: () => habilitacaoService.get(r.id),
      enabled: !!currentTenant,
      staleTime: 30_000,
    })),
  })
  const aceiteQueries = useQueries({
    queries: base.map((r) => ({
      queryKey: ['aceite', r.id],
      queryFn: () => aceiteService.get(r.id),
      enabled: !!currentTenant,
      staleTime: 30_000,
    })),
  })

  const termo = q.trim().toLowerCase()
  const linhas = base
    .map((r, i) => ({
      reserva: r,
      hab: habQueries[i]?.data,
      aceite: aceiteQueries[i]?.data,
      carregando: habQueries[i]?.isLoading || aceiteQueries[i]?.isLoading,
    }))
    .filter(({ reserva }) => !termo || (reserva.cliente?.nome ?? '').toLowerCase().includes(termo))

  const abrir = (r: Reserva) => {
    setDetail(r)
    setSheetOpen(true)
  }

  if (!currentTenant) {
    return (
      <div className="flex h-[50vh] items-center justify-center">
        <p className="text-muted-foreground">Selecione um tenant para continuar</p>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-3xl font-bold">Pendências</h1>
        <p className="text-muted-foreground">
          Reservas que ainda precisam de ação — chips em{' '}
          <span className="font-medium text-amber-600 dark:text-amber-400">âmbar</span> mostram o que falta.
        </p>
      </div>

      <div className="relative max-w-sm">
        <Search className="absolute left-2.5 top-2.5 h-4 w-4 text-muted-foreground" />
        <Input
          className="pl-8"
          placeholder="Buscar por cliente…"
          value={q}
          onChange={(e) => setQ(e.target.value)}
        />
      </div>

      {isLoading ? (
        <p className="py-10 text-center text-muted-foreground">Carregando…</p>
      ) : linhas.length === 0 ? (
        <div className="rounded-xl border py-16 text-center">
          <ClipboardList className="mx-auto h-12 w-12 text-muted-foreground" />
          <p className="mt-4 text-muted-foreground">Nenhuma reserva pendente 🎉</p>
        </div>
      ) : (
        <div className="divide-y rounded-xl border">
          {linhas.map(({ reserva: r, hab, aceite, carregando }) => {
            const etapas = etapasDe(r, hab, aceite)
            const faltam = etapas.filter((e) => !e.ok).length
            const proximaIdx = etapas.findIndex((e) => !e.ok)
            return (
              <button
                key={r.id}
                onClick={() => abrir(r)}
                className="flex w-full items-center gap-4 px-4 py-3 text-left hover:bg-accent/50"
              >
                <div className="w-14 shrink-0 text-sm tabular-nums text-muted-foreground">
                  {fmtData(r.dataInicio)}
                </div>

                <div className="min-w-0 flex-1 space-y-1.5">
                  <div className="flex items-center gap-2">
                    <p className="truncate font-medium">{r.cliente?.nome || 'Cliente não informado'}</p>
                    <span className="truncate text-sm text-muted-foreground">· {r.modelo?.nome}</span>
                  </div>
                  {/* Trilha de etapas — o que falta salta em âmbar; a próxima ação ganha um anel. */}
                  <div className="flex flex-wrap items-center gap-1.5">
                    {carregando && !hab && !aceite ? (
                      <span className="text-xs text-muted-foreground">Carregando etapas…</span>
                    ) : (
                      etapas.map((e, i) => (
                        <ChipEtapa key={e.chave} etapa={e} proxima={i === proximaIdx} />
                      ))
                    )}
                  </div>
                </div>

                <div className="flex shrink-0 items-center gap-2">
                  <Badge variant={r.cliente?.origem === 'PORTAL' ? 'default' : 'secondary'}>
                    {r.cliente?.origem === 'PORTAL' ? 'Online' : 'Balcão'}
                  </Badge>
                  {!carregando && (
                    <span
                      className={cn(
                        'hidden text-xs tabular-nums sm:inline',
                        faltam === 0 ? 'text-emerald-600' : 'text-amber-600 dark:text-amber-400'
                      )}
                    >
                      {faltam === 0 ? 'pronto' : `faltam ${faltam}`}
                    </span>
                  )}
                  <Badge variant={statusBadge[r.status]}>{r.status}</Badge>
                  <ChevronRight className="h-4 w-4 text-muted-foreground" />
                </div>
              </button>
            )
          })}
        </div>
      )}

      <ReservaDetailSheet reserva={detail} open={sheetOpen} onOpenChange={setSheetOpen} />
    </div>
  )
}
