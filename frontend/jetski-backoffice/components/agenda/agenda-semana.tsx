'use client'

import type { AgendaReserva } from '@/lib/api/types'

const STATUS_BG: Record<string, string> = {
  PENDENTE: 'bg-amber-500/90',
  CONFIRMADA: 'bg-emerald-600/90',
  EM_ANDAMENTO: 'bg-blue-600/90',
  CONCLUIDA: 'bg-slate-400/90',
  CANCELADA: 'bg-red-400/80',
  EXPIRADA: 'bg-slate-400/80',
  NO_SHOW: 'bg-slate-500/80',
}

const ymd = (d: Date) =>
  `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`

/**
 * Visão SEMANA: planejamento entre o Dia (operação) e o Mês (panorama).
 * 7 colunas seg–dom com blocos compactos; dot verde = pronta para embarcar.
 */
export function AgendaSemana({ segunda, reservas, onPickDay, onReservaClick }: {
  /** Segunda-feira da semana exibida. */
  segunda: Date
  reservas: AgendaReserva[]
  onPickDay: (d: Date) => void
  onReservaClick: (id: string) => void
}) {
  const dias = Array.from({ length: 7 }, (_, i) =>
    new Date(segunda.getFullYear(), segunda.getMonth(), segunda.getDate() + i))
  const hoje = ymd(new Date())
  const porDia = new Map<string, AgendaReserva[]>()
  for (const r of reservas) {
    const key = r.dataInicio.slice(0, 10)
    porDia.set(key, [...(porDia.get(key) ?? []), r])
  }

  return (
    <div className="overflow-x-auto rounded-lg border">
      <div className="grid min-w-[840px] grid-cols-7 divide-x">
        {dias.map((d) => {
          const key = ymd(d)
          const doDia = (porDia.get(key) ?? [])
            .sort((a, b) => a.dataInicio.localeCompare(b.dataInicio))
          const ehHoje = key === hoje
          return (
            <div key={key} className="flex min-h-[300px] flex-col">
              <button
                type="button"
                onClick={() => onPickDay(d)}
                title="Abrir o dia na grade"
                className={`border-b px-2 py-2 text-left transition hover:bg-accent/50 ${
                  ehHoje ? 'bg-primary/5' : 'bg-muted/40'
                }`}
              >
                <p className={`text-xs font-semibold capitalize ${ehHoje ? 'text-primary' : ''}`}>
                  {d.toLocaleDateString('pt-BR', { weekday: 'short' })}{' '}
                  <span className="tabular-nums">{d.getDate()}</span>
                </p>
                <p className="text-[10px] text-muted-foreground">
                  {doDia.length === 0 ? 'livre' : `${doDia.length} reserva${doDia.length > 1 ? 's' : ''}`}
                </p>
              </button>
              <div className="flex flex-1 flex-col gap-1 p-1.5">
                {doDia.map((r) => (
                  <button
                    key={r.id}
                    type="button"
                    onClick={() => onReservaClick(r.id)}
                    className={`flex items-center gap-1.5 rounded px-1.5 py-1 text-left text-[11px] text-white transition hover:brightness-110 ${
                      STATUS_BG[r.status] ?? 'bg-slate-500/90'
                    }`}
                  >
                    <span
                      className={`h-1.5 w-1.5 shrink-0 rounded-full ${
                        r.prontaParaCheckin ? 'bg-emerald-300' : 'bg-white/40'
                      }`}
                      title={r.prontaParaCheckin ? 'Pronta para embarcar' : 'Pendências abertas'}
                    />
                    <span className="font-mono">
                      {new Date(r.dataInicio).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })}
                    </span>
                    <span className="truncate">{r.clienteNome ?? '—'}</span>
                  </button>
                ))}
              </div>
            </div>
          )
        })}
      </div>
    </div>
  )
}
