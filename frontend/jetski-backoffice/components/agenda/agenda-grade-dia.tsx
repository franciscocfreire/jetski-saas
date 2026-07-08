'use client'

import { useEffect, useState } from 'react'
import { CircleDollarSign, FileSignature, IdCard, Wrench } from 'lucide-react'
import type { AgendaReserva, Jetski } from '@/lib/api/types'
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from '@/components/ui/tooltip'

const HORA_INICIO = 7
const HORA_FIM = 20
const HORA_PX = 52

const STATUS_BG: Record<string, string> = {
  PENDENTE: 'bg-amber-500/90',
  CONFIRMADA: 'bg-emerald-600/90',
  EM_ANDAMENTO: 'bg-blue-600/90',
  CONCLUIDA: 'bg-slate-400/90',
  CANCELADA: 'bg-red-400/80',
  EXPIRADA: 'bg-slate-400/80',
  NO_SHOW: 'bg-slate-500/80',
}

function minutosDesdeInicio(iso: string): number {
  const d = new Date(iso)
  return (d.getHours() - HORA_INICIO) * 60 + d.getMinutes()
}

function hhmm(iso: string): string {
  return new Date(iso).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })
}

/** Trio de prontidão — responde "pode embarcar?" sem abrir a reserva. */
function Prontidao({ r, clara }: { r: AgendaReserva; clara?: boolean }) {
  const okCls = clara ? 'text-white' : 'text-emerald-600'
  const offCls = clara ? 'text-white/40' : 'text-muted-foreground/50'
  const itens = [
    { ok: r.pagamentoOk, Icon: CircleDollarSign, dica: r.pagamentoOk ? 'Pagamento confirmado' : 'Pagamento pendente' },
    { ok: r.habilitacaoOk, Icon: IdCard, dica: r.habilitacaoOk ? `Habilitação resolvida (${r.habilitacaoVia ?? '—'})` : 'Habilitação pendente' },
    { ok: r.termoOk, Icon: FileSignature, dica: r.termoOk ? 'Termo assinado' : 'Termo pendente' },
  ]
  return (
    <span className="flex items-center gap-1">
      {itens.map(({ ok, Icon, dica }, i) => (
        <Tooltip key={i}>
          <TooltipTrigger asChild>
            <Icon size={13} className={ok ? okCls : offCls} aria-label={dica} />
          </TooltipTrigger>
          <TooltipContent>{dica}</TooltipContent>
        </Tooltip>
      ))}
    </span>
  )
}

/**
 * Grade do dia por JETSKI: colunas = jets (manutenção hachurada), linhas =
 * horas 07–20, blocos = reservas com prontidão. Reservas sem jet (portal)
 * ficam na faixa "A alocar". Responde "qual jet está livre às 14h?" de graça.
 */
