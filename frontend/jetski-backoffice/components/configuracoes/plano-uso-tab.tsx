'use client'

import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { creditosService, meteringService } from '@/lib/api/services'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Badge } from '@/components/ui/badge'
import { useToast } from '@/hooks/use-toast'
import { PixQrCode } from '@/components/pix-qrcode'
import { FileUpload, type UploadedFile } from '@/components/file-upload'
import { VerComprovanteButton } from '@/components/creditos/ver-comprovante-button'
import { Loader2, Gauge, FileCheck2, Landmark, Eye, Coins, Copy, ShoppingCart, QrCode } from 'lucide-react'
import { cn } from '@/lib/utils'
import {
  Bar,
  BarChart,
  CartesianGrid,
  Legend,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'

/**
 * Aba Configurações › Plano e Uso: consumo de emissões do tenant.
 * Hoje é apenas contagem; a cobrança por emissão entrará nos planos futuros.
 */
const TIPO_LABEL: Record<string, string> = {
  ADESAO: 'Créditos de adesão',
  AJUSTE: 'Lançamento Meu Jet',
  CONSUMO: 'Emissão de documento',
  ESTORNO: 'Estorno',
}

const STATUS_COMPRA: Record<string, { label: string; variant: 'default' | 'secondary' | 'destructive' }> = {
  PENDENTE: { label: 'Aguardando aprovação', variant: 'secondary' },
  APROVADA: { label: 'Aprovada', variant: 'default' },
  REJEITADA: { label: 'Rejeitada', variant: 'destructive' },
}

const brl = (v: number) =>
  v.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })

