'use client'

import { useState } from 'react'
import { useQuery, useMutation } from '@tanstack/react-query'
import { Loader2 } from 'lucide-react'
import Link from 'next/link'
import { toast } from 'sonner'
import { toLocalDateTime } from '@/lib/utils'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import { Input } from '@/components/ui/input'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { useTenantStore } from '@/lib/store/tenant-store'
import { modelosService, reservasService } from '@/lib/api/services'
import { formatDuracao } from '@/lib/utils'
import type { Atendimento } from '../types'
import type { Modelo, Reserva } from '@/lib/api/types'

// Durações padrão (minutos) — agilidade do operador; o campo abaixo permite editar.
const PRESETS = [30, 60, 120, 180]

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
  const duracaoInicial = reservaExistente
    ? Math.max(
        5,
        Math.round(
          (new Date(reservaExistente.dataFimPrevista).getTime() -
            new Date(reservaExistente.dataInicio).getTime()) /
            60_000
        )
      )
    : 60

  const [modeloId, setModeloId] = useState(
    reservaExistente?.modeloId ?? atendimento.modelo?.id ?? atendimento.prefillModeloId ?? ''
  )
  const [duracaoMin, setDuracaoMin] = useState(duracaoInicial)

  const { data: modelos, isLoading } = useQuery({
    queryKey: ['modelos', currentTenant?.id],
    queryFn: () => modelosService.list(),
    enabled: !!currentTenant,
  })

  const modelo = modelos?.find((m) => m.id === modeloId) ?? atendimento.modelo
  const valorIlustrativo = modelo ? (modelo.precoBaseHora * duracaoMin) / 60 : 0

  // Slot clicado na Agenda: usa o horário escolhido em vez do provisório
  const prefillInicio = atendimento.prefillInicio
    ? new Date(atendimento.prefillInicio)
    : null

  const salvar = useMutation({
    mutationFn: async (): Promise<Reserva> => {
      // O horário real é definido no embarque (fila) — salvo quando veio do
      // slot da Agenda, que já traz o horário escolhido pelo operador.
      const inicio = reservaExistente
        ? new Date(reservaExistente.dataInicio)
        : prefillInicio ?? new Date(Date.now() + 30 * 60_000)
      const fim = new Date(inicio.getTime() + duracaoMin * 60_000)

      // NÃO há cobrança do passeio aqui — o passeio é pago no fim da locação.
      // A reserva nasce como RASCUNHO (a única taxa, a GRU, vem na emissão da CHA).
      if (!reservaExistente) {
        return reservasService.criarRascunho({
          modeloId,
          clienteId: atendimento.cliente!.id,
          dataInicio: toLocalDateTime(inicio),
          dataFimPrevista: toLocalDateTime(fim),
          prioridade: 'BAIXA',
        })
      }
      // Já existe rascunho → atualiza modelo/duração só se mudou.
      const mudouModelo = modeloId !== reservaExistente.modeloId
      const mudouDuracao = duracaoMin !== duracaoInicial
      if (mudouModelo || mudouDuracao) {
        return reservasService.atualizar(reservaExistente.id, {
          modeloId,
          dataFimPrevista: toLocalDateTime(fim),
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
        <Label className="text-xs">Duração do passeio</Label>
        <div className="mt-1 flex flex-wrap items-center gap-2">
          {PRESETS.map((m) => (
            <Button
              key={m}
              type="button"
              size="sm"
              variant={duracaoMin === m ? 'default' : 'outline'}
              onClick={() => setDuracaoMin(m)}
            >
              {formatDuracao(m)}
            </Button>
          ))}
          <span className="mx-1 text-xs text-muted-foreground">ou</span>
          <Input
            type="number"
            min={5}
            step={5}
            value={duracaoMin}
            onChange={(e) => setDuracaoMin(Math.max(5, Number(e.target.value) || 5))}
            className="h-9 w-20"
          />
          <span className="text-xs text-muted-foreground">min ({formatDuracao(duracaoMin)})</span>
        </div>
      </div>

      {prefillInicio && !reservaExistente && (
        <p className="flex items-center gap-1.5 rounded-md bg-primary/5 px-3 py-2 text-sm text-primary">
          Horário do slot escolhido na Agenda:{' '}
          <b>
            {prefillInicio.toLocaleDateString('pt-BR')}{' '}
            {prefillInicio.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })}
          </b>
        </p>
      )}

      <div className="flex flex-wrap items-center justify-between gap-2 rounded-lg border bg-muted/30 p-4">
        <div>
          <span className="text-sm text-muted-foreground">Valor do passeio (estimado)</span>
          <p className="text-[11px] text-muted-foreground">
            Pagamento integral no balcão, antes do embarque — registrado no passo Pagamento.
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
