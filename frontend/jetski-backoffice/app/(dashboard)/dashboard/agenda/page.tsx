'use client'

import { useMemo, useState } from 'react'
import { useRouter } from 'next/navigation'
import { useQuery } from '@tanstack/react-query'
import { Calendar, ChevronLeft, ChevronRight, Plus } from 'lucide-react'
import { useTenantStore } from '@/lib/store/tenant-store'
import { reservasService, clientesService, modelosService, jetskisService } from '@/lib/api/services'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { ReservaDetailSheet } from '@/components/agenda/reserva-detail-sheet'
import { AgendaGradeDia } from '@/components/agenda/agenda-grade-dia'
import { AgendaSemana } from '@/components/agenda/agenda-semana'
import { formatCurrency } from '@/lib/utils'
import type { Reserva, ReservaStatus } from '@/lib/api/types'

const statusConfig: Record<ReservaStatus, { label: string; color: string }> = {
  RASCUNHO: { label: 'Rascunho', color: 'bg-slate-400' },
  PENDENTE: { label: 'Pendente', color: 'bg-yellow-500' },
  CONFIRMADA: { label: 'Confirmada', color: 'bg-green-500' },
  CANCELADA: { label: 'Cancelada', color: 'bg-red-500' },
  FINALIZADA: { label: 'Finalizada', color: 'bg-gray-500' },
  EXPIRADA: { label: 'Expirada', color: 'bg-warning' },
  NO_SHOW: { label: 'Não compareceu', color: 'bg-slate-500' },
}

const ymd = (d: Date) =>
  `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`

const horaDe = (iso: string) =>
  new Date(iso).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })

