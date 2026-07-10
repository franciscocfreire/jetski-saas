'use client'

import { useEffect, useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { toast } from 'sonner'
import { CheckCircle2, Copy, CreditCard, Loader2, Mail, QrCode } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { PixQrCode } from '@/components/pix-qrcode'
import { waHref, WhatsAppIcon } from '@/components/whatsapp-link'
import { reservasService } from '@/lib/api/services'
import type { FormaPagamento, Reserva, ReservaPix } from '@/lib/api/types'
import type { Atendimento } from '../types'

const FORMAS: { value: FormaPagamento; label: string }[] = [
  { value: 'DINHEIRO', label: 'Dinheiro' },
  { value: 'PIX', label: 'PIX' },
  { value: 'CARTAO_CREDITO', label: 'Cartão de crédito' },
  { value: 'CARTAO_DEBITO', label: 'Cartão de débito' },
  { value: 'OUTRO', label: 'Outro' },
]

/**
 * Pagamento presencial INTEGRAL do balcão (antes do embarque). O valor
 * digitado é o efetivamente cobrado (desconto de balcão permitido) e vira o
 * valor total da reserva. "Registrar depois" não bloqueia a emissão — a
 * reserva fica "Pagamento na loja" no portal até o registro.
 */
export function StepPagamento({
  atendimento,
  onBack,
  onDone,
  onSkip,
}: {
  atendimento: Atendimento
  onBack: () => void
  onDone: (reserva: Reserva) => void
  onSkip: () => void
}) {
  const reserva = atendimento.reserva
  const modelo = atendimento.modelo

  const duracaoMin =
    reserva
      ? Math.round(
          (new Date(reserva.dataFimPrevista).getTime() - new Date(reserva.dataInicio).getTime()) /
            60_000
        )
      : 0
  const valorEstimado = modelo ? (modelo.precoBaseHora * duracaoMin) / 60 : 0

  const jaPago = reserva?.pagamentoStatus === 'CONFIRMADO'

  const [forma, setForma] = useState<FormaPagamento>('DINHEIRO')
  const [valor, setValor] = useState(valorEstimado > 0 ? valorEstimado.toFixed(2) : '')
  const [observacao, setObservacao] = useState('')

  // PIX gerado para o valor atual — descartado se o valor/forma mudar.
  const [pix, setPix] = useState<ReservaPix | null>(null)
  useEffect(() => {
    setPix(null)
  }, [valor, forma])

  const clienteEmail = atendimento.cliente?.email
  const clienteFone = atendimento.cliente?.whatsapp || atendimento.cliente?.telefone
  const primeiroNome = atendimento.cliente?.nome?.split(' ')[0]

  const erroMsg = (e: unknown, fallback: string) =>
    (e as { response?: { data?: { message?: string } } })?.response?.data?.message ?? fallback

  const gerarQr = useMutation({
    mutationFn: () => reservasService.gerarPix(reserva!.id, Number(valor)),
    onSuccess: (p) => setPix(p),
    onError: (e: unknown) => toast.error(erroMsg(e, 'Falha ao gerar o PIX.')),
  })

  const enviarEmail = useMutation({
    mutationFn: () => reservasService.enviarPixEmail(reserva!.id, Number(valor)),
    onSuccess: ({ email }) => toast.success(`PIX copia-e-cola enviado para ${email}.`),
    onError: (e: unknown) => toast.error(erroMsg(e, 'Falha ao enviar o PIX por e-mail.')),
  })

  const abrirWhatsApp = (p: ReservaPix) => {
    const msg =
      `Olá${primeiroNome ? `, ${primeiroNome}` : ''}! Segue o PIX de R$ ${p.valor.toFixed(2)} ` +
      `para o pagamento do seu passeio.\n\nPIX copia e cola:\n${p.copiaECola}`
    const href = waHref(clienteFone, msg)
    if (!href) return
    const aberta = window.open(href, '_blank', 'noopener,noreferrer')
    if (!aberta) toast.error('O navegador bloqueou a janela — libere popups e tente de novo.')
  }

  const enviarWhatsApp = useMutation({
    mutationFn: () => reservasService.gerarPix(reserva!.id, Number(valor)),
    onSuccess: (p) => {
      setPix(p)
      abrirWhatsApp(p)
    },
    onError: (e: unknown) => toast.error(erroMsg(e, 'Falha ao gerar o PIX.')),
  })

  const copiarPix = async () => {
    if (!pix) return
    try {
      await navigator.clipboard.writeText(pix.copiaECola)
      toast.success('Código PIX copiado.')
    } catch {
      toast.error('Não foi possível copiar — selecione o código manualmente.')
    }
  }

  const registrar = useMutation({
    mutationFn: () =>
      reservasService.registrarPagamento(reserva!.id, {
        forma,
        valor: Number(valor),
        observacao: observacao.trim() || undefined,
      }),
    onSuccess: (atualizada) => {
      toast.success('Pagamento registrado — reserva paga.')
      onDone(atualizada)
    },
    onError: (e: unknown) => {
      const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
      toast.error(msg ?? 'Falha ao registrar o pagamento.')
    },
  })

  if (jaPago) {
    return (
      <div className="space-y-5">
        <div className="flex items-center gap-3 rounded-lg border border-emerald-200 bg-emerald-50 p-4 text-emerald-800">
          <CheckCircle2 className="h-6 w-6 shrink-0" />
          <div className="text-sm">
            <p className="font-medium">Pagamento já registrado</p>
            <p>
              Valor total: <b>R$ {(reserva?.valorTotal ?? 0).toFixed(2)}</b>
            </p>
          </div>
        </div>
        <div className="flex justify-between">
          <Button type="button" variant="outline" onClick={onBack}>
            Voltar
          </Button>
          <Button type="button" onClick={() => onDone(reserva!)}>
            Continuar
          </Button>
        </div>
      </div>
    )
  }

  const valorNum = Number(valor)
  const valido = Number.isFinite(valorNum) && valorNum > 0

  return (
    <div className="space-y-5">
      <div className="flex items-start gap-3 rounded-lg border bg-muted/30 p-4">
        <CreditCard className="mt-0.5 h-5 w-5 shrink-0 text-primary" />
        <div className="text-sm">
          <p className="font-medium">Pagamento integral no balcão</p>
          <p className="text-muted-foreground">
            O cliente paga o passeio antes do embarque. Valor estimado:{' '}
            <b>R$ {valorEstimado.toFixed(2)}</b> ({modelo?.nome}, {duracaoMin} min). O valor
            registrado abaixo é o efetivamente cobrado.
          </p>
        </div>
      </div>

      <div className="grid gap-4 sm:grid-cols-2">
        <div>
          <Label className="text-xs">Forma de pagamento</Label>
          <Select value={forma} onValueChange={(v) => setForma(v as FormaPagamento)}>
            <SelectTrigger className="h-9">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {FORMAS.map((f) => (
                <SelectItem key={f.value} value={f.value}>
                  {f.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
        <div>
          <Label className="text-xs">Valor cobrado (R$)</Label>
          <Input
            type="number"
            min="0.01"
            step="0.01"
            value={valor}
            onChange={(e) => setValor(e.target.value)}
            className="h-9"
          />
        </div>
      </div>

      {forma === 'PIX' && (
        <div className="space-y-3 rounded-lg border bg-muted/30 p-4">
          <div className="text-sm">
            <p className="font-medium">Cobrança PIX</p>
            <p className="text-muted-foreground">
              Gere o QR Code para o cliente pagar agora ou envie o copia-e-cola. Após confirmar o
              recebimento, registre o pagamento.
            </p>
          </div>

          <div className="flex flex-wrap gap-2">
            <Button
              type="button"
              variant="outline"
              size="sm"
              disabled={!valido || gerarQr.isPending}
              onClick={() => gerarQr.mutate()}
            >
              {gerarQr.isPending ? (
                <Loader2 className="mr-1 h-4 w-4 animate-spin" />
              ) : (
                <QrCode className="mr-1 h-4 w-4" />
              )}
              Gerar QR Code
            </Button>
            <Button
              type="button"
              variant="outline"
              size="sm"
              disabled={!valido || !clienteEmail || enviarEmail.isPending}
              title={!clienteEmail ? 'Cliente sem e-mail cadastrado' : undefined}
              onClick={() => enviarEmail.mutate()}
            >
              {enviarEmail.isPending ? (
                <Loader2 className="mr-1 h-4 w-4 animate-spin" />
              ) : (
                <Mail className="mr-1 h-4 w-4" />
              )}
              Enviar por e-mail
            </Button>
            <Button
              type="button"
              variant="outline"
              size="sm"
              disabled={!valido || !clienteFone || enviarWhatsApp.isPending}
              title={!clienteFone ? 'Cliente sem telefone/WhatsApp cadastrado' : undefined}
              onClick={() => (pix ? abrirWhatsApp(pix) : enviarWhatsApp.mutate())}
            >
              {enviarWhatsApp.isPending ? (
                <Loader2 className="mr-1 h-4 w-4 animate-spin" />
              ) : (
                <WhatsAppIcon className="mr-1 h-4 w-4" />
              )}
              Enviar por WhatsApp
            </Button>
          </div>

          {(!clienteEmail || !clienteFone) && (
            <p className="text-xs text-muted-foreground">
              {!clienteEmail && 'Cliente sem e-mail cadastrado. '}
              {!clienteFone && 'Cliente sem telefone/WhatsApp cadastrado.'}
            </p>
          )}

          {pix && (
            <div className="flex flex-col items-center gap-2 rounded-md border bg-background p-4">
              <PixQrCode payload={pix.copiaECola} size={200} />
              <p className="text-xs text-muted-foreground">
                PIX de <b>R$ {pix.valor.toFixed(2)}</b> — chave {pix.chave}
              </p>
              <code className="max-w-full break-all rounded bg-muted px-2 py-1 text-[11px]">
                {pix.copiaECola}
              </code>
              <Button type="button" variant="ghost" size="sm" onClick={copiarPix}>
                <Copy className="mr-1 h-4 w-4" />
                Copiar código
              </Button>
            </div>
          )}
        </div>
      )}

      <div>
        <Label className="text-xs">Observação (opcional)</Label>
        <Textarea
          value={observacao}
          onChange={(e) => setObservacao(e.target.value)}
          placeholder="Ex.: desconto combinado, pagamento dividido…"
          rows={2}
        />
      </div>

      <div className="flex flex-wrap justify-between gap-2">
        <Button type="button" variant="outline" onClick={onBack}>
          Voltar
        </Button>
        <div className="flex gap-2">
          <Button type="button" variant="ghost" onClick={onSkip}>
            Registrar depois
          </Button>
          <Button
            type="button"
            disabled={!valido || registrar.isPending}
            onClick={() => registrar.mutate()}
          >
            {registrar.isPending && <Loader2 className="mr-1 h-4 w-4 animate-spin" />}
            Registrar pagamento
          </Button>
        </div>
      </div>
    </div>
  )
}
