'use client'

import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Copy, Loader2, QrCode, Wallet } from 'lucide-react'
import { toast } from 'sonner'
import { useTenantStore } from '@/lib/store/tenant-store'
import { faturasService, type Fatura } from '@/lib/api/services/faturas'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle,
} from '@/components/ui/dialog'
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from '@/components/ui/table'
import { PixQrCode } from '@/components/pix-qrcode'

const STATUS_BADGE: Record<Fatura['status'], { label: string; variant: 'default' | 'secondary' | 'destructive' | 'outline' }> = {
  ABERTA: { label: 'Aberta', variant: 'secondary' },
  EM_CONFERENCIA: { label: 'Em conferência', variant: 'default' },
  PAGA: { label: 'Paga', variant: 'default' },
  CANCELADA: { label: 'Cancelada', variant: 'outline' },
}

const mesAno = (iso: string) => {
  const [y, m] = iso.split('-')
  return `${m}/${y}`
}
const dataBr = (iso: string) => new Date(iso + 'T00:00:00').toLocaleDateString('pt-BR')
const brl = (v: number) => v.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })

/** Barra de uso × limite do plano (-1/ausente = ilimitado). */
function UsoLimite({ rotulo, uso, limite }: { rotulo: string; uso: number; limite?: number }) {
  const ilimitado = limite == null || limite < 0
  const pct = ilimitado ? 0 : Math.min(100, Math.round((uso / Math.max(limite, 1)) * 100))
  const estourando = !ilimitado && pct >= 90
  return (
    <div>
      <div className="mb-1 flex justify-between text-sm">
        <span className="text-muted-foreground">{rotulo}</span>
        <span className={estourando ? 'font-medium text-amber-600' : ''}>
          {uso}{ilimitado ? '' : ` / ${limite}`}{ilimitado && <span className="text-muted-foreground"> (ilimitado)</span>}
        </span>
      </div>
      {!ilimitado && (
        <div className="h-2 rounded-full bg-muted">
          <div
            className={`h-2 rounded-full ${estourando ? 'bg-amber-500' : 'bg-primary'}`}
            style={{ width: `${pct}%` }}
          />
        </div>
      )}
    </div>
  )
}