export default function AgendaPage() {
  const { currentTenant } = useTenantStore()
  const router = useRouter()
  const [view, setView] = useState<'dia' | 'semana' | 'mes'>('dia')
  const [currentDate, setCurrentDate] = useState(new Date())
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

  // O ReservaResponse traz só IDs — enriquecemos com nome/origem/modelo/jetski no cliente.
  const enriched = useMemo<Reserva[]>(() => {
    if (!reservas) return []
    const cMap = new Map((clientes ?? []).map((c) => [c.id, c]))
    const mMap = new Map((modelos ?? []).map((m) => [m.id, m]))
    const jMap = new Map((jetskis ?? []).map((j) => [j.id, j]))
    return reservas.map((r) => ({
      ...r,
      cliente: cMap.get(r.clienteId),
      modelo: mMap.get(r.modeloId),
      jetski: r.jetskiId ? jMap.get(r.jetskiId) : undefined,
    }))
  }, [reservas, clientes, modelos, jetskis])

  const abrir = (r: Reserva) => {
    setDetail(r)
    setSheetOpen(true)
  }

  // Grade do dia: reservas com PRONTIDÃO (pagamento/habilitação/termo) em lote
  const { data: agendaDia, isLoading: agendaLoading } = useQuery({
    queryKey: ['agenda-dia', currentTenant?.id, ymd(currentDate)],
    queryFn: () => reservasService.agendaDoDia(ymd(currentDate)),
    enabled: !!currentTenant && view === 'dia',
  })

  const jetskisComModelo = useMemo(() => {
    const mMap = new Map((modelos ?? []).map((m) => [m.id, m.nome]))
    return (jetskis ?? [])
      .filter((j) => j.ativo !== false)
      .map((j) => ({ ...j, modeloNome: mMap.get(j.modeloId) }))
  }, [jetskis, modelos])

  const abrirPorId = (id: string) => {
    const r = enriched.find((x) => x.id === id)
    if (r) abrir(r)
  }

  // Semana: segunda-feira da semana da data corrente
  const segunda = useMemo(() => {
    const d = new Date(currentDate)
    const dow = (d.getDay() + 6) % 7 // 0 = segunda
    d.setDate(d.getDate() - dow)
    return d
  }, [currentDate])
  const domingo = useMemo(
    () => new Date(segunda.getFullYear(), segunda.getMonth(), segunda.getDate() + 6),
    [segunda]
  )
  const { data: agendaSemana, isLoading: semanaLoading } = useQuery({
    queryKey: ['agenda-semana', currentTenant?.id, ymd(segunda)],
    queryFn: () => reservasService.agendaDoDia(ymd(segunda), ymd(domingo)),
    enabled: !!currentTenant && view === 'semana',
  })

  // Canceladas/expiradas/no-show são ruído na operação do dia: fora da grade,
  // da faixa "aguardando alocação" e do resumo (continuam no módulo Reservas).
  const agendaDiaAtivas = useMemo(
    () => (agendaDia ?? []).filter((r) => !['CANCELADA', 'EXPIRADA', 'NO_SHOW'].includes(r.status)),
    [agendaDia]
  )
  const agendaSemanaAtivas = useMemo(
    () => (agendaSemana ?? []).filter((r) => !['CANCELADA', 'EXPIRADA', 'NO_SHOW'].includes(r.status)),
    [agendaSemana]
  )

  // Resumo do dia — a conta do "aceito walk-in?" feita de graça
  const resumo = useMemo(() => {
    const ativas = agendaDiaAtivas
    const precoHora = new Map((modelos ?? []).map((m) => [m.id, m.precoBaseHora]))
    const horasReservadas = ativas.reduce((acc, r) => {
      const h = (new Date(r.dataFimPrevista).getTime() - new Date(r.dataInicio).getTime()) / 3_600_000
      return acc + Math.max(0, h)
    }, 0)
    const jetsDisponiveis = jetskisComModelo.filter((j) => j.status !== 'MANUTENCAO').length
    return {
      total: ativas.length,
      prontas: ativas.filter((r) => r.prontaParaCheckin).length,
      ocupacao: jetsDisponiveis > 0 ? Math.round((horasReservadas / (jetsDisponiveis * 13)) * 100) : 0,
      previsto: ativas.reduce((acc, r) => {
        const h = (new Date(r.dataFimPrevista).getTime() - new Date(r.dataInicio).getTime()) / 3_600_000
        return acc + (precoHora.get(r.modeloId ?? '') ?? 0) * Math.max(0, h)
      }, 0),
      recebido: ativas.reduce((acc, r) => acc + (r.valorTotal ?? 0), 0),
    }
  }, [agendaDiaAtivas, modelos, jetskisComModelo])

  // Slot livre clicado → balcão com modelo/horário pré-preenchidos
  const novaReservaNoSlot = (jet: { modeloId: string }, hora: number) => {
    const inicio = `${ymd(currentDate)}T${String(hora).padStart(2, '0')}:00:00`
    router.push(`/dashboard/balcao?modeloId=${jet.modeloId}&inicio=${inicio}`)
  }

  const stepDay = (delta: number) =>
    setCurrentDate(new Date(currentDate.getFullYear(), currentDate.getMonth(), currentDate.getDate() + delta))
  const stepMonth = (delta: number) =>
    setCurrentDate(new Date(currentDate.getFullYear(), currentDate.getMonth() + delta, 1))
  const goToday = () => setCurrentDate(new Date())

  if (!currentTenant) {
    return (
      <div className="flex h-[50vh] items-center justify-center">
        <p className="text-muted-foreground">Selecione um tenant para continuar</p>
      </div>
    )
  }

  const dayLabel = currentDate.toLocaleDateString('pt-BR', {
    weekday: 'long',
    day: '2-digit',
    month: 'long',
  })
  const monthName = currentDate.toLocaleDateString('pt-BR', { month: 'long', year: 'numeric' })
  const semanaLabel = `${segunda.getDate()} ${segunda.toLocaleDateString('pt-BR', { month: 'short' })} – ${domingo.getDate()} ${domingo.toLocaleDateString('pt-BR', { month: 'short' })}`

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold">Agenda</h1>
          <p className="text-muted-foreground">Reservas do dia, por horário</p>
        </div>
        <Button onClick={() => router.push('/dashboard/balcao')}>
          <Plus className="mr-2 h-4 w-4" />
          Nova Reserva
        </Button>
      </div>

      {/* Toolbar */}
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-3">
          <Button variant="outline" size="icon" onClick={() => (view === 'dia' ? stepDay(-1) : view === 'semana' ? stepDay(-7) : stepMonth(-1))}>
            <ChevronLeft className="h-4 w-4" />
          </Button>
          <h2 className="flex-1 truncate text-center text-base font-semibold capitalize sm:min-w-[14rem] sm:flex-none sm:text-lg">
            {view === 'dia' ? dayLabel : view === 'semana' ? semanaLabel : monthName}
          </h2>
          <Button variant="outline" size="icon" onClick={() => (view === 'dia' ? stepDay(1) : view === 'semana' ? stepDay(7) : stepMonth(1))}>
            <ChevronRight className="h-4 w-4" />
          </Button>
          <Button variant="outline" onClick={goToday}>
            Hoje
          </Button>
        </div>
        <div className="inline-flex rounded-md border p-0.5">
          <Button size="sm" variant={view === 'dia' ? 'default' : 'ghost'} onClick={() => setView('dia')}>
            Dia
          </Button>
          <Button size="sm" variant={view === 'semana' ? 'default' : 'ghost'} onClick={() => setView('semana')}>
            Semana
          </Button>
          <Button size="sm" variant={view === 'mes' ? 'default' : 'ghost'} onClick={() => setView('mes')}>
            Mês
          </Button>
        </div>
      </div>

      {view === 'dia' && !agendaLoading && agendaDiaAtivas.length > 0 && (
        <div className="flex flex-wrap items-center gap-x-5 gap-y-1 rounded-lg border bg-muted/30 px-4 py-2 text-sm">
          <span><b className="tabular-nums">{resumo.total}</b> reserva{resumo.total !== 1 ? 's' : ''}</span>
          <span title="Pagamento + habilitação + termo OK" className="text-emerald-700 dark:text-emerald-400">
            <b className="tabular-nums">{resumo.prontas}</b> pronta{resumo.prontas !== 1 ? 's' : ''}
          </span>
          <span title="Horas reservadas ÷ (jets disponíveis × 13h da janela 07–20)">
            ocupação <b className="tabular-nums">{resumo.ocupacao}%</b>
          </span>
          <span title="Preço-hora do modelo × duração (estimativa)">
            previsto <b className="tabular-nums">{formatCurrency(resumo.previsto)}</b>
          </span>
          <span title="Pagamentos integrais confirmados">
            recebido <b className="tabular-nums">{formatCurrency(resumo.recebido)}</b>
          </span>
        </div>
      )}

      {view === 'dia' ? (
        agendaLoading ? (
          <p className="py-10 text-center text-muted-foreground">Carregando…</p>
        ) : agendaDiaAtivas.length === 0 ? (
          <div className="rounded-xl border py-16 text-center">
            <Calendar className="mx-auto h-12 w-12 text-muted-foreground" />
            <p className="mt-4 font-medium">Nenhuma reserva — 100% de disponibilidade</p>
            <p className="mt-1 text-sm text-muted-foreground">
              Boa hora para chamar a fila de espera ou aceitar walk-ins.
            </p>
          </div>
        ) : (
          <AgendaGradeDia
            reservas={agendaDiaAtivas}
            jetskis={jetskisComModelo}
            dataEhHoje={ymd(currentDate) === ymd(new Date())}
            onReservaClick={abrirPorId}
            onSlotClick={novaReservaNoSlot}
          />
        )
      ) : view === 'semana' ? (
        semanaLoading ? (
          <p className="py-10 text-center text-muted-foreground">Carregando…</p>
        ) : (
          <AgendaSemana
            segunda={segunda}
            reservas={agendaSemanaAtivas}
            onPickDay={(d) => {
              setCurrentDate(d)
              setView('dia')
            }}
            onReservaClick={abrirPorId}
          />
        )
      ) : (
        <MonthView
          currentDate={currentDate}
          reservas={enriched}
          onPickDay={(d) => {
            setCurrentDate(d)
            setView('dia')
          }}
          onSelect={abrir}
        />
      )}

      <ReservaDetailSheet reserva={detail} open={sheetOpen} onOpenChange={setSheetOpen} />
    </div>
  )
}

