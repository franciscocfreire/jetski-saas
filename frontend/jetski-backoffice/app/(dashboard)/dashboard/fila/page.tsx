'use client'

import { useEffect, useMemo, useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Ship, Anchor, Lightbulb, Users, Timer, Link2, Unlink } from 'lucide-react'
import { useTenantStore } from '@/lib/store/tenant-store'
import {
  reservasService,
  clientesService,
  modelosService,
  jetskisService,
  locacoesService,
} from '@/lib/api/services'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Checkbox } from '@/components/ui/checkbox'
import { EmbarqueDialog } from '@/components/fila/embarque-dialog'
import { cn, formatDuracao } from '@/lib/utils'
import {
  otimizarFila,
  planoFifo,
  esperaPonderada,
  type Alocacao,
  type JetEstado,
  type Party,
} from '@/lib/fila/otimizador'
import type { Reserva } from '@/lib/api/types'

const TURNAROUND_KEY = 'fila.turnaround'

const durMin = (r: Reserva) =>
  Math.max(1, Math.round((new Date(r.dataFimPrevista).getTime() - new Date(r.dataInicio).getTime()) / 60_000))

/** Jet da frota de um modelo, com o tempo até ficar pronto. */
type JetFrota = JetEstado & { livre: boolean; retornoMin: number }

type Bloco = {
  modeloId: string
  modeloNome: string
  jets: JetFrota[]
  parties: Party[]
  plano: Alocacao[]
  ganhoJetMin: number
  reordena: boolean
  reservasNaFila: Reserva[]
}

