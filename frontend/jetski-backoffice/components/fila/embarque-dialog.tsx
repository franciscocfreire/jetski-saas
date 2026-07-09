'use client'

import { useState } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { toast } from 'sonner'
import { Anchor, AlertTriangle, Loader2 } from 'lucide-react'
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
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
import { toLocalDateTime } from '@/lib/utils'

/**
 * Diálogo de embarque (check-in) a partir da fila: aloca o jetski escolhido e
 * cria a locação. Idempotente (confirma se PENDENTE; pula etapas já feitas).
 */
export function EmbarqueDialog({
  reservaId,
  modeloId,
  open,
  onOpenChange,
  onEmbarcado,
}: {
  reservaId: string
  modeloId?: string
  open: boolean
  onOpenChange: (v: boolean) => void
  onEmbarcado?: () => void
}) {
  const { currentTenant } = useTenantStore()
  const [jetskiId, setJetskiId] = useState('')
  const [horimetro, setHorimetro] = useState('')
  // Horário da saída — default agora; editável p/ registrar embarque retroativo
  // (o operador anota no papel e digita depois)
  const [horaSaida, setHoraSaida] = useState(() =>
    new Date().toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })
  )

  const { data: jetskis } = useQuery({
    queryKey: ['jetskis-disponiveis', currentTenant?.id],
    queryFn: () => jetskisService.list({ status: 'DISPONIVEL' }),
    enabled: open && !!currentTenant,
  })
  const disponiveis = (jetskis ?? []).filter((j) => j.modeloId === modeloId)

  // Pagamento é antes do uso — alerta (sem bloquear) se ainda não registrado.
  const { data: reserva } = useQuery({
    queryKey: ['reserva-embarque', reservaId],
    queryFn: () => reservasService.getById(reservaId),
    enabled: open && !!reservaId,
  })
  const semPagamento = !!reserva && reserva.pagamentoStatus !== 'CONFIRMADO'

  const escolher = (id: string) => {
    setJetskiId(id)
    const j = disponiveis.find((x) => x.id === id)
    if (j && !horimetro) setHorimetro(String(j.horimetroAtual ?? ''))
  }

  const checkIn = useMutation({
    mutationFn: async (): Promise<{ jaEmbarcado: boolean }> => {
      const r = await reservasService.getById(reservaId)
      if (r.status === 'FINALIZADA') return { jaEmbarcado: true }
      if (r.status === 'CANCELADA' || r.status === 'EXPIRADA') {
        throw new Error(`Reserva ${r.status.toLowerCase()} — não é possível embarcar.`)
      }
      if (r.status === 'PENDENTE') await reservasService.confirmar(reservaId)
      if (!r.jetskiId) await reservasService.alocarJetski(reservaId, jetskiId)
      const locacao = await locacoesService.checkInFromReserva({
        reservaId,
        horimetroInicio: Number(horimetro),
      })
      // Saída retroativa: o check-in nasce "agora"; se o operador escolheu outro
      // horário (hoje), corrigimos em seguida pelo endpoint auditado
      const [hh, mm] = horaSaida.split(':').map(Number)
      if (!Number.isNaN(hh) && !Number.isNaN(mm)) {
        const escolhido = new Date()
        escolhido.setHours(hh, mm, 0, 0)
        const drift = Math.abs(escolhido.getTime() - Date.now())
        if (drift > 60_000 && escolhido.getTime() <= Date.now()) {
          await locacoesService.updateDataCheckIn(locacao.id, toLocalDateTime(escolhido))
        }
      }
      return { jaEmbarcado: false }
    },
    onSuccess: ({ jaEmbarcado }) => {
      toast.success(jaEmbarcado ? 'Reserva já tinha check-in.' : 'Check-in concluído — locação criada.')
      onEmbarcado?.()
      onOpenChange(false)
    },
    onError: (e: unknown) => {
      const msg =
        (e as { response?: { data?: { message?: string } } })?.response?.data?.message ??
        (e as Error)?.message
      toast.error(msg ?? 'Falha no check-in.')
    },
  })

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Embarcar — check-in</DialogTitle>
        </DialogHeader>
        <div className="space-y-3">
          {semPagamento && (
            <div className="flex items-start gap-2 rounded-md border border-amber-300 bg-amber-50 p-3 text-sm text-amber-800 dark:bg-amber-950/30">
              <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />
              <span>
                <b>Pagamento não registrado.</b> O pagamento é feito antes do uso — combine o
                recebimento no balcão (ou registre na devolução).
              </span>
            </div>
          )}
          <div>
            <Label className="text-xs">Jetski</Label>
            <Select value={jetskiId} onValueChange={escolher}>
              <SelectTrigger>
                <SelectValue placeholder="Selecione o jetski disponível" />
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
                Sem jetski disponível para este modelo agora — o cliente segue na fila.
              </p>
            )}
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <Label className="text-xs">Horímetro inicial</Label>
              <Input
                type="number"
                step="0.1"
                value={horimetro}
                onChange={(e) => setHorimetro(e.target.value)}
              />
            </div>
            <div>
              <Label className="text-xs">Horário da saída</Label>
              <Input
                type="time"
                value={horaSaida}
                onChange={(e) => setHoraSaida(e.target.value)}
              />
              <p className="mt-1 text-[11px] text-muted-foreground">
                Ajuste se a saída já aconteceu (retroativo)
              </p>
            </div>
          </div>
        </div>
        <DialogFooter>
          <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
            Cancelar
          </Button>
          <Button
            type="button"
            disabled={!jetskiId || !horimetro || checkIn.isPending}
            onClick={() => checkIn.mutate()}
          >
            {checkIn.isPending ? (
              <Loader2 className="mr-2 h-4 w-4 animate-spin" />
            ) : (
              <Anchor className="mr-2 h-4 w-4" />
            )}
            Fazer check-in
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
