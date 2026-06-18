'use client'

import { useState } from 'react'
import { useQuery, useMutation } from '@tanstack/react-query'
import { Anchor, CalendarClock, Ship, CheckCircle2 } from 'lucide-react'
import Link from 'next/link'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { useTenantStore } from '@/lib/store/tenant-store'
import { jetskisService, reservasService, locacoesService } from '@/lib/api/services'
import type { Atendimento } from '../types'

/**
 * Desfecho do atendimento: "agendado" (mantém só a Reserva) ou "embarque agora"
 * (aloca jetski → confirma reserva → check-in → cria a Locação).
 */
export function EmbarqueSection({
  atendimento,
  onReset,
}: {
  atendimento: Atendimento
  onReset: () => void
}) {
  const { currentTenant } = useTenantStore()
  const [modo, setModo] = useState<'agendado' | 'embarque'>('agendado')
  const [jetskiId, setJetskiId] = useState('')
  const [horimetro, setHorimetro] = useState('')
  const [embarcado, setEmbarcado] = useState(false)

  const modeloId = atendimento.modelo?.id

  const { data: jetskis } = useQuery({
    queryKey: ['jetskis-disponiveis', currentTenant?.id, modeloId],
    queryFn: () => jetskisService.list({ status: 'DISPONIVEL' }),
    enabled: !!currentTenant && modo === 'embarque',
  })
  const disponiveis = (jetskis ?? []).filter((j) => j.modeloId === modeloId)

  const checkIn = useMutation({
    mutationFn: async () => {
      const reservaId = atendimento.reserva!.id
      await reservasService.alocarJetski(reservaId, jetskiId) // PENDENTE → aloca jetski
      await reservasService.confirmar(reservaId) // PENDENTE → CONFIRMADA
      return locacoesService.checkInFromReserva({
        reservaId,
        horimetroInicio: Number(horimetro),
      })
    },
    onSuccess: () => {
      setEmbarcado(true)
      toast.success('Check-in concluído — locação criada.')
    },
    onError: (e: unknown) => {
      const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
      toast.error(msg ?? 'Falha no check-in.')
    },
  })

  function escolherJetski(id: string) {
    setJetskiId(id)
    const j = disponiveis.find((x) => x.id === id)
    if (j && !horimetro) setHorimetro(String(j.horimetroAtual ?? ''))
  }

  if (embarcado) {
    return (
      <div className="space-y-4 rounded-lg border border-emerald-300 bg-emerald-50 p-4 dark:bg-emerald-950/30">
        <p className="flex items-center gap-2 font-medium text-emerald-700">
          <CheckCircle2 className="h-5 w-5" /> Locação criada (cliente embarcado).
        </p>
        <div className="flex gap-2">
          <Link href="/dashboard/locacoes">
            <Button type="button" variant="outline">
              Ver em Locações
            </Button>
          </Link>
          <Button type="button" onClick={onReset}>
            Novo atendimento
          </Button>
        </div>
      </div>
    )
  }

  return (
    <div className="space-y-4 rounded-lg border p-4">
      <Label className="text-sm font-medium">Desfecho do atendimento</Label>
      <div className="flex flex-wrap gap-2">
        <Button
          type="button"
          variant={modo === 'agendado' ? 'default' : 'outline'}
          size="sm"
          onClick={() => setModo('agendado')}
        >
          <CalendarClock size={14} className="mr-1" /> Agendado (só reserva)
        </Button>
        <Button
          type="button"
          variant={modo === 'embarque' ? 'default' : 'outline'}
          size="sm"
          onClick={() => setModo('embarque')}
        >
          <Ship size={14} className="mr-1" /> Embarque agora (check-in)
        </Button>
      </div>

      {modo === 'agendado' ? (
        <div className="flex items-center justify-between">
          <p className="text-xs text-muted-foreground">
            A reserva fica na Agenda; o check-in será feito no embarque.
          </p>
          <Button type="button" onClick={onReset}>
            Concluir atendimento
          </Button>
        </div>
      ) : (
        <div className="space-y-3">
          <div className="grid gap-3 sm:grid-cols-2">
            <div>
              <Label className="text-xs">Jetski</Label>
              <Select value={jetskiId} onValueChange={escolherJetski}>
                <SelectTrigger>
                  <SelectValue placeholder="Selecione o jetski" />
                </SelectTrigger>
                <SelectContent>
                  {disponiveis.map((j) => (
                    <SelectItem key={j.id} value={j.id}>
                      {j.serie} (horímetro {j.horimetroAtual})
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              {disponiveis.length === 0 && (
                <p className="mt-1 text-xs text-amber-600">
                  Sem jetski disponível para este modelo.
                </p>
              )}
            </div>
            <div>
              <Label className="text-xs">Horímetro inicial</Label>
              <Input
                type="number"
                step="0.1"
                value={horimetro}
                onChange={(e) => setHorimetro(e.target.value)}
              />
            </div>
          </div>
          <div className="flex justify-end">
            <Button
              type="button"
              disabled={!jetskiId || !horimetro || checkIn.isPending}
              onClick={() => checkIn.mutate()}
            >
              <Anchor size={15} className="mr-1" />
              {checkIn.isPending ? 'Processando…' : 'Fazer check-in e concluir'}
            </Button>
          </div>
        </div>
      )}
    </div>
  )
}