export default function FilaPage() {
  const { currentTenant } = useTenantStore()
  const qc = useQueryClient()
  const [alvo, setAlvo] = useState<Reserva | null>(null)
  const [dialogOpen, setDialogOpen] = useState(false)
  const [now, setNow] = useState(() => Date.now())
  const [turnaround, setTurnaround] = useState(5)
  const [sel, setSel] = useState<Set<string>>(new Set())
  const [grupos, setGrupos] = useState<string[][]>([])

  // Relógio (atualiza contagens) + turnaround persistido no navegador.
  useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), 30_000)
    return () => clearInterval(id)
  }, [])
  useEffect(() => {
    const v = Number(localStorage.getItem(TURNAROUND_KEY))
    if (Number.isFinite(v) && v > 0) setTurnaround(v)
  }, [])
  useEffect(() => {
    localStorage.setItem(TURNAROUND_KEY, String(turnaround))
  }, [turnaround])

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
  const { data: emCurso } = useQuery({
    queryKey: ['locacoes-em-curso', currentTenant?.id],
    queryFn: () => locacoesService.list({ status: 'EM_CURSO' }),
    enabled: !!currentTenant,
  })

  const blocos = useMemo<Bloco[]>(() => {
    if (!reservas) return []
    const cMap = new Map((clientes ?? []).map((c) => [c.id, c]))
    const mMap = new Map((modelos ?? []).map((m) => [m.id, m]))

    // Retorno (min a partir de agora) por jetski em uso.
    const retornoPorJet = new Map<string, number>()
    for (const l of emCurso ?? []) {
      if (!l.jetskiId) continue
      const fim = new Date(l.dataCheckIn).getTime() + (l.duracaoPrevista ?? 0) * 60_000
      retornoPorJet.set(l.jetskiId, Math.max(0, (fim - now) / 60_000))
    }

    const naFila = reservas
      .filter((r) => !!r.documentoEmitidoEm && (r.status === 'PENDENTE' || r.status === 'CONFIRMADA'))
      .map((r) => ({ ...r, cliente: cMap.get(r.clienteId) }))
      .sort((a, b) => a.dataInicio.localeCompare(b.dataInicio))

    const modelosNaFila = [...new Set(naFila.map((r) => r.modeloId))]

    return modelosNaFila.map((modeloId) => {
      const fila = naFila.filter((r) => r.modeloId === modeloId)
      const ordemDe = new Map(fila.map((r, i) => [r.id, i]))

      // Frota do modelo (livre ou em uso); ignora manutenção/inativo.
      const jets: JetFrota[] = (jetskis ?? [])
        .filter((j) => j.modeloId === modeloId && (j.status === 'DISPONIVEL' || j.status === 'LOCADO'))
        .map((j) => {
          const retornoMin = j.status === 'LOCADO' ? (retornoPorJet.get(j.id) ?? turnaround) : 0
          const livre = j.status === 'DISPONIVEL'
          return {
            id: j.id,
            serie: j.serie,
            livre,
            retornoMin,
            prontoEm: livre ? 0 : retornoMin + turnaround,
          }
        })
        .sort((a, b) => a.prontoEm - b.prontoEm)

      // Parties: grupos (andam juntos) + avulsos.
      const naFilaIds = new Set(fila.map((r) => r.id))
      const agrupadas = new Set<string>()
      const parties: Party[] = []
      for (const g of grupos) {
        const membros = g.filter((id) => naFilaIds.has(id)).map((id) => fila.find((r) => r.id === id)!)
        if (membros.length < 2) continue
        membros.forEach((m) => agrupadas.add(m.id))
        parties.push({
          id: 'g:' + membros.map((m) => m.id).join(','),
          label: membros.map((m) => m.cliente?.nome?.split(' ')[0] ?? 'Cliente').join(' + '),
          jets: membros.length,
          duracaoMin: Math.max(...membros.map(durMin)),
          ordem: Math.min(...membros.map((m) => ordemDe.get(m.id)!)),
          reservaIds: membros.map((m) => m.id),
        })
      }
      for (const r of fila) {
        if (agrupadas.has(r.id)) continue
        parties.push({
          id: r.id,
          label: r.cliente?.nome ?? 'Cliente',
          jets: 1,
          duracaoMin: durMin(r),
          ordem: ordemDe.get(r.id)!,
          reservaIds: [r.id],
        })
      }
      parties.sort((a, b) => a.ordem - b.ordem)

      const plano = otimizarFila(jets, parties, turnaround)
      const fifo = planoFifo(jets, parties, turnaround)
      const ganhoJetMin = Math.round(esperaPonderada(fifo) - esperaPonderada(plano))
      const reordena = plano[0]?.party.id !== fifo[0]?.party.id

      return {
        modeloId,
        modeloNome: mMap.get(modeloId)?.nome ?? 'Modelo',
        jets,
        parties,
        plano,
        ganhoJetMin,
        reordena,
        reservasNaFila: fila,
      }
    })
  }, [reservas, clientes, modelos, jetskis, emCurso, grupos, turnaround, now])

  const fmtClock = (min: number) =>
    new Date(now + min * 60_000).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })

  const embarcar = (reservaId: string) => {
    const r = reservas?.find((x) => x.id === reservaId) ?? null
    setAlvo(r)
    setDialogOpen(true)
  }

  const toggleSel = (id: string) =>
    setSel((s) => {
      const n = new Set(s)
      if (n.has(id)) n.delete(id)
      else n.add(id)
      return n
    })

  const agrupar = (idsDoModelo: string[]) => {
    const escolhidos = idsDoModelo.filter((id) => sel.has(id))
    if (escolhidos.length < 2) return
    setGrupos((g) => [...g, escolhidos])
    setSel((s) => {
      const n = new Set(s)
      escolhidos.forEach((id) => n.delete(id))
      return n
    })
  }
  const desagrupar = (partyId: string) =>
    setGrupos((g) => g.filter((grp) => 'g:' + grp.join(',') !== partyId))

  if (!currentTenant) {
    return (
      <div className="flex h-[50vh] items-center justify-center">
        <p className="text-muted-foreground">Selecione um tenant para continuar</p>
      </div>
    )
  }

  const vazia = blocos.length === 0

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-3xl font-bold">Fila de espera</h1>
          <p className="text-muted-foreground">
            Otimize o uso da frota: a sugestão considera quando cada jet volta, a duração e os grupos.
          </p>
        </div>
        <div className="flex items-center gap-2 rounded-lg border px-3 py-2">
          <Timer className="h-4 w-4 text-muted-foreground" />
          <Label htmlFor="turn" className="text-xs text-muted-foreground">
            Embarque/desembarque
          </Label>
          <Input
            id="turn"
            type="number"
            min={0}
            max={60}
            value={turnaround}
            onChange={(e) => setTurnaround(Math.max(0, Number(e.target.value) || 0))}
            className="h-8 w-16"
          />
          <span className="text-xs text-muted-foreground">min</span>
        </div>
      </div>

      {isLoading ? (
        <p className="py-10 text-center text-muted-foreground">Carregando…</p>
      ) : vazia ? (
        <div className="rounded-xl border py-16 text-center">
          <Ship className="mx-auto h-12 w-12 text-muted-foreground" />
          <p className="mt-4 text-muted-foreground">Ninguém na fila 🎉</p>
        </div>
      ) : (
        <div className="space-y-6">
          {blocos.map((b) => {
            const proxima = b.plano[0]
            const idsModelo = b.reservasNaFila.map((r) => r.id)
            const selNoModelo = idsModelo.filter((id) => sel.has(id)).length
            return (
              <div key={b.modeloId} className="overflow-hidden rounded-xl border">
                <div className="flex items-center justify-between border-b bg-muted/30 px-4 py-2">
                  <h3 className="font-semibold">{b.modeloNome}</h3>
                  <Badge variant="secondary">{b.reservasNaFila.length} na fila</Badge>
                </div>

                {/* Frota agora */}
                <div className="flex flex-wrap items-center gap-2 border-b px-4 py-3">
                  <span className="mr-1 text-xs font-medium text-muted-foreground">Frota:</span>
                  {b.jets.length === 0 && (
                    <span className="text-xs text-muted-foreground">nenhum jet deste modelo ativo</span>
                  )}
                  {b.jets.map((j) => (
                    <span
                      key={j.id}
                      className={cn(
                        'inline-flex items-center gap-1 rounded-full border px-2 py-0.5 text-xs',
                        j.livre
                          ? 'border-emerald-300 bg-emerald-50 text-emerald-700 dark:bg-emerald-950/40 dark:text-emerald-300'
                          : 'border-amber-300 bg-amber-50 text-amber-800 dark:bg-amber-950/40 dark:text-amber-200'
                      )}
                    >
                      <Ship className="h-3 w-3" />
                      {j.serie}
                      {j.livre ? (
                        <span>· livre</span>
                      ) : (
                        <span>
                          · volta {fmtClock(j.retornoMin)} (em {formatDuracao(j.retornoMin)})
                        </span>
                      )}
                    </span>
                  ))}
                </div>

                {/* Sugestão */}
                {proxima && (
                  <div className="border-b bg-primary/5 px-4 py-3">
                    <div className="flex items-start gap-2">
                      <Lightbulb className="mt-0.5 h-4 w-4 shrink-0 text-amber-500" />
                      <div className="min-w-0 flex-1 space-y-1">
                        <p className="text-sm">
                          <span className="font-medium">Próximo: </span>
                          {proxima.party.label}{' '}
                          <span className="text-muted-foreground">
                            ({proxima.party.jets} jet{proxima.party.jets > 1 ? 's' : ''} ·{' '}
                            {formatDuracao(proxima.party.duracaoMin)}) — embarca{' '}
                            {proxima.inicio < 1 ? 'agora' : `${fmtClock(proxima.inicio)} (em ${formatDuracao(proxima.inicio)})`}
                          </span>
                        </p>
                        <p className="text-xs text-muted-foreground">
                          {b.reordena && b.ganhoJetMin > 0
                            ? `Sugestão reordena a fila: economiza ~${b.ganhoJetMin} min de jet ocioso vs. ordem de chegada (evita deixar jet parado esperando formar grupo).`
                            : 'A ordem de chegada já é a melhor para a frota agora.'}
                        </p>
                        {b.plano.length > 1 && (
                          <p className="text-xs text-muted-foreground">
                            Plano:{' '}
                            {b.plano
                              .map(
                                (a, i) =>
                                  `${i + 1}) ${a.party.label} ${a.inicio < 1 ? 'agora' : fmtClock(a.inicio)}`
                              )
                              .join('  ·  ')}
                          </p>
                        )}
                      </div>
                    </div>
                  </div>
                )}

                {/* Fila / parties */}
                <div className="divide-y">
                  {b.parties.map((p) => {
                    const ehProxima = proxima?.party.id === p.id
                    const grupo = p.jets > 1
                    return (
                      <div
                        key={p.id}
                        className={cn('px-4 py-3', ehProxima && 'bg-amber-50/60 dark:bg-amber-950/20')}
                      >
                        <div className="flex items-center gap-3">
                          <div className="w-6 shrink-0 text-center text-sm font-semibold tabular-nums text-muted-foreground">
                            {p.ordem + 1}
                          </div>
                          {!grupo && (
                            <Checkbox
                              checked={sel.has(p.reservaIds[0])}
                              onCheckedChange={() => toggleSel(p.reservaIds[0])}
                            />
                          )}
                          <div className="min-w-0 flex-1">
                            <p className="flex items-center gap-2 truncate font-medium">
                              {grupo && <Users className="h-4 w-4 text-primary" />}
                              {p.label}
                              {ehProxima && (
                                <Badge className="bg-amber-500 hover:bg-amber-500">próximo</Badge>
                              )}
                            </p>
                            <p className="text-xs text-muted-foreground">
                              {grupo ? `${p.jets} jets juntos · ` : ''}
                              {b.modeloNome} · {formatDuracao(p.duracaoMin)}
                            </p>
                          </div>
                          {grupo ? (
                            <div className="flex items-center gap-2">
                              <Button
                                type="button"
                                variant="ghost"
                                size="sm"
                                onClick={() => desagrupar(p.id)}
                              >
                                <Unlink className="mr-1 h-3.5 w-3.5" /> Desagrupar
                              </Button>
                              <div className="flex flex-col gap-1">
                                {p.reservaIds.map((rid, i) => (
                                  <Button
                                    key={rid}
                                    type="button"
                                    size="sm"
                                    variant="outline"
                                    onClick={() => embarcar(rid)}
                                  >
                                    <Anchor className="mr-1 h-3.5 w-3.5" /> Embarcar {i + 1}
                                  </Button>
                                ))}
                              </div>
                            </div>
                          ) : (
                            <Button type="button" size="sm" onClick={() => embarcar(p.reservaIds[0])}>
                              <Anchor className="mr-1 h-4 w-4" /> Embarcar
                            </Button>
                          )}
                        </div>
                      </div>
                    )
                  })}
                </div>

                {/* Ação de agrupar (quando há ≥2 selecionados neste modelo) */}
                {selNoModelo >= 2 && (
                  <div className="flex items-center justify-between border-t bg-muted/30 px-4 py-2">
                    <span className="text-xs text-muted-foreground">
                      {selNoModelo} selecionados — andar juntos usa {selNoModelo} jets ao mesmo tempo
                    </span>
                    <Button type="button" size="sm" onClick={() => agrupar(idsModelo)}>
                      <Link2 className="mr-1 h-3.5 w-3.5" /> Andam juntos
                    </Button>
                  </div>
                )}
              </div>
            )
          })}
        </div>
      )}

      <EmbarqueDialog
        reservaId={alvo?.id ?? ''}
        modeloId={alvo?.modeloId}
        open={dialogOpen}
        onOpenChange={setDialogOpen}
        onEmbarcado={() => {
          qc.invalidateQueries({ queryKey: ['reservas', currentTenant?.id] })
          qc.invalidateQueries({ queryKey: ['locacoes-em-curso', currentTenant?.id] })
          qc.invalidateQueries({ queryKey: ['jetskis', currentTenant?.id] })
        }}
      />
    </div>
  )
}