export function AgendaGradeDia({ reservas, jetskis, dataEhHoje, onReservaClick, onSlotClick }: {
  reservas: AgendaReserva[]
  jetskis: (Jetski & { modeloNome?: string })[]
  dataEhHoje: boolean
  onReservaClick: (id: string) => void
  /** Clique num horário livre → nova reserva com modelo/horário pré-preenchidos. */
  onSlotClick?: (jetski: { modeloId: string; serie: string }, hora: number) => void
}) {
  const [agoraMin, setAgoraMin] = useState(() => minutosDesdeInicio(new Date().toISOString()))
  useEffect(() => {
    if (!dataEhHoje) return
    const t = setInterval(
      () => setAgoraMin(minutosDesdeInicio(new Date().toISOString())), 60_000)
    return () => clearInterval(t)
  }, [dataEhHoje])

  const horas = Array.from({ length: HORA_FIM - HORA_INICIO }, (_, i) => HORA_INICIO + i)
  const alturaTotal = (HORA_FIM - HORA_INICIO) * HORA_PX
  const semJet = reservas.filter((r) => !r.jetskiId)
  const porJet = new Map<string, AgendaReserva[]>()
  for (const r of reservas) {
    if (r.jetskiId) {
      porJet.set(r.jetskiId, [...(porJet.get(r.jetskiId) ?? []), r])
    }
  }
  const nowTop = (agoraMin / 60) * HORA_PX
  const mostrarAgora = dataEhHoje && agoraMin >= 0 && agoraMin <= (HORA_FIM - HORA_INICIO) * 60

  return (
    <TooltipProvider delayDuration={200}>
      <div className="space-y-3">
        {/* Faixa "A alocar" — reservas do portal sem jetski (fila de trabalho) */}
        {semJet.length > 0 && (
          <div className="rounded-lg border border-amber-200 bg-amber-50/60 p-3 dark:border-amber-900 dark:bg-amber-950/30">
            <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-amber-700 dark:text-amber-400">
              A alocar ({semJet.length}) — jetski definido no check-in
            </p>
            <div className="flex flex-wrap gap-2">
              {semJet.map((r) => (
                <button
                  key={r.id}
                  type="button"
                  onClick={() => onReservaClick(r.id)}
                  className="flex items-center gap-2 rounded-md border bg-background px-2.5 py-1.5 text-xs hover:border-primary"
                >
                  <span className="font-mono">{hhmm(r.dataInicio)}</span>
                  <span className="font-medium">{r.clienteNome ?? '—'}</span>
                  <span className="text-muted-foreground">{r.modeloNome}</span>
                  <Prontidao r={r} />
                </button>
              ))}
            </div>
          </div>
        )}

        {/* Grade */}
        <div className="overflow-x-auto rounded-lg border">
          <div className="flex min-w-max">
            {/* Régua de horas */}
            <div className="sticky left-0 z-20 w-14 shrink-0 border-r bg-background">
              <div className="h-10 border-b" />
              <div className="relative" style={{ height: alturaTotal }}>
                {horas.map((h) => (
                  <div
                    key={h}
                    className="absolute right-2 -translate-y-1/2 text-[11px] tabular-nums text-muted-foreground"
                    style={{ top: (h - HORA_INICIO) * HORA_PX }}
                  >
                    {h > HORA_INICIO ? `${String(h).padStart(2, '0')}:00` : ''}
                  </div>
                ))}
              </div>
            </div>

            {/* Colunas por jetski */}
            {jetskis.map((j) => {
              const emManutencao = j.status === 'MANUTENCAO'
              const blocos = porJet.get(j.id) ?? []
              return (
                <div key={j.id} className="w-[168px] shrink-0 border-r last:border-r-0">
                  <div className={`flex h-10 flex-col justify-center border-b px-2 ${emManutencao ? 'bg-amber-50 dark:bg-amber-950/40' : 'bg-muted/40'}`}>
                    <p className="flex items-center gap-1 truncate text-xs font-semibold">
                      {emManutencao && <Wrench size={11} className="shrink-0 text-amber-600" />}
                      {j.serie}
                    </p>
                    <p className="truncate text-[10px] text-muted-foreground">
                      {j.modeloNome ?? ''}{emManutencao ? ' · manutenção' : ''}
                    </p>
                  </div>
                  <div
                    className="relative"
                    style={{
                      height: alturaTotal,
                      backgroundImage: emManutencao
                        ? 'repeating-linear-gradient(45deg, transparent, transparent 8px, rgba(217,119,6,.08) 8px, rgba(217,119,6,.08) 16px)'
                        : undefined,
                    }}
                    title={emManutencao ? 'Em manutenção — OS aberta bloqueia a agenda' : undefined}
                  >
                    {/* linhas de hora */}
                    {horas.slice(1).map((h) => (
                      <div key={h} className="absolute inset-x-0 border-t border-dashed border-border/60"
                        style={{ top: (h - HORA_INICIO) * HORA_PX }} />
                    ))}
                    {/* slots clicáveis (atrás dos blocos): horário livre → nova reserva */}
                    {onSlotClick && !emManutencao && horas.map((h) => (
                      <button
                        key={`slot-${h}`}
                        type="button"
                        aria-label={`Nova reserva às ${String(h).padStart(2, '0')}:00 no ${j.serie}`}
                        onClick={() => onSlotClick({ modeloId: j.modeloId, serie: j.serie }, h)}
                        className="group absolute inset-x-0 z-[1] flex items-center justify-center text-transparent transition hover:bg-primary/5 hover:text-primary/60"
                        style={{ top: (h - HORA_INICIO) * HORA_PX, height: HORA_PX }}
                      >
                        <span className="text-lg font-light">+</span>
                      </button>
                    ))}
                    {/* linha do agora */}
                    {mostrarAgora && (
                      <div className="absolute inset-x-0 z-10 border-t-2 border-red-500/80"
                        style={{ top: nowTop }} />
                    )}
                    {/* blocos de reserva */}
                    {blocos.map((r) => {
                      const top = Math.max(0, (minutosDesdeInicio(r.dataInicio) / 60) * HORA_PX)
                      const fim = Math.min((HORA_FIM - HORA_INICIO) * 60, minutosDesdeInicio(r.dataFimPrevista))
                      const height = Math.max(30, ((fim - minutosDesdeInicio(r.dataInicio)) / 60) * HORA_PX - 2)
                      return (
                        <button
                          key={r.id}
                          type="button"
                          onClick={() => onReservaClick(r.id)}
                          className={`absolute inset-x-1 z-[5] overflow-hidden rounded-md p-1.5 text-left text-white shadow-sm transition hover:brightness-110 ${
                            STATUS_BG[r.status] ?? 'bg-slate-500/90'
                          } ${r.prontaParaCheckin ? 'ring-2 ring-emerald-300' : ''}`}
                          style={{ top, height }}
                        >
                          <p className="truncate text-[11px] font-semibold leading-tight">
                            {hhmm(r.dataInicio)} {r.clienteNome ?? '—'}
                          </p>
                          {height >= 44 && (
                            <div className="mt-0.5 flex items-center gap-1">
                              <Prontidao r={r} clara />
                            </div>
                          )}
                        </button>
                      )
                    })}
                  </div>
                </div>
              )
            })}
          </div>
        </div>
      </div>
    </TooltipProvider>
  )
}
