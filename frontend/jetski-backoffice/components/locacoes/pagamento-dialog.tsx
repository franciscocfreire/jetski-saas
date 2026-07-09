'use client'

import { useEffect, useState } from 'react'
import { toast } from 'sonner'
import type { FormaPagamento, FolioExtrato } from '@/lib/api/types'
import { formatCurrency } from '@/lib/utils'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Skeleton } from '@/components/ui/skeleton'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'

export const FORMAS_PAGAMENTO: { value: FormaPagamento; label: string }[] = [
  { value: 'DINHEIRO', label: 'Dinheiro' },
  { value: 'PIX', label: 'PIX' },
  { value: 'CARTAO_CREDITO', label: 'Cartão de crédito' },
  { value: 'CARTAO_DEBITO', label: 'Cartão de débito' },
  { value: 'OUTRO', label: 'Outro' },
]

/**
 * Passo de recebimento genérico (extraído do CheckOutDialog): mostra o
 * resumo do folio (total / já pago / saldo) e registra um recebimento.
 * Serve tanto para locações quanto para reservas — quem sabe qual service
 * chamar é o `onRegistrar` do chamador; o dialog fecha sozinho no sucesso
 * e mostra o erro do backend no toast.
 */
export function PagamentoDialog({
  open,
  onOpenChange,
  titulo,
  descricao,
  extrato,
  carregando,
  onRegistrar,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
  titulo: string
  descricao?: string
  extrato: FolioExtrato | null | undefined
  carregando?: boolean
  onRegistrar: (forma: FormaPagamento, valor: number) => Promise<void>
}) {
  const [forma, setForma] = useState<FormaPagamento>('DINHEIRO')
  const [valor, setValor] = useState('')
  const [registrando, setRegistrando] = useState(false)

  // Valor sugerido = saldo em aberto (o caso comum é receber tudo de uma vez)
  useEffect(() => {
    if (extrato) setValor(extrato.saldo > 0 ? extrato.saldo.toFixed(2) : '')
  }, [extrato])

  const saldo = extrato?.saldo ?? 0
  const valorNum = Number(valor)

  const registrar = async () => {
    setRegistrando(true)
    try {
      await onRegistrar(forma, valorNum)
      onOpenChange(false)
    } catch (e: unknown) {
      const msg =
        (e as { response?: { data?: { message?: string } } })?.response?.data?.message ??
        'Não foi possível registrar o recebimento'
      toast.error(msg)
    } finally {
      setRegistrando(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-[440px]">
        <DialogHeader>
          <DialogTitle>{titulo}</DialogTitle>
          <DialogDescription>
            {descricao ?? 'Registre o que foi recebido do cliente.'}
          </DialogDescription>
        </DialogHeader>

        {carregando || !extrato ? (
          <div className="grid gap-3 py-2">
            <Skeleton className="h-24 w-full" />
            <Skeleton className="h-9 w-full" />
          </div>
        ) : (
          <div className="grid gap-3 py-2">
            <div className="rounded-lg border p-4 space-y-1 bg-muted/30 text-sm">
              <div className="flex justify-between">
                <span className="text-muted-foreground">Total:</span>
                <span className="font-medium">{formatCurrency(extrato.totalCobrancas)}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-muted-foreground">Já pago:</span>
                <span>{formatCurrency(extrato.totalPagamentos - extrato.totalEstornos)}</span>
              </div>
              <div className="flex justify-between border-t pt-1 font-semibold">
                <span>Saldo a receber:</span>
                <span>{formatCurrency(Math.max(saldo, 0))}</span>
              </div>
            </div>

            {saldo <= 0 ? (
              <p className="text-sm text-muted-foreground">
                Nada a receber — os pagamentos já cobrem o valor.
              </p>
            ) : (
              <>
                <div className="grid gap-2">
                  <Label>Forma de pagamento</Label>
                  <Select value={forma} onValueChange={(v) => setForma(v as FormaPagamento)}>
                    <SelectTrigger>
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      {FORMAS_PAGAMENTO.map((f) => (
                        <SelectItem key={f.value} value={f.value}>
                          {f.label}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>
                <div className="grid gap-2">
                  <Label>Valor recebido (R$)</Label>
                  <Input
                    type="number"
                    min="0.01"
                    step="0.01"
                    value={valor}
                    onChange={(e) => setValor(e.target.value)}
                  />
                </div>
              </>
            )}
          </div>
        )}

        <DialogFooter>
          {extrato && saldo <= 0 ? (
            <Button type="button" onClick={() => onOpenChange(false)}>
              Concluir
            </Button>
          ) : (
            <>
              <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
                Receber depois
              </Button>
              <Button
                type="button"
                disabled={!extrato || !(valorNum > 0) || registrando}
                onClick={registrar}
              >
                {registrando ? 'Registrando…' : 'Registrar recebimento'}
              </Button>
            </>
          )}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
