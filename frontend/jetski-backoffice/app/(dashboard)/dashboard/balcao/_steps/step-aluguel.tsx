'use client'

import { useState } from 'react'
import { useQuery, useMutation } from '@tanstack/react-query'
import { Loader2 } from 'lucide-react'
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
import { modelosService, reservasService } from '@/lib/api/services'
import type { Atendimento } from '../types'
import type { Modelo, Reserva } from '@/lib/api/types'

const DURACOES = [1, 2, 3, 4]

/** Formata um Date como string aceita pelo input datetime-local (hora local). */
function toLocalInput(d: Date): string {
  const off = d.getTimezoneOffset() * 60_000
  return new Date(d.getTime() - off).toISOString().slice(0, 16)
}

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
  const [modeloId, setModeloId] = useState('')
  const [horas, setHoras] = useState(1)
  // Início padrão: agora + 30min (folga p/ a validação @Future e desvio de
  // relógio cliente↔servidor). O atendente pode ajustar (ex.: reserva adiantada).
  const [inicioLocal, setInicioLocal] = useState(() => toLocalInput(new Date(Date.now() + 30 * 60_000)))

  const { data: modelos, isLoading } = useQuery({
    queryKey: ['modelos', currentTenant?.id],
    queryFn: () => modelosService.list(),
    enabled: !!currentTenant,
  })

  const modelo = modelos?.find((m) => m.id === modeloId)
  const valorTotal = modelo ? modelo.precoBaseHora * horas : 0

  const criar = useMutation({
    mutationFn: async (): Promise<Reserva> => {
      const inicio = new Date(inicioLocal) // string local → Date (UTC correto)
      const fim = new Date(inicio.getTime() + horas * 3600_000)
      const reserva = await reservasService.create({
        modeloId,
        clienteId: atendimento.cliente!.id,
        dataInicio: inicio.toISOString(),
        dataFimPrevista: fim.toISOString(),
        prioridade: 'ALTA',
      })
      // Balcão: pagamento TOTAL imediato (sem sinal).
      return reservasService.confirmarPagamento(reserva.id, {
        tipo: 'TOTAL',
        valorPago: valorTotal,
      })
    },
    onSuccess: (reserva) => {
      toast.success('Reserva criada e pagamento registrado.')
      onDone(reserva, modelo!)
    },
    onError: (e: unknown) => {
      const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
      toast.error(msg ?? 'Falha ao criar reserva/pagamento.')
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
        <div>
          <Label className="text-xs">Início</Label>
          <Input
            type="datetime-local"
            value={inicioLocal}
            onChange={(e) => setInicioLocal(e.target.value)}
          />
          <p className="mt-1 text-xs text-muted-foreground">
            Padrão: agora + 30 min (ajuste p/ reserva adiantada).
          </p>
        </div>
        <div className="hidden sm:block" />
      </div>

      <div className="flex items-center justify-between rounded-lg border bg-muted/30 p-4">
        <span className="text-sm text-muted-foreground">Valor total (pagamento no balcão)</span>
        <span className="text-2xl font-bold">R$ {valorTotal.toFixed(2)}</span>
      </div>

      <div className="flex justify-between">
        <Button type="button" variant="outline" onClick={onBack}>
          Voltar
        </Button>
        <Button
          type="button"
          disabled={!modeloId || !inicioLocal || criar.isPending}
          onClick={() => criar.mutate()}
        >
          {criar.isPending ? 'Processando…' : 'Criar reserva e cobrar total'}
        </Button>
      </div>
    </div>
  )
}
