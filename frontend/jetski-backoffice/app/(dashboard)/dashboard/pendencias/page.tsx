'use client'

import { useMemo, useState } from 'react'
import { useQuery, useQueries, useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { Check, ChevronRight, ClipboardList, Circle, Search, Ban, Loader2 } from 'lucide-react'
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
import { Button } from '@/components/ui/button'
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

/** Situação da GRU (EMA) para o tooltip do chip "GRU paga". */
function hintGru(hab: Habilitacao | null | undefined): string {
  if (hab?.gruPago) return hab.gruNumero ? `paga · nº ${hab.gruNumero}` : 'paga'
  if (hab?.gruPixCopiaECola || hab?.gruBoletoDisponivel) return 'gerada — aguardando pagamento'
  return 'gerar e pagar a GRU'
}

/** Dados pessoais (anexos NORMAM-212) — necessários só para emitir os documentos. */
function faltaDadosPessoais(c: Reserva['cliente']): string {
  const falta: string[] = []
  if (!c?.rg) falta.push('RG')
  if (!c?.orgaoEmissor) falta.push('órgão emissor')
  if (!c?.nacionalidade) falta.push('nacionalidade')
  if (!c?.naturalidade) falta.push('naturalidade')
  return falta.length ? `falta: ${falta.join(', ')}` : 'completos'
}

function etapasDe(
  r: Reserva,
  hab: Habilitacao | null | undefined,
  aceite: { aceitoEm?: string } | null | undefined
): Etapa[] {
  const ema = hab?.via === 'EMA'
  const etapas: Etapa[] = [
    { chave: 'termos', label: 'Termos', ok: !!aceite, hint: aceite ? 'assinados' : 'pendentes' },
  ]
  if (ema) {
    const c = r.cliente
    const dadosOk = !!(c?.rg && c?.orgaoEmissor && c?.nacionalidade && c?.naturalidade)
    etapas.push(
      { chave: 'dados', label: 'Dados pessoais', ok: dadosOk, hint: faltaDadosPessoais(c) },
      { chave: 'gru', label: 'GRU paga', ok: !!hab?.gruPago, hint: hintGru(hab) },
      {
        chave: 'comprovante',
        label: 'Comprovante GRU',
        ok: !!hab?.gruComprovanteDisponivel,
        hint: hab?.gruComprovanteDisponivel ? 'anexado' : 'a anexar',
      }
    )
  } else {
    etapas.push({
      chave: 'cha',
      label: 'CHA',
      ok: !!hab?.resolvida,
      hint: hab?.chaNumero ? `nº ${hab.chaNumero}` : 'informar CHA',
    })
  }
  etapas.push({
    chave: 'emissao',
    label: 'Emissão (Marinha)',
    ok: !!r.documentoEmitidoEm,
    hint: r.documentoEmitidoEm ? 'emitidos' : 'a emitir',
  })
  return etapas
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
  const qc = useQueryClient()
  const [q, setQ] = useState('')
  const [detail, setDetail] = useState<Reserva | null>(null)
  const [sheetOpen, setSheetOpen] = useState(false)

  const cancelar = useMutation({
    mutationFn: (id: string) => reservasService.cancelar(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['reservas', currentTenant?.id] })
      toast.success('Reserva cancelada — saiu da fila e das pendências.')
    },
    onError: (e: unknown) => {
      const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
      toast.error(msg ?? 'Falha ao cancelar.')
    },
  })

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
              <div
                key={r.id}
                className="flex w-full items-center gap-3 px-4 py-3 hover:bg-accent/50"
              >
                <button onClick={() => abrir(r)} className="flex min-w-0 flex-1 items-center gap-4 text-left">
                  <div className="w-14 shrink-0 text-sm tabular-nums text-muted-foreground">
                    {fmtData(r.dataInicio)}
                  </div>

                  <div className="min-w-0 flex-1 space-y-1.5">
                    <div className="flex items-center gap-2">
                      <p className="truncate font-medium">{r.cliente?.nome || 'Cliente não informado'}</p>
                      <span className="truncate text-sm text-muted-foreground">· {r.modelo?.nome}</span>
                      <span className="shrink-0 rounded bg-muted px-1.5 py-0.5 font-mono text-[11px] text-muted-foreground">
                        #{r.id.slice(0, 8)}
                      </span>
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
                </button>

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
                  <Button
                    size="icon"
                    variant="ghost"
                    className="h-8 w-8 text-red-600 hover:bg-red-50 hover:text-red-700 dark:hover:bg-red-950/40"
                    title="Cancelar reserva"
                    disabled={cancelar.isPending && cancelar.variables === r.id}
                    onClick={() => {
                      if (window.confirm(`Cancelar a reserva de ${r.cliente?.nome ?? 'cliente'}? Sai da fila e das pendências.`))
                        cancelar.mutate(r.id)
                    }}
                  >
                    {cancelar.isPending && cancelar.variables === r.id ? (
                      <Loader2 className="h-4 w-4 animate-spin" />
                    ) : (
                      <Ban className="h-4 w-4" />
                    )}
                  </Button>
                  <ChevronRight className="h-4 w-4 text-muted-foreground" />
                </div>
              </div>
            )
          })}
        </div>
      )}

      <ReservaDetailSheet reserva={detail} open={sheetOpen} onOpenChange={setSheetOpen} />
    </div>
  )
}
