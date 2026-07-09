'use client'

import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { locacoesService } from '@/lib/api/services'
import type { Locacao, CheckOutRequest, FolioExtrato } from '@/lib/api/types'
import { formatDateTime, formatDuration } from '@/lib/utils'
import { PagamentoDialog } from '@/components/locacoes/pagamento-dialog'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Label } from '@/components/ui/label'
import { Checkbox } from '@/components/ui/checkbox'

// O passo de recebimento virou componente genérico (PagamentoDialog); a lista
// de formas mora lá — o re-export mantém os imports existentes funcionando.
export { FORMAS_PAGAMENTO } from '@/components/locacoes/pagamento-dialog'

// Checklist items for check-out (entrada)
const checkoutChecklistItems = [
  { id: 'motor_ok', label: 'Motor OK' },
  { id: 'casco_ok', label: 'Casco OK' },
  { id: 'limpeza_ok', label: 'Limpeza OK' },
  { id: 'combustivel_verificado', label: 'Combustível verificado' },
  { id: 'equipamentos_ok', label: 'Equipamentos OK' },
]

export function CheckOutDialog({
  locacao,
  open,
  onOpenChange,
}: {
  locacao: Locacao
  open: boolean
  onOpenChange: (open: boolean) => void
}) {
  const queryClient = useQueryClient()

  const [checklist, setChecklist] = useState<Record<string, boolean>>(() =>
    checkoutChecklistItems.reduce((acc, item) => ({ ...acc, [item.id]: false }), {})
  )
  const [horimetroFim, setHorimetroFim] = useState(locacao.horimetroInicio || 0)
  const [observacoes, setObservacoes] = useState('')
  const [skipPhotos, setSkipPhotos] = useState(false)

  // Passo 2 — Recebimento (após o check-out o backend conhece o valor final)
  const [extrato, setExtrato] = useState<FolioExtrato | null>(null)

  const toggleChecklistItem = (id: string) => {
    setChecklist(prev => ({ ...prev, [id]: !prev[id] }))
  }

  const allChecked = Object.values(checklist).every(v => v)

  const checkOutMutation = useMutation({
    mutationFn: (data: CheckOutRequest) => locacoesService.checkOut(locacao.id, data),
    onSuccess: async () => {
      queryClient.invalidateQueries({ queryKey: ['locacoes'] })
      queryClient.invalidateQueries({ queryKey: ['jetskis'] })
      // Passo 2: buscar o extrato para cobrar o saldo exato
      try {
        const ex = await locacoesService.extrato(locacao.id)
        setExtrato(ex)
      } catch {
        onOpenChange(false) // extrato indisponível → fecha como antes
      }
    },
  })

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    const checkedItems = Object.entries(checklist)
      .filter(([, checked]) => checked)
      .map(([id]) => id)

    const request: CheckOutRequest = {
      horimetroFim,
      checklistEntradaJson: JSON.stringify(checkedItems),
      observacoes: observacoes || undefined,
      skipPhotos,
    }
    checkOutMutation.mutate(request)
  }

  // Calculate estimated usage
  const horasUsadas = horimetroFim > locacao.horimetroInicio
    ? (horimetroFim - locacao.horimetroInicio).toFixed(1)
    : '0'

  // Passo 2 — Recebimento: check-out concluído, cobrar o saldo exato
  // (mesmo comportamento de antes, agora via componente genérico).
  if (extrato) {
    return (
      <PagamentoDialog
        open={open}
        onOpenChange={onOpenChange}
        titulo="Recebimento"
        descricao="Check-out concluído — registre o que foi recebido do cliente."
        extrato={extrato}
        onRegistrar={async (forma, valor) => {
          await locacoesService.registrarPagamento(locacao.id, { forma, valor })
          queryClient.invalidateQueries({ queryKey: ['locacoes'] })
        }}
      />
    )
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-[500px]">
        <form onSubmit={handleSubmit}>
          <DialogHeader>
            <DialogTitle>Check-out</DialogTitle>
            <DialogDescription>
              Finalize a locação registrando o horímetro final e verificando os itens
            </DialogDescription>
          </DialogHeader>

          <div className="grid gap-4 py-4">
            {/* Rental Info Summary */}
            <div className="rounded-lg border p-4 space-y-2 bg-muted/30">
              <div className="flex justify-between">
                <span className="text-muted-foreground">Jetski:</span>
                <span className="font-medium">{locacao.jetskiSerie || locacao.jetskiModeloNome}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-muted-foreground">Cliente:</span>
                <span>{locacao.clienteNome || 'Walk-in'}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-muted-foreground">Check-in:</span>
                <span>{formatDateTime(locacao.dataCheckIn)}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-muted-foreground">Horímetro Inicial:</span>
                <span>{locacao.horimetroInicio}h</span>
              </div>
              {locacao.duracaoPrevista && (
                <div className="flex justify-between">
                  <span className="text-muted-foreground">Duração Prevista:</span>
                  <span>{formatDuration(locacao.duracaoPrevista)}</span>
                </div>
              )}
            </div>

            {/* Horímetro Final */}
            <div className="grid gap-2">
              <Label htmlFor="horimetroFim">Horímetro Final *</Label>
              <Input
                id="horimetroFim"
                type="number"
                value={horimetroFim}
                onChange={(e) => setHorimetroFim(Number(e.target.value))}
                min={locacao.horimetroInicio}
                step={0.1}
                required
              />
              {Number(horasUsadas) > 0 && (
                <p className="text-sm text-muted-foreground">
                  Tempo de uso: ~{horasUsadas}h ({Math.round(Number(horasUsadas) * 60)} minutos)
                </p>
              )}
            </div>

            {/* Checklist de Verificação */}
            <div className="grid gap-2">
              <Label>Checklist de Verificação *</Label>
              <div className="rounded-lg border p-3 space-y-2">
                {checkoutChecklistItems.map((item) => (
                  <label
                    key={item.id}
                    className="flex items-center gap-3 cursor-pointer hover:bg-muted/50 p-2 rounded-md transition-colors"
                  >
                    <Checkbox
                      checked={checklist[item.id]}
                      onCheckedChange={() => toggleChecklistItem(item.id)}
                    />
                    <span className={checklist[item.id] ? 'text-foreground' : 'text-muted-foreground'}>
                      {item.label}
                    </span>
                  </label>
                ))}
              </div>
              {!allChecked && (
                <p className="text-xs text-muted-foreground">
                  Marque todos os itens para continuar
                </p>
              )}
            </div>

            {/* Skip Photos Option */}
            <div className="flex items-center space-x-2">
              <Checkbox
                id="skipPhotos"
                checked={skipPhotos}
                onCheckedChange={(checked) => setSkipPhotos(checked === true)}
              />
              <Label htmlFor="skipPhotos" className="text-sm cursor-pointer">
                Pular validação de fotos (adicionar depois)
              </Label>
            </div>

            {/* Observações */}
            <div className="grid gap-2">
              <Label htmlFor="observacoes">Observações</Label>
              <Input
                id="observacoes"
                value={observacoes}
                onChange={(e) => setObservacoes(e.target.value)}
                placeholder="Alguma observação sobre a devolução?"
              />
            </div>
          </div>

          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
              Cancelar
            </Button>
            <Button type="submit" disabled={checkOutMutation.isPending || !allChecked}>
              {checkOutMutation.isPending ? 'Finalizando...' : 'Finalizar Locação'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
