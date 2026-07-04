'use client'

import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { reservasService } from '@/lib/api/services'
import type { PagamentoPendente } from '@/lib/api/types'
import { useTenantStore } from '@/lib/store/tenant-store'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Input } from '@/components/ui/input'
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { toast } from 'sonner'
import {
  BadgeCheck,
  FileSearch,
  Loader2,
  ReceiptText,
  XCircle,
} from 'lucide-react'

const brl = (v?: number | null) =>
  v == null ? '—' : v.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })

/**
 * Fila de validação de pagamentos (sinal/total): reservas com comprovante
 * enviado pelo cliente no portal (EM_ANALISE). Confirmar libera a garantia
 * (prioridade ALTA); recusar devolve a pendência ao cliente com motivo.
 */
export default function SinaisPage() {
  const { currentTenant } = useTenantStore()
  const queryClient = useQueryClient()

  const [confirmando, setConfirmando] = useState<PagamentoPendente | null>(null)
  const [valorConfirmado, setValorConfirmado] = useState('')
  const [recusando, setRecusando] = useState<PagamentoPendente | null>(null)
  const [motivo, setMotivo] = useState('')

  const { data: pendentes, isLoading } = useQuery({
    queryKey: ['pagamentos-pendentes', currentTenant?.id],
    queryFn: () => reservasService.pagamentosPendentes(),
    enabled: !!currentTenant,
    refetchInterval: 60_000,
  })

  const invalidate = () =>
    queryClient.invalidateQueries({ queryKey: ['pagamentos-pendentes', currentTenant?.id] })

  const confirmar = useMutation({
    mutationFn: (p: { id: string; tipo?: 'SINAL' | 'TOTAL'; valor: number }) =>
      reservasService.confirmarPagamento(p.id, { tipo: p.tipo ?? 'SINAL', valorPago: p.valor }),
    onSuccess: () => {
      toast.success('Pagamento confirmado — reserva garantida')
      setConfirmando(null)
      invalidate()
    },
    onError: (e: Error) => toast.error('Não foi possível confirmar', { description: e.message }),
  })

  const recusar = useMutation({
    mutationFn: (p: { id: string; motivo: string }) =>
      reservasService.recusarPagamento(p.id, p.motivo),
    onSuccess: () => {
      toast.success('Pagamento recusado — o cliente verá o motivo')
      setRecusando(null)
      setMotivo('')
      invalidate()
    },
    onError: (e: Error) => toast.error('Não foi possível recusar', { description: e.message }),
  })

  // Viewer IN-APP: navegar para a URL do MinIO dentro do PWA standalone
  // engole o app (mesmo host = mesmo escopo; sem chrome, sem voltar).
  const [viewer, setViewer] = useState<{ url: string; pdf: boolean } | null>(null)

  const verComprovante = async (reservaId: string) => {
    try {
      const lista = await reservasService.comprovantes(reservaId)
      if (lista.length === 0) {
        toast.info('Nenhum comprovante anexado')
        return
      }
      const url = lista[0].downloadUrl
      const pdf = /\.pdf(\?|$)/i.test(url)
      setViewer({ url, pdf })
    } catch {
      toast.error('Não foi possível abrir o comprovante')
    }
  }

  if (!currentTenant) {
    return <p className="p-6 text-muted-foreground">Selecione uma empresa.</p>
  }

  return (
    <div className="space-y-6 p-6">
      <div>
        <h1 className="font-display text-2xl font-semibold">Validar pagamentos</h1>
        <p className="text-sm text-muted-foreground">
          Comprovantes PIX enviados pelos clientes — confira no extrato e confirme ou recuse.
        </p>
      </div>

      {isLoading && (
        <div className="flex justify-center py-16 text-muted-foreground">
          <Loader2 className="animate-spin" />
        </div>
      )}

      {!isLoading && (pendentes?.length ?? 0) === 0 && (
        <div className="rounded-xl border border-dashed p-10 text-center text-muted-foreground">
          <ReceiptText className="mx-auto mb-2 h-8 w-8 opacity-40" />
          Nenhum pagamento aguardando validação.
        </div>
      )}

      <div className="space-y-3">
        {pendentes?.map((p) => {
          const valorEsperado =
            p.pagamentoTipo === 'TOTAL'
              ? p.valorEstimadoTotal
              : p.valorEstimadoTotal != null
                ? Math.round(p.valorEstimadoTotal * 30) / 100
                : undefined
          const divergente =
            p.valorInformado != null && valorEsperado != null &&
            Math.abs(p.valorInformado - valorEsperado) > 0.01
          return (
            <div key={p.reservaId} className="flex flex-wrap items-center gap-4 rounded-xl border bg-card p-4">
              <div className="min-w-48 flex-1">
                <p className="font-medium">{p.clienteNome}</p>
                <p className="text-sm text-muted-foreground">
                  {p.modeloNome} ·{' '}
                  {p.dataInicio ? new Date(p.dataInicio).toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' }) : '—'}
                </p>
              </div>
              <div className="flex items-center gap-2">
                <Badge variant="outline">{p.pagamentoTipo ?? 'SINAL'}</Badge>
                {p.canal === 'PORTAL' && <Badge variant="secondary">Portal</Badge>}
              </div>
              <div className="text-sm tabular-nums">
                <p>
                  Informado: <span className="font-semibold">{brl(p.valorInformado)}</span>
                </p>
                <p className={divergente ? 'text-destructive' : 'text-muted-foreground'}>
                  Esperado: {brl(valorEsperado)}
                  {divergente && ' ⚠'}
                </p>
              </div>
              <div className="flex w-full flex-wrap gap-2 sm:w-auto">
                <Button size="sm" variant="outline" className="gap-1" onClick={() => verComprovante(p.reservaId)}>
                  <FileSearch className="h-4 w-4" /> Comprovante
                </Button>
                <Button
                  size="sm"
                  className="gap-1"
                  onClick={() => {
                    setConfirmando(p)
                    setValorConfirmado(String(p.valorInformado ?? valorEsperado ?? ''))
                  }}
                >
                  <BadgeCheck className="h-4 w-4" /> Confirmar
                </Button>
                <Button size="sm" variant="destructive" className="gap-1" onClick={() => setRecusando(p)}>
                  <XCircle className="h-4 w-4" /> Recusar
                </Button>
              </div>
            </div>
          )
        })}
      </div>

      {/* Comprovante (viewer in-app — não navega o PWA) */}
      <Dialog open={!!viewer} onOpenChange={(o) => !o && setViewer(null)}>
        <DialogContent className="max-w-lg">
          <DialogHeader>
            <DialogTitle>Comprovante</DialogTitle>
          </DialogHeader>
          {viewer && (
            <div className="max-h-[65vh] overflow-auto rounded-lg border bg-muted/30">
              {viewer.pdf ? (
                <iframe src={viewer.url} className="h-[60vh] w-full" title="Comprovante" />
              ) : (
                // eslint-disable-next-line @next/next/no-img-element
                <img src={viewer.url} alt="Comprovante" className="w-full" />
              )}
            </div>
          )}
          <div className="flex justify-between gap-2">
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={() => viewer && window.open(viewer.url, '_blank', 'noopener')}
            >
              Abrir em nova aba
            </Button>
            <Button type="button" size="sm" onClick={() => setViewer(null)}>
              Fechar
            </Button>
          </div>
        </DialogContent>
      </Dialog>

      {/* Confirmar */}
      <Dialog open={!!confirmando} onOpenChange={(o) => !o && setConfirmando(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Confirmar pagamento</DialogTitle>
          </DialogHeader>
          <p className="text-sm text-muted-foreground">
            {confirmando?.clienteNome} · {confirmando?.pagamentoTipo ?? 'SINAL'}
          </p>
          <div className="space-y-1">
            <label className="text-xs font-medium" htmlFor="valor-confirmado">Valor recebido (R$)</label>
            <Input
              id="valor-confirmado"
              type="number"
              step="0.01"
              min="0.01"
              value={valorConfirmado}
              onChange={(e) => setValorConfirmado(e.target.value)}
              className="tabular-nums"
            />
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setConfirmando(null)}>Cancelar</Button>
            <Button
              disabled={confirmar.isPending || !(Number(valorConfirmado) > 0)}
              onClick={() =>
                confirmando &&
                confirmar.mutate({
                  id: confirmando.reservaId,
                  tipo: confirmando.pagamentoTipo,
                  valor: Number(valorConfirmado),
                })
              }
            >
              {confirmar.isPending && <Loader2 className="mr-1 h-4 w-4 animate-spin" />}
              Confirmar pagamento
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Recusar */}
      <Dialog open={!!recusando} onOpenChange={(o) => !o && setRecusando(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Recusar pagamento</DialogTitle>
          </DialogHeader>
          <p className="text-sm text-muted-foreground">
            O cliente verá o motivo no portal e poderá reenviar o comprovante.
          </p>
          <div className="space-y-1">
            <label className="text-xs font-medium" htmlFor="motivo-recusa">Motivo</label>
            <Input
              id="motivo-recusa"
              value={motivo}
              onChange={(e) => setMotivo(e.target.value)}
              placeholder="Ex.: valor divergente do comprovante"
              maxLength={200}
            />
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setRecusando(null)}>Cancelar</Button>
            <Button
              variant="destructive"
              disabled={recusar.isPending || motivo.trim().length < 5}
              onClick={() => recusando && recusar.mutate({ id: recusando.reservaId, motivo: motivo.trim() })}
            >
              {recusar.isPending && <Loader2 className="mr-1 h-4 w-4 animate-spin" />}
              Recusar
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
