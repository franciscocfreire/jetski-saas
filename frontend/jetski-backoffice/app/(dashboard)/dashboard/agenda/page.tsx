'use client'

import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import {
  Calendar,
  ChevronLeft,
  ChevronRight,
  Plus,
} from 'lucide-react'
import { useTenantStore } from '@/lib/store/tenant-store'
import { reservasService } from '@/lib/api/services'
import { formatDateTime } from '@/lib/utils'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import type { ReservaStatus } from '@/lib/api/types'

const statusConfig: Record<ReservaStatus, { label: string; color: string }> = {
  PENDENTE: { label: 'Pendente', color: 'bg-yellow-500' },
  CONFIRMADA: { label: 'Confirmada', color: 'bg-green-500' },
  CANCELADA: { label: 'Cancelada', color: 'bg-red-500' },
  CONCLUIDA: { label: 'Concluída', color: 'bg-gray-500' },
}

export default function AgendaPage() {
  const { currentTenant } = useTenantStore()

  const [currentDate, setCurrentDate] = useState(new Date())

  const { data: reservas, isLoading } = useQuery({
    queryKey: ['reservas', currentTenant?.id],
    queryFn: () => reservasService.list(),
    enabled: !!currentTenant,
  })

  const goToPreviousMonth = () => {
    setCurrentDate(new Date(currentDate.getFullYear(), currentDate.getMonth() - 1, 1))
  }

  const goToNextMonth = () => {
    setCurrentDate(new Date(currentDate.getFullYear(), currentDate.getMonth() + 1, 1))
  }

  const goToToday = () => {
    setCurrentDate(new Date())
  }

  const monthName = currentDate.toLocaleDateString('pt-BR', { month: 'long', year: 'numeric' })

  // Get days in month
  const firstDayOfMonth = new Date(currentDate.getFullYear(), currentDate.getMonth(), 1)
  const lastDayOfMonth = new Date(currentDate.getFullYear(), currentDate.getMonth() + 1, 0)
  const daysInMonth = lastDayOfMonth.getDate()
  const startingDayOfWeek = firstDayOfMonth.getDay()

  // Generate calendar days
  const calendarDays = []
  for (let i = 0; i < startingDayOfWeek; i++) {
    calendarDays.push(null)
  }
  for (let day = 1; day <= daysInMonth; day++) {
    calendarDays.push(day)
  }

  // Get reservations for a specific day
  const getReservationsForDay = (day: number) => {
    if (!reservas) return []
    const dateStr = `${currentDate.getFullYear()}-${String(currentDate.getMonth() + 1).padStart(2, '0')}-${String(day).padStart(2, '0')}`
    return reservas.filter(r => r.dataInicio.startsWith(dateStr))
  }

  const weekDays = ['Dom', 'Seg', 'Ter', 'Qua', 'Qui', 'Sex', 'Sáb']
  const today = new Date()
  const isToday = (day: number) =>
    day === today.getDate() &&
    currentDate.getMonth() === today.getMonth() &&
    currentDate.getFullYear() === today.getFullYear()

  if (!currentTenant) {
    return (
      <div className="flex h-[50vh] items-center justify-center">
        <p className="text-muted-foreground">Selecione um tenant para continuar</p>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold">Agenda</h1>
          <p className="text-muted-foreground">Visualize e gerencie as reservas</p>
        </div>
        <Button>
          <Plus className="mr-2 h-4 w-4" />
          Nova Reserva
        </Button>
      </div>

      {/* Calendar Header */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-4">
          <Button variant="outline" size="icon" onClick={goToPreviousMonth}>
            <ChevronLeft className="h-4 w-4" />
          </Button>
          <h2 className="text-xl font-semibold capitalize">{monthName}</h2>
          <Button variant="outline" size="icon" onClick={goToNextMonth}>
            <ChevronRight className="h-4 w-4" />
          </Button>
        </div>
        <Button variant="outline" onClick={goToToday}>
          Hoje
        </Button>
      </div>

      {/* Calendar Grid */}
      <div className="rounded-lg border">
        <div className="grid grid-cols-7 border-b">
          {weekDays.map((day) => (
            <div key={day} className="p-3 text-center text-sm font-medium text-muted-foreground">
              {day}
            </div>
          ))}
        </div>
        <div className="grid grid-cols-7">
          {calendarDays.map((day, index) => {
            const dayReservations = day ? getReservationsForDay(day) : []
            return (
              <div
                key={index}
                className={`min-h-[120px] border-b border-r p-2 ${
                  day ? 'bg-card hover:bg-accent/50 cursor-pointer' : 'bg-muted/20'
                } ${index % 7 === 6 ? 'border-r-0' : ''}`}
              >
                {day && (
                  <>
                    <div className={`text-sm font-medium ${isToday(day) ? 'flex h-7 w-7 items-center justify-center rounded-full bg-primary text-primary-foreground' : ''}`}>
                      {day}
                    </div>
                    <div className="mt-1 space-y-1">
                      {dayReservations.slice(0, 3).map((reserva) => (
                        <div
                          key={reserva.id}
                          className={`rounded px-1.5 py-0.5 text-xs text-white truncate ${statusConfig[reserva.status].color}`}
                        >
                          {reserva.cliente?.nome || 'Reserva'}
                        </div>
                      ))}
                      {dayReservations.length > 3 && (
                        <div className="text-xs text-muted-foreground">
                          +{dayReservations.length - 3} mais
                        </div>
                      )}
                    </div>
                  </>
                )}
              </div>
            )
          })}
        </div>
      </div>

      {/* Upcoming Reservations */}
      <div className="rounded-xl border bg-card p-6">
        <h3 className="text-lg font-semibold">Próximas Reservas</h3>
        <p className="text-sm text-muted-foreground">Reservas agendadas para os próximos dias</p>

        <div className="mt-4 space-y-4">
          {isLoading ? (
            <p className="text-center text-muted-foreground py-4">Carregando...</p>
          ) : reservas?.filter(r => r.status === 'PENDENTE' || r.status === 'CONFIRMADA').length === 0 ? (
            <div className="text-center py-8">
              <Calendar className="mx-auto h-12 w-12 text-muted-foreground" />
              <p className="mt-4 text-muted-foreground">Nenhuma reserva pendente</p>
            </div>
          ) : (
            reservas
              ?.filter(r => r.status === 'PENDENTE' || r.status === 'CONFIRMADA')
              .slice(0, 5)
              .map((reserva) => (
                <div key={reserva.id} className="flex items-center justify-between border-b pb-4 last:border-0">
                  <div>
                    <p className="font-medium">{reserva.cliente?.nome || 'Cliente não informado'}</p>
                    <p className="text-sm text-muted-foreground">
                      {reserva.modelo?.nome} - {formatDateTime(reserva.dataInicio)}
                    </p>
                  </div>
                  <div className="flex items-center gap-2">
                    <Badge variant={reserva.status === 'CONFIRMADA' ? 'success' : 'warning'}>
                      {statusConfig[reserva.status].label}
                    </Badge>
                    <Button variant="outline" size="sm">
                      Ver detalhes
                    </Button>
                  </div>
                </div>
              ))
          )}
        </div>
      </div>
    </div>
  )
}
