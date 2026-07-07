'use client'

import Link from 'next/link'
import { useQuery } from '@tanstack/react-query'
import {
  CalendarRange,
  ExternalLink,
  Landmark,
  Mail,
  Phone,
  ShieldCheck,
  User,
} from 'lucide-react'
import { reservasService } from '@/lib/api/services'
import { GruCicloTimeline, montarPassosCiclo } from '@/components/grus/gru-ciclo-timeline'
import { Button } from '@/components/ui/button'
import { formatCurrency } from '@/lib/utils'
import type { Gru } from '@/lib/api/types'
import { Badge } from '@/components/ui/badge'
import { Separator } from '@/components/ui/separator'
import { Skeleton } from '@/components/ui/skeleton'
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from '@/components/ui/sheet'

/**
 * Painel READ-ONLY da reserva por trás de uma GRU: contexto (cliente/passeio)
 * + timeline do ciclo Marinha. Consulta, não operação — ações continuam na
 * Agenda (detalhe da reserva).
 */
export function GruReservaSheet({ gru, open, onOpenChange }: {
  gru: Gru | null
  open: boolean
  onOpenChange: (open: boolean) => void
}) {
  const { data: reserva, isLoading } = useQuery({
    queryKey: ['reserva', gru?.reservaId],
    queryFn: () => reservasService.getById(gru!.reservaId),
    enabled: open && !!gru,
  })

  if (!gru) return null

  const fmt = (d?: string) =>
    d ? new Date(d).toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' }) : null
  const fmtHora = (d?: string) =>
    d ? new Date(d).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' }) : ''

  const statusLabel: Record<string, string> = {
    RASCUNHO: 'Rascunho', PENDENTE: 'Pendente', CONFIRMADA: 'Confirmada',
    EM_ANDAMENTO: 'Em andamento', CONCLUIDA: 'Concluída', CANCELADA: 'Cancelada',
    EXPIRADA: 'Expirada', NO_SHOW: 'No-show',
  }

  const passos = montarPassosCiclo(gru, formatCurrency)

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent className="w-full overflow-y-auto sm:max-w-md">
        <SheetHeader>
          <SheetTitle className="flex flex-wrap items-center gap-2">
            Reserva{' '}
            <span className="font-mono text-base uppercase">
              #{gru.reservaId.slice(0, 8)}
            </span>
            {reserva && (
              <>
                <Badge variant="secondary">{statusLabel[reserva.status] ?? reserva.status}</Badge>
                <Badge variant={reserva.canal === 'PORTAL' ? 'default' : 'outline'}>
                  {reserva.canal === 'PORTAL' ? 'Portal' : 'Balcão'}
                </Badge>
              </>
            )}
          </SheetTitle>
          <SheetDescription>
            Visão de consulta — para operar a reserva, use a Agenda.
          </SheetDescription>
        </SheetHeader>

        {isLoading ? (
          <div className="mt-6 space-y-3">
            <Skeleton className="h-6 w-2/3" />
            <Skeleton className="h-6 w-1/2" />
            <Skeleton className="h-24 w-full" />
          </div>
        ) : (
          <div className="mt-6 space-y-4">
            {/* Cliente */}
            <div className="space-y-1.5">
              <h4 className="flex items-center gap-2 text-sm font-medium">
                <User size={15} /> Cliente
              </h4>
              <p className="text-sm font-semibold">{reserva?.cliente?.nome ?? gru.clienteNome ?? '—'}</p>
              {(reserva?.cliente?.telefone || reserva?.cliente?.whatsapp) && (
                <p className="flex items-center gap-1.5 text-xs text-muted-foreground">
                  <Phone size={12} /> {reserva.cliente.whatsapp || reserva.cliente.telefone}
                </p>
              )}
              {reserva?.cliente?.email && (
                <p className="flex items-center gap-1.5 text-xs text-muted-foreground">
                  <Mail size={12} /> {reserva.cliente.email}
                </p>
              )}
            </div>

            <Separator />

            {/* Passeio */}
            <div className="space-y-1.5">
              <h4 className="flex items-center gap-2 text-sm font-medium">
                <CalendarRange size={15} /> Passeio
              </h4>
              <p className="text-sm">
                {reserva?.modelo?.nome ?? 'Modelo —'}
                {reserva?.jetski?.serie ? ` · ${reserva.jetski.serie}` : ''}
              </p>
              {reserva && (
                <p className="text-xs text-muted-foreground">
                  {new Date(reserva.dataInicio).toLocaleDateString('pt-BR', {
                    weekday: 'short', day: '2-digit', month: '2-digit', year: 'numeric',
                  })}{' '}
                  · {fmtHora(reserva.dataInicio)}–{fmtHora(reserva.dataFimPrevista)}
                </p>
              )}
              {reserva?.valorTotal != null && (
                <p className="text-xs text-muted-foreground">
                  Valor da reserva: <b>{formatCurrency(reserva.valorTotal)}</b>
                </p>
              )}
              {reserva?.observacoes && (
                <p className="rounded-md bg-muted px-2 py-1.5 text-xs text-muted-foreground">
                  {reserva.observacoes}
                </p>
              )}
            </div>

            <Separator />

            {/* Ciclo da GRU — timeline vertical */}
            <div className="space-y-1.5">
              <h4 className="flex items-center gap-2 text-sm font-medium">
                <Landmark size={15} /> Ciclo da GRU
              </h4>
              <GruCicloTimeline passos={passos} />
              {gru.marinhaConfirmadaEm && (
                <p className="flex items-center gap-1.5 rounded-md bg-emerald-50 px-2 py-1.5 text-xs text-emerald-700">
                  <ShieldCheck size={13} className="shrink-0" />
                  Habilitação temporária confirmada — reusável pelo cliente por 30 dias da emissão.
                </p>
              )}
            </div>

            <Button variant="outline" className="w-full" asChild>
              <Link
                href={`/dashboard/reservas/${gru.reservaId}`}
                onClick={() => onOpenChange(false)}
              >
                <ExternalLink size={14} className="mr-2" />
                Abrir página completa da reserva
              </Link>
            </Button>
          </div>
        )}
      </SheetContent>
    </Sheet>
  )
}
