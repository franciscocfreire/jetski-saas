'use client'

import { useState } from 'react'
import { useQuery, useMutation } from '@tanstack/react-query'
import { Loader2 } from 'lucide-react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { useTenantStore } from '@/lib/store/tenant-store'
import { modelosService, reservasService } from '@/lib/api/services'
import type { Atendimento } from '../types'
import type { Modelo, Reserva } from '@/lib/api/types'

const DURACOES = [1, 2, 3, 4]

export function StepAluguel({
  atendimento,
  onBack,
  onDone,
}: {
  atendimento: Atendimento
  onBack: () => void
  onDone: (reserva: Reserva, modelo: Modelo) => void
}) {
  const { currentTenant } = useTenantStore()

  // Rascunho deste atendimento → semeia modelo/duração (a seleção não "some" ao
  // voltar). Modelo/duração continuam editáveis enquanto é rascunho.
  const reservaExistente = atendimento.reserva
  const horasIniciais = reservaExistente
    ? Math.max(
        1,
        Math.round(
          (new Date(reservaExistente.dataFimPrevista).getTime() -
            new Date(reservaExistente.dataInicio).getTime()) /
            3_600_000
        )
      )
    : 1

  const [modeloId, setModeloId] = useState(
    reservaExistente?.modeloId ?? atendimento.modelo?.id ?? ''
  )
  const [horas, setHoras] = useState(horasIniciais)

  const { data: modelos, isLoading } = useQuery({
    queryKey: ['modelos', currentTenant?.id],
    queryFn: () => modelosService.list(),
    enabled: !!currentTenant,
  })

  const modelo = modelos?.find((m) => m.id === modeloId) ?? atendimento.modelo
  const valorIlustrativo = modelo ? modelo.precoBaseHora * horas : 0

  const salvar = useMutation({
    mutationFn: async (): Promise<Reserva> => {
      // O horário real é definido no embarque (fila). Aqui só um início provisório.
      const inicio = reservaExistente
        ? new Date(reservaExistente.dataInicio)
        : new Date(Date.now() + 30 * 60_000)
      const fim = new Date(inicio.getTime() + horas * 3600_000)

      // NÃO há cobrança do passeio aqui — o passeio é pago no fim da locação.
      // A reserva nasce como RASCUNHO (a única taxa, a GRU, vem na emissão da CHA).
      if (!reservaExistente) {
        return reservasService.criarRascunho({
          modeloId,
          clienteId: atendimento.cliente!.id,
          dataInicio: inicio.toISOString(),
          dataFimPrevista: fim.toISOString(),
          prioridade: 'BAIXA',
        })
      }
      // Já existe rascunho → atualiza modelo/duração só se mudou.
      const mudouModelo = modeloId !== reservaExistente.modeloId
      const mudouDuracao = horas !== horasIniciais
      if (mudouModelo || mudouDuracao) {
        return reservasService.atualizar(reservaExistente.id, {
          modeloId,
          dataFimPrevista: fim.toISOString(),
        })
      }
      return reservaExistente
    },
    onSuccess: (reserva) => onDone(reserva, modelo!),
    onError: (e: unknown) => {
      const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
      toast.error(msg ?? 'Falha ao salvar o passeio.')
    },
  })

  return (
    <div className="space-y-5">
      <div className="grid gap-4 sm:grid-cols-2">
        <div>
          <Label className="text-xs">Modelo</Label>
          {isLoading ? (
            <div className="flex h-9 items-center text-sm text-muted-foreground">
              <Loader2 className="mr-2 h-4 w-4 animate-spin" /> Carregando…
            </div>
          ) : (
            <Select value={modeloId} onValueChange={setModeloId}>
              <SelectTrigger>
                <SelectValue placeholder="Selecione o modelo" />
              </SelectTrigger>
              <SelectContent>
                {(modelos ?? [])
                  .filter((m) => m.ativo)
                  .map((m) => (
                    <SelectItem key={m.id} value={m.id}>
                      {m.nome} — R$ {m.precoBaseHora.toFixed(2)}/h
                    </SelectItem>
                  ))}
              </SelectContent>
            </Select>
          )}
        </div>
        <div>
          <Label className="text-xs">Duração</Label>
          <Select value={String(horas)} onValueChange={(v) => setHoras(Number(v))}>
            <SelectTrigger>
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {DURACOES.map((h) => (
                <SelectItem key={h} value={String(h)}>
                  {h} hora{h > 1 ? 's' : ''}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
      </div>

      <div className="flex items-center justify-between rounded-lg border bg-muted/30 p-4">
        <div>
          <span className="text-sm text-muted-foreground">Valor do passeio (ilustrativo)</span>
          <p className="text-[11px] text-muted-foreground">
            Cobrado no fim da locação — não é pago agora.
          </p>
        </div>
        <span className="text-2xl font-bold">R$ {valorIlustrativo.toFixed(2)}</span>
      </div>

      <div className="flex justify-between">
        <Button type="button" variant="outline" onClick={onBack}>
          Voltar
        </Button>
        <Button type="button" disabled={!modelo || salvar.isPending} onClick={() => salvar.mutate()}>
          {salvar.isPending ? 'Salvando…' : 'Continuar'}
        </Button>
      </div>
    </div>
  )
}