/** Compra manual v1: escolhe a QUANTIDADE, vê o valor, transfere via PIX e envia o comprovante. */
function ComprarCreditosCard() {
  const queryClient = useQueryClient()
  const { toast } = useToast()
  const [quantidade, setQuantidade] = useState('50')
  const [comprovante, setComprovante] = useState<UploadedFile | null>(null)
  const [pix, setPix] = useState<{ copiaECola: string; valor: number; quantidade: number } | null>(null)

  const { data: config } = useQuery({
    queryKey: ['creditos-config'],
    queryFn: () => creditosService.getConfig(),
  })
  const { data: compras } = useQuery({
    queryKey: ['creditos-compras'],
    queryFn: () => creditosService.getCompras(5),
  })

  const gerarPix = useMutation({
    mutationFn: () => creditosService.gerarPix(Number(quantidade)),
    onSuccess: (p) => setPix(p),
    onError: (e: unknown) => {
      const err = e as { response?: { data?: { message?: string } }; message?: string }
      toast({
        title: 'Erro ao gerar PIX',
        description: err.response?.data?.message || err.message || 'Não foi possível gerar.',
        variant: 'destructive',
      })
    },
  })

  const solicitar = useMutation({
    mutationFn: () => {
      if (!comprovante) throw new Error('Envie a foto do comprovante antes de continuar.')
      return creditosService.solicitarCompra(Number(quantidade), comprovante.dataUrl)
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['creditos-compras'] })
      setComprovante(null)
      setPix(null)
      toast({
        title: 'Solicitação enviada',
        description: 'Assim que o pagamento for conferido, os créditos entram no seu saldo.',
      })
    },
    onError: (e: unknown) => {
      const err = e as { response?: { data?: { message?: string } }; message?: string }
      toast({
        title: 'Erro ao solicitar',
        description: err.response?.data?.message || err.message || 'Não foi possível enviar.',
        variant: 'destructive',
      })
    },
  })

  const copiarPix = async () => {
    if (pix) {
      await navigator.clipboard.writeText(pix.copiaECola)
      toast({ title: 'PIX copia-e-cola copiado', description: 'Cole no app do seu banco.' })
    }
  }

  const qtdNum = Number(quantidade)
  const qtdValida = Number.isInteger(qtdNum) && qtdNum > 0
  const preco = config?.precoUnitario ?? 0
  const valorAPagar = qtdValida && preco > 0 ? qtdNum * preco : 0

  return (
    <div className="rounded-lg border p-5">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="flex items-center gap-2 text-xs font-medium uppercase tracking-wide text-muted-foreground">
          <ShoppingCart className="h-4 w-4" /> Comprar créditos
        </div>
        {preco > 0 && (
          <span className="text-xs text-muted-foreground">
            Preço vigente: <span className="font-semibold text-foreground">{brl(preco)}</span> por crédito
          </span>
        )}
      </div>
      <ol className="mt-3 list-decimal space-y-1 pl-5 text-sm text-muted-foreground">
        <li>Escolha quantas emissões quer comprar e gere o PIX com o valor exato.</li>
        <li>Pague pelo QR Code ou copia-e-cola no app do seu banco.</li>
        <li>Envie a foto do comprovante — o Meu Jet confere e libera os créditos.</li>
      </ol>
      <div className="mt-4 flex flex-wrap items-end gap-3">
        <div className="space-y-1">
          <label className="text-xs font-medium" htmlFor="compra-qtd">Quantidade de emissões</label>
          <Input
            id="compra-qtd"
            type="number"
            min={1}
            step="1"
            value={quantidade}
            onChange={(e) => { setQuantidade(e.target.value); setPix(null); setComprovante(null) }}
            className="w-36 tabular-nums"
          />
        </div>
        <Button
          variant="outline"
          onClick={() => gerarPix.mutate()}
          disabled={gerarPix.isPending || !qtdValida}
          className="gap-2"
        >
          {gerarPix.isPending ? <Loader2 className="h-4 w-4 animate-spin" /> : <QrCode className="h-4 w-4" />}
          Gerar PIX{qtdValida && preco > 0 ? ` de ${brl(valorAPagar)}` : ''}
        </Button>
      </div>

      {pix && (
        <div className="mt-4 flex flex-wrap items-center gap-5 rounded-lg border bg-muted/40 p-4">
          <PixQrCode payload={pix.copiaECola} size={148} className="rounded-md border bg-white p-1.5" />
          <div className="min-w-0 flex-1 basis-64 space-y-2">
            <p className="text-sm">
              Pague <span className="font-semibold tabular-nums">{brl(pix.valor)}</span>{' '}
              <span className="text-muted-foreground">({pix.quantidade} créditos)</span> escaneando o QR
              ou pelo copia-e-cola:
            </p>
            <div className="flex items-center gap-2">
              <code className="min-w-0 max-w-xs flex-1 truncate rounded bg-background px-2 py-1 text-[11px]">{pix.copiaECola}</code>
              <Button size="sm" variant="outline" onClick={copiarPix} className="gap-1 shrink-0">
                <Copy className="h-3.5 w-3.5" /> Copiar
              </Button>
            </div>
            <div className="flex flex-wrap items-end gap-2 pt-1">
              <div className="min-w-0 flex-1 basis-56 space-y-1">
                <span className="text-xs font-medium">Foto do comprovante</span>
                <FileUpload
                  label="Foto do comprovante (imagem ou PDF)"
                  accept="image/*,application/pdf"
                  tipoDocumento="GRU_COMPROVANTE"
                  onChange={setComprovante}
                />
              </div>
              <Button
                onClick={() => solicitar.mutate()}
                disabled={solicitar.isPending || !comprovante}
                className="gap-2"
              >
                {solicitar.isPending ? <Loader2 className="h-4 w-4 animate-spin" /> : <ShoppingCart className="h-4 w-4" />}
                Já paguei — enviar
              </Button>
            </div>
          </div>
        </div>
      )}

      {compras && compras.length > 0 && (
        <div className="mt-4 divide-y rounded-lg border">
          {compras.map((c) => {
            const st = STATUS_COMPRA[c.status] ?? { label: c.status, variant: 'secondary' as const }
            return (
              <div key={c.id} className="flex flex-wrap items-center justify-between gap-2 px-4 py-2.5 text-sm">
                <div className="flex min-w-0 flex-wrap items-center gap-2">
                  <span className="font-medium tabular-nums">+{c.quantidade} créditos</span>
                  {c.valorPago != null && (
                    <span className="text-xs text-muted-foreground tabular-nums">({brl(c.valorPago)})</span>
                  )}
                  {c.temComprovante ? (
                    <VerComprovanteButton fetchBlob={() => creditosService.getComprovante(c.id)} />
                  ) : (
                    c.pixTxid && (
                      <span className="font-mono text-xs text-muted-foreground">{c.pixTxid}</span>
                    )
                  )}
                  {c.observacao && (
                    <span className="text-xs text-destructive">{c.observacao}</span>
                  )}
                </div>
                <div className="flex items-center gap-3">
                  <Badge variant={st.variant}>{st.label}</Badge>
                  <span className="text-xs text-muted-foreground">
                    {new Date(c.createdAt).toLocaleDateString('pt-BR')}
                  </span>
                </div>
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}

export function PlanoUsoTab() {
  const { data: serie, isLoading } = useQuery({
    queryKey: ['metering-emissoes'],
    queryFn: () => meteringService.getEmissoesMensais(6),
  })
  const { data: saldoData } = useQuery({
    queryKey: ['creditos-saldo'],
    queryFn: () => creditosService.getSaldo(),
  })
  const { data: extrato } = useQuery({
    queryKey: ['creditos-extrato'],
    queryFn: () => creditosService.getExtrato(10),
  })

  const mesAtual = serie?.[serie.length - 1]
  const saldo = saldoData?.saldo

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <Gauge className="h-5 w-5" />
          Plano e Uso
        </CardTitle>
        <CardDescription>
          Consumo de emissões da sua empresa. A cobrança por emissão fará parte dos
          planos futuros — por enquanto é apenas contagem informativa.
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-6">
        {isLoading || !serie ? (
          <div className="flex items-center gap-2 text-sm text-muted-foreground">
            <Loader2 className="h-4 w-4 animate-spin" /> Carregando…
          </div>
        ) : (
          <>
            {/* Saldo de créditos — pré-pago: cada documento à Marinha consome 1 */}
            <div
              className={cn(
                'rounded-lg border p-5',
                saldo === undefined
                  ? ''
                  : saldo === 0
                    ? 'border-destructive/50 bg-destructive/5'
                    : saldo < 5
                      ? 'border-warning/50 bg-warning/5'
                      : 'border-gold/40 bg-gold/5'
              )}
            >
              <div className="flex flex-wrap items-center justify-between gap-4">
                <div>
                  <div className="flex items-center gap-2 text-xs font-medium uppercase tracking-wide text-muted-foreground">
                    <Coins className="h-4 w-4" /> Saldo de créditos de emissão
                  </div>
                  <div className="mt-1 text-3xl font-semibold tabular-nums">
                    {saldo ?? '—'}
                  </div>
                  <p className="mt-1 text-xs text-muted-foreground">
                    Cada documento emitido à Marinha consome 1 crédito. Emissões via CHA não consomem.
                  </p>
                </div>
                {saldo !== undefined && saldo === 0 && (
                  <p className="text-sm font-medium text-destructive">
                    Créditos esgotados — novas emissões estão bloqueadas. Fale com o Meu Jet.
                  </p>
                )}
                {saldo !== undefined && saldo > 0 && saldo < 5 && (
                  <p className="text-sm font-medium text-warning">
                    Saldo baixo — garanta mais créditos para não travar o balcão.
                  </p>
                )}
              </div>
            </div>

            <div className="grid gap-4 sm:grid-cols-3">
              <div className="rounded-lg border p-4">
                <div className="flex items-center gap-2 text-xs font-medium uppercase tracking-wide text-muted-foreground">
                  <FileCheck2 className="h-4 w-4" /> Documentos emitidos
                </div>
                <div className="mt-1 text-2xl font-semibold tabular-nums">{mesAtual?.documento ?? 0}</div>
                <div className="text-xs text-muted-foreground">neste mês</div>
              </div>
              <div className="rounded-lg border p-4">
                <div className="flex items-center gap-2 text-xs font-medium uppercase tracking-wide text-muted-foreground">
                  <Landmark className="h-4 w-4" /> GRUs geradas
                </div>
                <div className="mt-1 text-2xl font-semibold tabular-nums">{mesAtual?.gru ?? 0}</div>
                <div className="text-xs text-muted-foreground">neste mês</div>
              </div>
              <div className="rounded-lg border p-4">
                <div className="flex items-center gap-2 text-xs font-medium uppercase tracking-wide text-muted-foreground">
                  <Eye className="h-4 w-4" /> Prévias geradas
                </div>
                <div className="mt-1 text-2xl font-semibold tabular-nums">{mesAtual?.previa ?? 0}</div>
                <div className="text-xs text-muted-foreground">não cobrável</div>
              </div>
            </div>

            <div>
              <p className="mb-2 text-sm font-medium">Últimos 6 meses</p>
              <ResponsiveContainer width="100%" height={240}>
                <BarChart data={serie}>
                  <CartesianGrid strokeDasharray="2 3" vertical={false} />
                  <XAxis dataKey="competencia" fontSize={11} />
                  <YAxis allowDecimals={false} fontSize={11} width={32} />
                  <Tooltip />
                  <Legend />
                  <Bar name="Documentos" dataKey="documento" fill="var(--chart-1)" radius={[3, 3, 0, 0]} />
                  <Bar name="GRUs" dataKey="gru" fill="var(--chart-2)" radius={[3, 3, 0, 0]} />
                  <Bar name="Prévias" dataKey="previa" fill="var(--chart-3)" radius={[3, 3, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
            </div>

            <ComprarCreditosCard />

            {extrato && extrato.length > 0 && (
              <div>
                <p className="mb-2 text-sm font-medium">Movimentação de créditos</p>
                <div className="divide-y rounded-lg border">
                  {extrato.map((l) => (
                    <div key={l.id} className="flex items-center justify-between gap-4 px-4 py-2.5 text-sm">
                      <div className="min-w-0">
                        <span className="font-medium">{TIPO_LABEL[l.tipo] ?? l.tipo}</span>
                        {l.motivo && (
                          <span className="ml-2 truncate text-xs text-muted-foreground">{l.motivo}</span>
                        )}
                      </div>
                      <div className="flex shrink-0 items-center gap-4 tabular-nums">
                        <span className={cn('font-semibold', l.quantidade > 0 ? 'text-success' : 'text-muted-foreground')}>
                          {l.quantidade > 0 ? `+${l.quantidade}` : l.quantidade}
                        </span>
                        <span className="w-16 text-right text-xs text-muted-foreground">saldo {l.saldoApos}</span>
                        <span className="w-20 text-right text-xs text-muted-foreground">
                          {new Date(l.createdAt).toLocaleDateString('pt-BR')}
                        </span>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </>
        )}
      </CardContent>
    </Card>
  )
}