function MonthView({
  currentDate,
  reservas,
  onPickDay,
  onSelect,
}: {
  currentDate: Date
  reservas: Reserva[]
  onPickDay: (d: Date) => void
  onSelect: (r: Reserva) => void
}) {
  const weekDays = ['Dom', 'Seg', 'Ter', 'Qua', 'Qui', 'Sex', 'Sáb']
  const firstDay = new Date(currentDate.getFullYear(), currentDate.getMonth(), 1)
  const daysInMonth = new Date(currentDate.getFullYear(), currentDate.getMonth() + 1, 0).getDate()
  const cells: (number | null)[] = []
  for (let i = 0; i < firstDay.getDay(); i++) cells.push(null)
  for (let d = 1; d <= daysInMonth; d++) cells.push(d)

  const today = new Date()
  const isToday = (day: number) =>
    day === today.getDate() &&
    currentDate.getMonth() === today.getMonth() &&
    currentDate.getFullYear() === today.getFullYear()

  const forDay = (day: number) => {
    const key = ymd(new Date(currentDate.getFullYear(), currentDate.getMonth(), day))
    return reservas.filter((r) => r.status !== 'RASCUNHO' && r.dataInicio.startsWith(key))
  }

  return (
    <div className="overflow-x-auto rounded-lg border">
      <div className="grid min-w-[560px] grid-cols-7 border-b">
        {weekDays.map((d) => (
          <div key={d} className="p-3 text-center text-sm font-medium text-muted-foreground">
            {d}
          </div>
        ))}
      </div>
      <div className="grid min-w-[560px] grid-cols-7">
        {cells.map((day, i) => {
          const list = day ? forDay(day) : []
          return (
            <div
              key={i}
              className={`min-h-[120px] border-b border-r p-2 ${day ? 'bg-card' : 'bg-muted/20'} ${
                i % 7 === 6 ? 'border-r-0' : ''
              }`}
            >
              {day && (
                <>
                  <button
                    onClick={() => onPickDay(new Date(currentDate.getFullYear(), currentDate.getMonth(), day))}
                    className={`text-sm font-medium ${
                      isToday(day)
                        ? 'flex h-7 w-7 items-center justify-center rounded-full bg-primary text-primary-foreground'
                        : 'hover:underline'
                    }`}
                  >
                    {day}
                  </button>
                  <div className="mt-1 space-y-1">
                    {list.slice(0, 3).map((r) => (
                      <button
                        key={r.id}
                        onClick={() => onSelect(r)}
                        className={`block w-full truncate rounded px-1.5 py-0.5 text-left text-xs text-white ${statusConfig[r.status].color}`}
                      >
                        {horaDe(r.dataInicio)} {r.cliente?.nome || 'Reserva'}
                      </button>
                    ))}
                    {list.length > 3 && (
                      <div className="text-xs text-muted-foreground">+{list.length - 3} mais</div>
                    )}
                  </div>
                </>
              )}
            </div>
          )
        })}
      </div>
    </div>
  )
}
