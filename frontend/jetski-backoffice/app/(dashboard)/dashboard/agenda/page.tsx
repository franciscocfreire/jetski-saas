'use client'

import { useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Calendar, ChevronLeft, ChevronRight, Plus } from 'lucide-react'
import { useTenantStore } from '@/lib/store/tenant-store'
import { reservasService, clientesService, modelosService, jetskisService } from '@/lib/api/services'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { ReservaDetailSheet } from '@/components/agenda/reserva-detail-sheet'
import type { Reserva, ReservaStatus } from '@/lib/api/types'

const statusConfig: Record<ReservaStatus, { label: string; color: string }> = {
  RASCUNHO: { label: 'Rascunho', color: 'bg-slate-400' },
  PENDENTE: { label: 'Pendente', color: 'bg-yellow-500' },
  CONFIRMADA: { label: 'Confirmada', color: 'bg-green-500' },
  CANCELADA: { label: 'Cancelada', color: 'bg-red-500' },
  FINALIZADA: { label: 'Finalizada', color: 'bg-gray-500' },
  EXPIRADA: { label: 'Expirada', color: 'bg-warning' },
}

const ymd = (d: Date) =>
  `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`

const horaDe = (iso: string) =>
  new Date(iso).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })

const origemLabel = (r: Reserva) => (r.cliente?.origem === 'PORTAL' ? 'Online' : 'Balcão')

export default function AgendaPage() {
  const { currentTenant } = useTenantStore()
  const [view, setView] = useState<'dia' | 'mes'>('dia')
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

  const reservasDoDia = useMemo(() => {
    const key = ymd(currentDate)
    return enriched
      .filter((r) => r.status !== 'RASCUNHO' && r.dataInicio.startsWith(key))
      .sort((a, b) => a.dataInicio.localeCompare(b.dataInicio))
  }, [enriched, currentDate])

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

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold">Agenda</h1>
          <p className="text-muted-foreground">Reservas do dia, por horário</p>
        </div>
        <Button>
          <Plus className="mr-2 h-4 w-4" />
          Nova Reserva
        </Button>
      </div>

      {/* Toolbar */}
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-3">
          <Button variant="outline" size="icon" onClick={() => (view === 'dia' ? stepDay(-1) : stepMonth(-1))}>
            <ChevronLeft className="h-4 w-4" />
          </Button>
          <h2 className="flex-1 truncate text-center text-base font-semibold capitalize sm:min-w-[14rem] sm:flex-none sm:text-lg">
            {view === 'dia' ? dayLabel : monthName}
          </h2>
          <Button variant="outline" size="icon" onClick={() => (view === 'dia' ? stepDay(1) : stepMonth(1))}>
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
          <Button size="sm" variant={view === 'mes' ? 'default' : 'ghost'} onClick={() => setView('mes')}>
            Mês
          </Button>
        </div>
      </div>

      {view === 'dia' ? (
        <DayView
          loading={isLoading}
          reservas={reservasDoDia}
          onSelect={abrir}
        />
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

function DayView({
  loading,
  reservas,
  onSelect,
}: {
  loading: boolean
  reservas: Reserva[]
  onSelect: (r: Reserva) => void
}) {
  if (loading) {
    return <p className="py-10 text-center text-muted-foreground">Carregando…</p>
  }
  if (reservas.length === 0) {
    return (
      <div className="rounded-xl border py-16 text-center">
        <Calendar className="mx-auto h-12 w-12 text-muted-foreground" />
        <p className="mt-4 text-muted-foreground">Nenhuma reserva para este dia</p>
      </div>
    )
  }
  return (
    <div className="divide-y rounded-xl border">
      {reservas.map((r) => (
        <button
          key={r.id}
          onClick={() => onSelect(r)}
          className="flex w-full items-center gap-4 px-4 py-3 text-left hover:bg-accent/50"
        >
          <div className="w-16 shrink-0 text-center">
            <div className="text-lg font-semibold tabular-nums">{horaDe(r.dataInicio)}</div>
            <div className="text-[11px] text-muted-foreground">{horaDe(r.dataFimPrevista)}</div>
          </div>
          <div className="min-w-0 flex-1">
            <p className="truncate font-medium">{r.cliente?.nome || 'Cliente não informado'}</p>
            <p className="truncate text-sm text-muted-foreground">{r.modelo?.nome}</p>
          </div>
          <div className="flex shrink-0 items-center gap-2">
            <Badge variant={r.cliente?.origem === 'PORTAL' ? 'default' : 'secondary'}>
              {origemLabel(r)}
            </Badge>
            <Badge variant={r.status === 'CONFIRMADA' ? 'success' : 'warning'}>
              {statusConfig[r.status].label}
            </Badge>
          </div>
        </button>
      ))}
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
