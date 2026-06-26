'use client'

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useRouter } from 'next/navigation'
import { toast } from 'sonner'
import {
  CheckCircle2,
  Circle,
  Copy,
  FileDown,
  Loader2,
  PlayCircle,
  QrCode,
  Receipt,
  RefreshCw,
} from 'lucide-react'
import {
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
  SheetDescription,
} from '@/components/ui/sheet'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Separator } from '@/components/ui/separator'
import { habilitacaoService, aceiteService } from '@/lib/api/services'
import { formatCurrency, formatDate } from '@/lib/utils'
import type { Reserva } from '@/lib/api/types'

function Etapa({ ok, label, hint }: { ok: boolean; label: string; hint?: string }) {
  return (
    <div className="flex items-start gap-2">
      {ok ? (
        <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0 text-emerald-600" />
      ) : (
        <Circle className="mt-0.5 h-4 w-4 shrink-0 text-muted-foreground" />
      )}
      <div className="text-sm">
        <span className={ok ? '' : 'text-muted-foreground'}>{label}</span>
        {hint && <span className="ml-1 text-xs text-muted-foreground">— {hint}</span>}
      </div>
    </div>
  )
}

export function ReservaDetailSheet({
  reserva,
  open,
  onOpenChange,
}: {
  reserva: Reserva | null
  open: boolean
  onOpenChange: (v: boolean) => void
}) {
  const qc = useQueryClient()
  const router = useRouter()
  const reservaId = reserva?.id

  const { data: hab } = useQuery({
    queryKey: ['habilitacao', reservaId],
    queryFn: () => habilitacaoService.get(reservaId!),
    enabled: open && !!reservaId,
  })
  const { data: aceite } = useQuery({
    queryKey: ['aceite', reservaId],
    queryFn: () => aceiteService.get(reservaId!),
    enabled: open && !!reservaId,
  })

  const invalidar = () => qc.invalidateQueries({ queryKey: ['habilitacao', reservaId] })

  const gerarPix = useMutation({
    mutationFn: () => habilitacaoService.gerarGru(reservaId!),
    onSuccess: (r) => {
      invalidar()
      toast[r.sucesso ? 'success' : 'warning'](
        r.sucesso ? 'PIX gerado.' : 'Não foi possível gerar o PIX automaticamente.'
      )
    },
    onError: () => toast.error('Falha ao gerar o PIX.'),
  })

  const gerarBoleto = useMutation({
    mutationFn: async () => {
      const r = await habilitacaoService.gerarBoleto(reservaId!)
      if (r.sucesso) {
        const blob = await habilitacaoService.baixarBoleto(reservaId!)
        window.open(URL.createObjectURL(blob), '_blank')
      }
      return r
    },
    onSuccess: (r) => {
      invalidar()
      toast[r.sucesso ? 'success' : 'warning'](
        r.sucesso ? 'Boleto gerado.' : 'Não foi possível gerar o boleto automaticamente.'
      )
    },
    onError: () => toast.error('Falha ao gerar o boleto.'),
  })

  const baixarBoleto = useMutation({
    mutationFn: () => habilitacaoService.baixarBoleto(reservaId!),
    onSuccess: (blob) => window.open(URL.createObjectURL(blob), '_blank'),
    onError: () => toast.error('Falha ao baixar o boleto.'),
  })

  const verificarPagamento = useMutation({
    mutationFn: () => habilitacaoService.verificarPagamento(reservaId!),
    onSuccess: (r) => {
      invalidar()
      if (r.pago) toast.success('Pagamento confirmado — GRU paga.')
      else if (r.situacao === 'EXPIRADO')
        toast.warning('A sessão do PIX expirou. Gere um novo PIX ou boleto.')
      else toast.info('Pagamento ainda não identificado. Tente novamente em instantes.')
    },
    onError: () => toast.error('Falha ao verificar o pagamento.'),
  })

  const baixarComprovante = useMutation({
    mutationFn: () => habilitacaoService.baixarComprovante(reservaId!),
    onSuccess: (blob) => window.open(URL.createObjectURL(blob), '_blank'),
    onError: () => toast.error('Falha ao baixar o comprovante.'),
  })

  if (!reserva) return null

  const online = reserva.cliente?.origem === 'PORTAL'
  const ema = hab?.via === 'EMA'
  const gruPaga = !!hab?.gruPago
  const inicio = new Date(reserva.dataInicio)
  const hora = inicio.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })

  // habilitação pendente (EMA sem GRU paga) → mostrar opções de pagamento
  const mostrarPagamentoGru = ema && !gruPaga
  const temPix = !!hab?.gruPixCopiaECola
  const temBoleto = !!hab?.gruBoletoDisponivel

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent className="w-full overflow-y-auto sm:max-w-md">
        <SheetHeader>
          <SheetTitle>{reserva.cliente?.nome || 'Reserva'}</SheetTitle>
          <SheetDescription>
            {reserva.modelo?.nome} · {formatDate(reserva.dataInicio)} às {hora}
          </SheetDescription>
        </SheetHeader>

        <div className="mt-4 flex flex-wrap gap-2">
          <Badge variant={online ? 'default' : 'secondary'}>
            {online ? 'Online (portal)' : 'Balcão'}
          </Badge>
          <Badge variant={reserva.status === 'CONFIRMADA' ? 'success' : 'warning'}>
            {reserva.status}
          </Badge>
          {reserva.jetski?.serie && <Badge variant="outline">Jetski {reserva.jetski.serie}</Badge>}
        </div>

        {(reserva.status === 'PENDENTE' || reserva.status === 'CONFIRMADA') && (
          <Button
            className="mt-4 w-full"
            onClick={() => {
              onOpenChange(false)
              router.push(`/dashboard/balcao?reserva=${reserva.id}`)
            }}
          >
            <PlayCircle className="mr-2 h-4 w-4" />
            Retomar atendimento
          </Button>
        )}

        <Separator className="my-4" />

        {/* Estágio da reserva */}
        <div className="space-y-2">
          <h4 className="text-sm font-medium">Estágio</h4>
          <Etapa
            ok={!!aceite}
            label="Termos assinados"
            hint={aceite ? formatDate(aceite.aceitoEm) : 'pendente'}
          />
          <Etapa
            ok={!!reserva.sinalPago || reserva.pagamentoStatus === 'CONFIRMADO'}
            label="Pagamento/sinal"
            hint={
              reserva.pagamentoStatus
                ? reserva.pagamentoStatus.toLowerCase()
                : reserva.sinalPago
                  ? 'pago'
                  : 'pendente'
            }
          />
          {hab ? (
            <Etapa
              ok={!!hab.resolvida}
              label={ema ? 'GRU (habilitação EMA)' : 'CHA informada'}
              hint={ema ? (gruPaga ? 'paga' : 'pendente') : hab.chaNumero || ''}
            />
          ) : (
            <Etapa ok={false} label="Habilitação" hint="não registrada" />
          )}
          <Etapa
            ok={!!reserva.documentoEmitidoEm}
            label="Documentos emitidos"
            hint={reserva.documentoEmitidoEm ? formatDate(reserva.documentoEmitidoEm) : 'pendente'}
          />
        </div>

        {/* Checklist detalhado dos pré-requisitos (EMA) — visibilidade do que foi entregue */}
        {ema && hab && (
          <>
            <Separator className="my-4" />
            <div className="space-y-2">
              <h4 className="text-sm font-medium">Pré-requisitos (EMA)</h4>
              <Etapa ok={!!hab.videoaulaEm} label="Videoaula assistida" />
              <Etapa ok={!!hab.anexoSaude} label="Autodeclaração de saúde (5-C)" />
              <Etapa ok={!!hab.anexoRegras} label="Anexo de regras de navegação" />
              <Etapa ok={!!hab.anexoResidencia} label="Comprovante/Declaração de residência" />
              <Etapa ok={!!hab.instrutorId} label="Instrutor (atestado de demonstração)" />
              <Etapa
                ok={!!hab.gruPago}
                label="GRU paga"
                hint={hab.gruNumero ? `nº ${hab.gruNumero}` : ''}
              />
            </div>
          </>
        )}

        {/* Pagamento da GRU quando pendente */}
        {mostrarPagamentoGru && (
          <>
            <Separator className="my-4" />
            <div className="space-y-3">
              <h4 className="text-sm font-medium">Pagamento da GRU</h4>
              {hab?.gruValor != null && (
                <p className="text-sm">
                  Valor: <strong>{formatCurrency(hab.gruValor)}</strong>
                  {hab.gruNumero && (
                    <span className="ml-2 text-muted-foreground">GRU {hab.gruNumero}</span>
                  )}
                </p>
              )}

              {(temPix || temBoleto) && (
                <Button
                  type="button"
                  className="w-full"
                  disabled={verificarPagamento.isPending}
                  onClick={() => verificarPagamento.mutate()}
                >
                  {verificarPagamento.isPending ? (
                    <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                  ) : (
                    <RefreshCw className="mr-2 h-4 w-4" />
                  )}
                  Verificar pagamento
                </Button>
              )}

              {temPix && (
                <div className="space-y-2 rounded-md border border-emerald-200 bg-emerald-50 p-3 dark:border-emerald-900 dark:bg-emerald-950/40">
                  <div className="flex items-center gap-2 text-sm font-medium">
                    <QrCode className="h-4 w-4" /> PIX copia-e-cola
                  </div>
                  {hab?.gruPixExpiracao && (
                    <p className="text-xs text-muted-foreground">
                      vence {new Date(hab.gruPixExpiracao).toLocaleString('pt-BR')}
                    </p>
                  )}
                  <div className="flex items-center gap-2">
                    <code className="flex-1 truncate rounded bg-background px-2 py-1 text-xs">
                      {hab?.gruPixCopiaECola}
                    </code>
                    <Button
                      type="button"
                      size="sm"
                      variant="outline"
                      onClick={() => {
                        navigator.clipboard.writeText(hab!.gruPixCopiaECola!)
                        toast.success('PIX copiado.')
                      }}
                    >
                      <Copy className="h-3.5 w-3.5" />
                    </Button>
                  </div>
                </div>
              )}

              {temBoleto && (
                <Button
                  type="button"
                  variant="outline"
                  className="w-full"
                  disabled={baixarBoleto.isPending}
                  onClick={() => baixarBoleto.mutate()}
                >
                  <FileDown className="mr-2 h-4 w-4" />
                  {baixarBoleto.isPending ? 'Abrindo…' : 'Baixar boleto (PDF)'}
                </Button>
              )}

              {/* Gerar se ainda não houver */}
              <div className="flex gap-2">
                {!temPix && (
                  <Button
                    type="button"
                    size="sm"
                    variant="secondary"
                    className="flex-1"
                    disabled={gerarPix.isPending}
                    onClick={() => gerarPix.mutate()}
                  >
                    {gerarPix.isPending && <Loader2 className="mr-2 h-3.5 w-3.5 animate-spin" />}
                    Gerar PIX
                  </Button>
                )}
                {!temBoleto && (
                  <Button
                    type="button"
                    size="sm"
                    variant="outline"
                    className="flex-1"
                    disabled={gerarBoleto.isPending}
                    onClick={() => gerarBoleto.mutate()}
                  >
                    {gerarBoleto.isPending && <Loader2 className="mr-2 h-3.5 w-3.5 animate-spin" />}
                    Gerar boleto
                  </Button>
                )}
              </div>
            </div>
          </>
        )}

        {ema && gruPaga && hab?.gruComprovanteDisponivel && (
          <>
            <Separator className="my-4" />
            <Button
              type="button"
              variant="outline"
              className="w-full"
              disabled={baixarComprovante.isPending}
              onClick={() => baixarComprovante.mutate()}
            >
              <Receipt className="mr-2 h-4 w-4" />
              {baixarComprovante.isPending ? 'Abrindo…' : 'Baixar comprovante (PDF)'}
            </Button>
          </>
        )}
      </SheetContent>
    </Sheet>
  )
}