export default function PlanoPage() {
  const { currentTenant } = useTenantStore()
  const queryClient = useQueryClient()
  const [pixDe, setPixDe] = useState<Fatura | null>(null)
  const [informando, setInformando] = useState<Fatura | null>(null)
  const [txid, setTxid] = useState('')

  const { data, isLoading } = useQuery({
    queryKey: ['plano-faturas', currentTenant?.id],
    queryFn: () => faturasService.minhas(),
    enabled: !!currentTenant,
  })

  const informar = useMutation({
    mutationFn: () => faturasService.informarPagamento(informando!.id, txid.trim()),
    onSuccess: () => {
      toast.success('Pagamento informado — nossa equipe vai conferir e confirmar.')
      queryClient.invalidateQueries({ queryKey: ['plano-faturas'] })
      setInformando(null)
      setTxid('')
    },
    onError: (e: unknown) => {
      const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
      toast.error(msg ?? 'Falha ao informar o pagamento.')
    },
  })

  const isAdmin = currentTenant?.roles?.includes('ADMIN_TENANT')
  if (currentTenant && !isAdmin) {
    return (
      <p className="py-16 text-center text-muted-foreground">
        Página restrita ao administrador da empresa.
      </p>
    )
  }

  const limites: Record<string, number> = (() => {
    try { return JSON.parse(data?.plano.limites ?? '{}') } catch { return {} }
  })()

  return (
    <div className="space-y-6">
      <div>
        <h1 className="flex items-center gap-2 text-2xl font-bold">
          <Wallet className="size-6" /> Plano e faturas
        </h1>
        <p className="text-muted-foreground">
          Sua assinatura Meu Jet: plano contratado, uso dos limites e faturas mensais.
        </p>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Plano {data?.plano.plano ?? '…'}</CardTitle>
          <CardDescription>
            {data && data.plano.precoMensal > 0
              ? `${brl(data.plano.precoMensal)}/mês — fatura gerada todo início de mês, pagamento via PIX.`
              : 'Período de teste — sem cobrança. Para contratar um plano, fale com o Meu Jet.'}
          </CardDescription>
        </CardHeader>
        <CardContent className="grid gap-4 sm:grid-cols-3">
          <UsoLimite rotulo="Jetskis ativos" uso={data?.uso.jetskisAtivos ?? 0} limite={limites.frota_max} />
          <UsoLimite rotulo="Locações no mês" uso={data?.uso.locacoesMes ?? 0} limite={limites.locacoes_mes} />
          <UsoLimite rotulo="Usuários da equipe" uso={data?.uso.usuariosAtivos ?? 0} limite={limites.usuarios_max} />
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Faturas</CardTitle>
          <CardDescription>
            Pague o PIX e informe o número da transação — a confirmação é feita pela equipe Meu Jet.
          </CardDescription>
        </CardHeader>
        <CardContent>
          {isLoading ? (
            <p className="py-8 text-center text-muted-foreground">Carregando…</p>
          ) : !data || data.faturas.length === 0 ? (
            <p className="py-8 text-center text-muted-foreground">
              Nenhuma fatura ainda — o período de teste não gera cobrança.
            </p>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Competência</TableHead>
                  <TableHead>Plano</TableHead>
                  <TableHead className="text-right">Valor</TableHead>
                  <TableHead>Vencimento</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead className="text-right">Ações</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {data.faturas.map((f) => (
                  <TableRow key={f.id}>
                    <TableCell className="font-medium">{mesAno(f.competencia)}</TableCell>
                    <TableCell>{f.planoNome}</TableCell>
                    <TableCell className="text-right tabular-nums">{brl(f.valor)}</TableCell>
                    <TableCell>{dataBr(f.vencimento)}</TableCell>
                    <TableCell>
                      <Badge variant={STATUS_BADGE[f.status].variant}>{STATUS_BADGE[f.status].label}</Badge>
                    </TableCell>
                    <TableCell className="text-right">
                      {(f.status === 'ABERTA' || f.status === 'EM_CONFERENCIA') && (
                        <div className="flex justify-end gap-2">
                          <Button variant="outline" size="sm" onClick={() => setPixDe(f)}>
                            <QrCode className="mr-1 size-4" /> PIX
                          </Button>
                          <Button size="sm" onClick={() => { setInformando(f); setTxid(f.txidInformado ?? '') }}>
                            Informar pagamento
                          </Button>
                        </div>
                      )}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>

      {/* Dialog do PIX */}
      <Dialog open={!!pixDe} onOpenChange={(o) => !o && setPixDe(null)}>
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle>PIX da fatura {pixDe && mesAno(pixDe.competencia)}</DialogTitle>
            <DialogDescription>
              {pixDe && `${brl(pixDe.valor)} — vencimento ${dataBr(pixDe.vencimento)}.`}
            </DialogDescription>
          </DialogHeader>
          {pixDe?.pixCopiaECola && (
            <div className="flex flex-col items-center gap-3">
              <PixQrCode payload={pixDe.pixCopiaECola} size={200} />
              <code className="max-w-full break-all rounded bg-muted px-2 py-1 text-[11px]">
                {pixDe.pixCopiaECola}
              </code>
              <Button
                variant="ghost"
                size="sm"
                onClick={async () => {
                  await navigator.clipboard.writeText(pixDe.pixCopiaECola!)
                  toast.success('Código PIX copiado.')
                }}
              >
                <Copy className="mr-1 size-4" /> Copiar código
              </Button>
            </div>
          )}
        </DialogContent>
      </Dialog>

      {/* Dialog informar pagamento */}
      <Dialog open={!!informando} onOpenChange={(o) => !o && setInformando(null)}>
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle>Informar pagamento</DialogTitle>
            <DialogDescription>
              Cole o número da transação PIX (aparece no comprovante do seu banco) — usamos para
              localizar o pagamento no extrato.
            </DialogDescription>
          </DialogHeader>
          <div>
            <Label className="text-xs">Número da transação (txid / E2E)</Label>
            <Input
              value={txid}
              onChange={(e) => setTxid(e.target.value)}
              placeholder="E12345678202607111234ABCDEF"
              className="font-mono"
            />
          </div>
          <DialogFooter>
            <Button variant="ghost" onClick={() => setInformando(null)}>Cancelar</Button>
            <Button disabled={!txid.trim() || informar.isPending} onClick={() => informar.mutate()}>
              {informar.isPending && <Loader2 className="mr-1 size-4 animate-spin" />}
              Enviar para conferência
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
