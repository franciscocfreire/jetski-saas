'use client'

import { useEffect, useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useRouter } from 'next/navigation'
import { toast } from 'sonner'
import {
  CheckCircle2,
  Circle,
  Copy,
  FileDown,
  Loader2,
  Mail,
  Pencil,
  PlayCircle,
  QrCode,
  Receipt,
  FileText,
  RefreshCw,
  Save,
  Undo2,
  Upload,
  UserX,
  X,
  Ban,
} from 'lucide-react'
import { FileUpload } from '@/components/file-upload'
import {
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
  SheetDescription,
} from '@/components/ui/sheet'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Separator } from '@/components/ui/separator'
import { habilitacaoService, aceiteService, reservasService, clientesService } from '@/lib/api/services'
import { formatCurrency, formatDate, formatDateTime, toLocalDateTime } from '@/lib/utils'
import { PixQrCode } from '@/components/pix-qrcode'
import { waHref } from '@/components/whatsapp-link'
import { abrirPdfBlob } from '@/lib/pdf'
import { DocumentoPreviewButtons } from '@/components/documento-preview-buttons'
import type { FormaPagamento, Reserva } from '@/lib/api/types'

/** Glifo da marca WhatsApp (lucide não traz ícones de marca). */
function WhatsAppGlyph({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 24 24" fill="currentColor" className={className} aria-hidden="true">
      <path d="M.057 24l1.687-6.163a11.867 11.867 0 01-1.587-5.945C.16 5.335 5.495 0 12.05 0a11.817 11.817 0 018.413 3.488 11.824 11.824 0 013.48 8.414c-.003 6.557-5.338 11.892-11.893 11.892a11.9 11.9 0 01-5.688-1.448L.057 24zm6.597-3.807c1.676.995 3.276 1.591 5.392 1.592 5.448 0 9.886-4.434 9.889-9.885.002-5.462-4.415-9.89-9.881-9.892-5.452 0-9.887 4.434-9.889 9.884a9.86 9.86 0 001.51 5.26l-.999 3.648 3.978-1.04zm11.387-5.464c-.074-.124-.272-.198-.57-.347-.297-.149-1.758-.868-2.031-.967-.272-.099-.47-.149-.669.149-.198.297-.768.967-.941 1.165-.173.198-.347.223-.644.074-.297-.149-1.255-.462-2.39-1.475-.883-.788-1.48-1.761-1.653-2.059-.173-.297-.018-.458.13-.606.134-.133.297-.347.446-.521.151-.172.2-.296.3-.495.099-.198.05-.372-.025-.521-.075-.148-.669-1.611-.916-2.206-.242-.579-.487-.501-.669-.51l-.57-.01c-.198 0-.52.074-.792.372s-1.04 1.017-1.04 2.479 1.065 2.876 1.213 3.074c.149.198 2.096 3.2 5.077 4.487.709.306 1.262.489 1.694.626.712.226 1.36.194 1.872.118.571-.085 1.758-.719 2.006-1.413.247-.694.247-1.289.173-1.413z" />
    </svg>
  )
}

/** Duração (horas) implícita nas datas previstas. */
function horasDe(r: Reserva): number {
  const ms = new Date(r.dataFimPrevista).getTime() - new Date(r.dataInicio).getTime()
  return Math.max(1, Math.round(ms / 3_600_000))
}

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
  const [compModo, setCompModo] = useState(false)
  const [compFile, setCompFile] = useState<string | undefined>(undefined)
  const [devModo, setDevModo] = useState(false)
  const [devFile, setDevFile] = useState<string | undefined>(undefined)

  // Fase 2 — visualizar → editar → salvar. Abrir = somente leitura; só vira edição
  // quando o operador clica "Editar". Sem estado persistido EM_EDICAO: é modo de tela.
  const [editMode, setEditMode] = useState(false)
  const [obs, setObs] = useState('')
  const [dur, setDur] = useState(1)

  // Reseta o modo/valores ao trocar de reserva ou reabrir o drawer.
  useEffect(() => {
    setEditMode(false)
    setObs(reserva?.observacoes ?? '')
    setDur(reserva ? horasDe(reserva) : 1)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [reserva?.id, open])

  const dirty =
    editMode && reserva != null && (obs !== (reserva.observacoes ?? '') || dur !== horasDe(reserva))

  // Aviso ao fechar a aba/navegador com alterações não salvas.
  useEffect(() => {
    if (!dirty) return
    const handler = (e: BeforeUnloadEvent) => {
      e.preventDefault()
      e.returnValue = ''
    }
    window.addEventListener('beforeunload', handler)
    return () => window.removeEventListener('beforeunload', handler)
  }, [dirty])

  const salvarEdicao = useMutation({
    mutationFn: () => {
      const inicio = new Date(reserva!.dataInicio)
      const fim = new Date(inicio.getTime() + dur * 3_600_000)
      return reservasService.atualizar(reservaId!, {
        observacoes: obs,
        dataFimPrevista: toLocalDateTime(fim),
      })
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['reservas'] })
      setEditMode(false)
      toast.success('Reserva atualizada.')
    },
    onError: (e: unknown) => {
      const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
      toast.error(msg ?? 'Falha ao salvar a reserva.')
    },
  })

  const cancelar = useMutation({
    mutationFn: () => reservasService.cancelar(reservaId!),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['reservas'] })
      toast.success('Reserva cancelada.')
      onOpenChange(false)
    },
    onError: (e: unknown) => {
      const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
      toast.error(msg ?? 'Falha ao cancelar a reserva.')
    },
  })

  // Pagamento presencial (balcão) — dialog forma+valor
  const [pagOpen, setPagOpen] = useState(false)
  const [pagForma, setPagForma] = useState<FormaPagamento>('DINHEIRO')
  const [pagValor, setPagValor] = useState('')
  const registrarPagamento = useMutation({
    mutationFn: () =>
      reservasService.registrarPagamento(reservaId!, {
        forma: pagForma,
        valor: Number(pagValor),
      }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['reservas'] })
      setPagOpen(false)
      toast.success('Pagamento registrado — reserva paga.')
    },
    onError: (e: unknown) => {
      const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
      toast.error(msg ?? 'Falha ao registrar o pagamento.')
    },
  })

  // Extrato do folio da reserva (pagamentos/estornos) — compacto
  const { data: extratoReserva } = useQuery({
    queryKey: ['reserva-extrato', reservaId],
    queryFn: () => reservasService.extrato(reservaId!),
    enabled: open && !!reservaId && reserva?.pagamentoStatus === 'CONFIRMADO',
  })

  // Estorno (devolução) de reserva paga — dialog forma+valor+justificativa
  const [estornoOpen, setEstornoOpen] = useState(false)
  const [estornoForma, setEstornoForma] = useState<FormaPagamento>('DINHEIRO')
  const [estornoValor, setEstornoValor] = useState('')
  const [estornoObs, setEstornoObs] = useState('')
  const registrarEstorno = useMutation({
    mutationFn: () =>
      reservasService.registrarEstorno(reservaId!, {
        forma: estornoForma,
        valor: Number(estornoValor),
        observacao: estornoObs.trim(),
      }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['reservas'] })
      setEstornoOpen(false)
      toast.success('Estorno registrado.')
    },
    onError: (e: unknown) => {
      const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
      toast.error(msg ?? 'Falha ao registrar o estorno.')
    },
  })

  const marcarNoShow = useMutation({
    mutationFn: () => reservasService.marcarNoShow(reservaId!),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['reservas'] })
      toast.success('Reserva marcada como não comparecimento.')
      onOpenChange(false)
    },
    onError: (e: unknown) => {
      const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
      toast.error(msg ?? 'Falha ao marcar não comparecimento.')
    },
  })

  // Fecha o drawer com guarda de alterações não salvas.
  const fecharComGuarda = (v: boolean) => {
    if (!v && dirty && !window.confirm('Há alterações não salvas. Descartar?')) return
    onOpenChange(v)
  }

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
  // Anexos do cliente — para checar o documento de identidade (RG/CNH).
  const { data: anexosTipos } = useQuery({
    queryKey: ['cliente-anexos-tipos', reserva?.clienteId],
    queryFn: () => clientesService.listarAnexos(reserva!.clienteId),
    enabled: open && !!reserva?.clienteId,
  })
  const identidadeOk = (anexosTipos ?? []).some((a) => a.tipo === 'IDENTIDADE')
  const selfieOk = (anexosTipos ?? []).some((a) => a.tipo === 'SELFIE')

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

  // Abertura de PDF: a aba é aberta no clique (abrirPdfBlob) p/ funcionar no iOS.
  const [pdfBusy, setPdfBusy] = useState<'gerarBoleto' | 'baixarBoleto' | 'comprovante' | 'devolutiva' | null>(null)

  const gerarBoleto = () => {
    setPdfBusy('gerarBoleto')
    abrirPdfBlob(async () => {
      const r = await habilitacaoService.gerarBoleto(reservaId!)
      invalidar()
      if (!r.sucesso) {
        toast.warning('Não foi possível gerar o boleto automaticamente.')
        throw new Error('boleto não gerado')
      }
      toast.success('Boleto gerado.')
      return habilitacaoService.baixarBoleto(reservaId!)
    }, 'boleto.pdf')
      .catch(() => {})
      .finally(() => setPdfBusy(null))
  }

  const baixarBoleto = () => {
    setPdfBusy('baixarBoleto')
    abrirPdfBlob(() => habilitacaoService.baixarBoleto(reservaId!), 'boleto.pdf')
      .catch(() => toast.error('Falha ao baixar o boleto.'))
      .finally(() => setPdfBusy(null))
  }

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

  const baixarComprovante = () => {
    setPdfBusy('comprovante')
    abrirPdfBlob(() => habilitacaoService.baixarComprovante(reservaId!), 'comprovante.pdf')
      .catch(() => toast.error('Falha ao baixar o comprovante.'))
      .finally(() => setPdfBusy(null))
  }

  const enviarComprovante = useMutation({
    mutationFn: () => habilitacaoService.registrarComprovante(reservaId!, compFile!),
    onSuccess: () => {
      invalidar()
      setCompModo(false)
      setCompFile(undefined)
      toast.success('Comprovante registrado — GRU marcada como paga.')
    },
    onError: () => toast.error('Falha ao registrar o comprovante.'),
  })

  const enviarDevolutiva = useMutation({
    mutationFn: () => habilitacaoService.registrarDevolutiva(reservaId!, devFile!),
    onSuccess: () => {
      invalidar()
      setDevModo(false)
      setDevFile(undefined)
      toast.success('Devolutiva anexada — CHA-MTA-E confirmada pela Marinha.')
    },
    onError: () => toast.error('Falha ao anexar a devolutiva.'),
  })

  const baixarDevolutiva = () => {
    setPdfBusy('devolutiva')
    abrirPdfBlob(() => habilitacaoService.baixarDevolutiva(reservaId!), 'cha-mtae-confirmada.pdf')
      .catch(() => toast.error('Falha ao baixar a devolutiva.'))
      .finally(() => setPdfBusy(null))
  }

  const enviarEmailGru = useMutation({
    mutationFn: () => habilitacaoService.enviarEmailGru(reservaId!),
    onSuccess: (enviado) =>
      enviado
        ? toast.success('E-mail da GRU enviado ao cliente.')
        : toast.warning('Cliente sem e-mail ou GRU ainda não gerada.'),
    onError: () => toast.error('Falha ao enviar o e-mail da GRU.'),
  })

  if (!reserva) return null

  // canal da reserva é a fonte precisa (V030); cliente.origem é fallback
  // (cliente nem sempre vem carregado no objeto da reserva)
  const online = (reserva.canal ?? reserva.cliente?.origem) === 'PORTAL'
  const ema = hab?.via === 'EMA'
  const gruPaga = !!hab?.gruPago
  // Editável enquanto não-terminal (rascunho/pendente/confirmada).
  const editavel =
    reserva.status === 'RASCUNHO' ||
    reserva.status === 'PENDENTE' ||
    reserva.status === 'CONFIRMADA'
  const inicio = new Date(reserva.dataInicio)
  const hora = inicio.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })

  // habilitação pendente (EMA sem GRU paga) → mostrar opções de pagamento
  const mostrarPagamentoGru = ema && !gruPaga
  const temPix = !!hab?.gruPixCopiaECola
  const temBoleto = !!hab?.gruBoletoDisponivel

  return (
    <Sheet open={open} onOpenChange={fecharComGuarda}>
      <SheetContent className="w-full overflow-y-auto sm:max-w-md">
        <SheetHeader>
          <SheetTitle className="flex items-center gap-2">
            {reserva.cliente?.nome || 'Reserva'}
            <span className="rounded bg-muted px-1.5 py-0.5 font-mono text-xs font-normal text-muted-foreground">
              #{reserva.id.slice(0, 8)}
            </span>
          </SheetTitle>
          <SheetDescription>
            {reserva.modelo?.nome} · {formatDate(reserva.dataInicio)} às {hora}
          </SheetDescription>
        </SheetHeader>

        <div className="mt-4 flex flex-wrap gap-2">
          <Badge variant={online ? 'default' : 'secondary'}>
            {online ? 'Portal' : 'Balcão'}
          </Badge>
          <Badge
            variant={
              reserva.status === 'CONFIRMADA'
                ? 'success'
                : reserva.status === 'NO_SHOW'
                  ? 'secondary'
                  : 'warning'
            }
          >
            {reserva.status === 'NO_SHOW' ? 'Não compareceu' : reserva.status}
          </Badge>
          <Badge variant={reserva.pagamentoStatus === 'CONFIRMADO' ? 'success' : 'secondary'}>
            {reserva.pagamentoStatus === 'CONFIRMADO'
              ? 'Pago'
              : online
                ? 'Aguardando sinal'
                : 'Pagamento na loja'}
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

        {!online && editavel && reserva.pagamentoStatus !== 'CONFIRMADO' && (
          <Dialog open={pagOpen} onOpenChange={setPagOpen}>
            <DialogTrigger asChild>
              <Button variant="outline" className="mt-2 w-full">
                <Receipt className="mr-2 h-4 w-4" />
                Registrar pagamento
              </Button>
            </DialogTrigger>
            <DialogContent className="sm:max-w-sm">
              <DialogHeader>
                <DialogTitle>Registrar pagamento presencial</DialogTitle>
                <DialogDescription>
                  Pagamento integral recebido no balcão. O valor informado é o efetivamente
                  cobrado.
                </DialogDescription>
              </DialogHeader>
              <div className="grid gap-3">
                <div>
                  <Label className="text-xs">Forma de pagamento</Label>
                  <Select value={pagForma} onValueChange={(v) => setPagForma(v as FormaPagamento)}>
                    <SelectTrigger className="h-9">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="DINHEIRO">Dinheiro</SelectItem>
                      <SelectItem value="PIX">PIX</SelectItem>
                      <SelectItem value="CARTAO_CREDITO">Cartão de crédito</SelectItem>
                      <SelectItem value="CARTAO_DEBITO">Cartão de débito</SelectItem>
                      <SelectItem value="OUTRO">Outro</SelectItem>
                    </SelectContent>
                  </Select>
                </div>
                <div>
                  <Label className="text-xs">Valor cobrado (R$)</Label>
                  <Input
                    type="number"
                    min="0.01"
                    step="0.01"
                    value={pagValor}
                    onChange={(e) => setPagValor(e.target.value)}
                    className="h-9"
                  />
                </div>
              </div>
              <DialogFooter>
                <Button
                  disabled={!(Number(pagValor) > 0) || registrarPagamento.isPending}
                  onClick={() => registrarPagamento.mutate()}
                >
                  {registrarPagamento.isPending && (
                    <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                  )}
                  Confirmar
                </Button>
              </DialogFooter>
            </DialogContent>
          </Dialog>
        )}

        {extratoReserva && extratoReserva.lancamentos.length > 0 && (
          <div className="mt-3 space-y-1 rounded-md border p-3 text-xs">
            <p className="font-medium">Extrato</p>
            {extratoReserva.lancamentos.map((l) => (
              <div key={l.id} className="flex justify-between text-muted-foreground">
                <span>
                  {l.tipo === 'PAGAMENTO' ? 'Pagamento' : l.tipo === 'ESTORNO' ? 'Estorno' : l.tipo}
                  {l.forma ? ` · ${l.forma}` : ''} · {formatDate(l.createdAt)}
                </span>
                <span className={l.tipo === 'ESTORNO' ? 'text-red-600' : ''}>
                  {formatCurrency(l.valor)}
                </span>
              </div>
            ))}
          </div>
        )}

        {reserva.pagamentoStatus === 'CONFIRMADO' && (
          <Dialog open={estornoOpen} onOpenChange={setEstornoOpen}>
            <DialogTrigger asChild>
              <Button variant="outline" className="mt-2 w-full">
                <Undo2 className="mr-2 h-4 w-4" />
                Registrar estorno
              </Button>
            </DialogTrigger>
            <DialogContent className="sm:max-w-sm">
              <DialogHeader>
                <DialogTitle>Registrar estorno (devolução)</DialogTitle>
                <DialogDescription>
                  Registre quando a loja devolver o valor ao cliente (cancelamento/não
                  comparecimento). Nunca excede o recebido.
                </DialogDescription>
              </DialogHeader>
              <div className="grid gap-3">
                <div>
                  <Label className="text-xs">Forma da devolução</Label>
                  <Select value={estornoForma} onValueChange={(v) => setEstornoForma(v as FormaPagamento)}>
                    <SelectTrigger className="h-9">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="DINHEIRO">Dinheiro</SelectItem>
                      <SelectItem value="PIX">PIX</SelectItem>
                      <SelectItem value="CARTAO_CREDITO">Cartão de crédito</SelectItem>
                      <SelectItem value="CARTAO_DEBITO">Cartão de débito</SelectItem>
                      <SelectItem value="OUTRO">Outro</SelectItem>
                    </SelectContent>
                  </Select>
                </div>
                <div>
                  <Label className="text-xs">Valor devolvido (R$)</Label>
                  <Input
                    type="number"
                    min="0.01"
                    step="0.01"
                    value={estornoValor}
                    onChange={(e) => setEstornoValor(e.target.value)}
                    className="h-9"
                  />
                </div>
                <div>
                  <Label className="text-xs">Justificativa (obrigatória)</Label>
                  <Input
                    value={estornoObs}
                    onChange={(e) => setEstornoObs(e.target.value)}
                    placeholder="Ex.: cancelou por chuva — devolução combinada"
                    className="h-9"
                  />
                </div>
              </div>
              <DialogFooter>
                <Button
                  disabled={!(Number(estornoValor) > 0) || !estornoObs.trim() || registrarEstorno.isPending}
                  onClick={() => registrarEstorno.mutate()}
                >
                  {registrarEstorno.isPending && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
                  Confirmar estorno
                </Button>
              </DialogFooter>
            </DialogContent>
          </Dialog>
        )}

        {(reserva.status === 'PENDENTE' || reserva.status === 'CONFIRMADA') &&
          new Date(reserva.dataInicio) < new Date() && (
            <Button
              variant="outline"
              className="mt-2 w-full"
              disabled={marcarNoShow.isPending}
              onClick={() => {
                const paga = reserva.pagamentoStatus === 'CONFIRMADO'
                if (
                  window.confirm(
                    `Marcar não comparecimento de ${reserva.cliente?.nome ?? 'cliente'}? A reserva sai da agenda.` +
                      (paga
                        ? '\n\nATENÇÃO: esta reserva está PAGA — se a loja devolver o valor, registre o estorno depois.'
                        : '')
                  )
                )
                  marcarNoShow.mutate()
              }}
            >
              {marcarNoShow.isPending ? (
                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
              ) : (
                <UserX className="mr-2 h-4 w-4" />
              )}
              Marcar não comparecimento
            </Button>
          )}

        {editavel && (
          <Button
            variant="outline"
            className="mt-2 w-full border-red-300 text-red-600 hover:bg-red-50 hover:text-red-700 dark:border-red-900 dark:hover:bg-red-950/40"
            disabled={cancelar.isPending}
            onClick={() => {
              const paga = reserva.pagamentoStatus === 'CONFIRMADO'
              if (
                window.confirm(
                  `Cancelar a reserva de ${reserva.cliente?.nome ?? 'cliente'}? Esta ação não pode ser desfeita.` +
                    (paga
                      ? '\n\nATENÇÃO: esta reserva está PAGA — se a loja devolver o valor, registre o estorno depois.'
                      : '')
                )
              )
                cancelar.mutate()
            }}
          >
            {cancelar.isPending ? (
              <Loader2 className="mr-2 h-4 w-4 animate-spin" />
            ) : (
              <Ban className="mr-2 h-4 w-4" />
            )}
            Cancelar reserva
          </Button>
        )}

        <Separator className="my-4" />

        {/* Detalhes da reserva — visualizar → editar → salvar (Fase 2) */}
        <div className="space-y-2">
          <div className="flex items-center justify-between">
            <h4 className="text-sm font-medium">Detalhes da reserva</h4>
            {editavel && !editMode && (
              <Button variant="ghost" size="sm" onClick={() => setEditMode(true)}>
                <Pencil className="mr-1 h-3.5 w-3.5" /> Editar
              </Button>
            )}
            {editMode && dirty && <Badge variant="warning">Não salvo</Badge>}
          </div>

          {!editMode ? (
            <div className="space-y-1 text-sm">
              <p>
                <span className="text-muted-foreground">Duração: </span>
                <span className="font-medium">{horasDe(reserva)} h</span>
              </p>
              <p>
                <span className="text-muted-foreground">Observações: </span>
                <span className="font-medium">{reserva.observacoes || '—'}</span>
              </p>
            </div>
          ) : (
            <div className="space-y-3">
              <div>
                <Label className="text-xs">Duração (horas)</Label>
                <Input
                  type="number"
                  min={1}
                  max={12}
                  value={dur}
                  onChange={(e) => setDur(Math.max(1, Number(e.target.value) || 1))}
                />
              </div>
              <div>
                <Label className="text-xs">Observações</Label>
                <Input
                  value={obs}
                  onChange={(e) => setObs(e.target.value)}
                  placeholder="Anotações da reserva"
                />
              </div>
              <div className="flex gap-2">
                <Button
                  size="sm"
                  disabled={!dirty || salvarEdicao.isPending}
                  onClick={() => salvarEdicao.mutate()}
                >
                  {salvarEdicao.isPending ? (
                    <Loader2 className="mr-1 h-3.5 w-3.5 animate-spin" />
                  ) : (
                    <Save className="mr-1 h-3.5 w-3.5" />
                  )}
                  Salvar
                </Button>
                <Button
                  size="sm"
                  variant="ghost"
                  onClick={() => {
                    if (dirty && !window.confirm('Descartar alterações?')) return
                    setEditMode(false)
                    setObs(reserva.observacoes ?? '')
                    setDur(horasDe(reserva))
                  }}
                >
                  <X className="mr-1 h-3.5 w-3.5" /> Cancelar
                </Button>
              </div>
            </div>
          )}
        </div>

        <Separator className="my-4" />

        {/* Estágio da reserva */}
        <div className="space-y-2">
          <h4 className="text-sm font-medium">Estágio</h4>
          <Etapa
            ok={!!aceite}
            label="Termos assinados"
            hint={aceite ? formatDateTime(aceite.aceitoEm) : 'pendente'}
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
          {ema && (
            <Etapa
              ok={!!hab?.gruComprovanteDisponivel}
              label="Comprovante de pagamento (GRU)"
              hint={
                hab?.gruComprovanteDisponivel
                  ? 'anexado'
                  : gruPaga
                    ? 'paga, sem comprovante'
                    : 'pendente'
              }
            />
          )}
          {/* Pagamento do passeio (balcão integral / sinal do portal) — "Registrar depois" fica pendente aqui. */}
          <Etapa
            ok={reserva.pagamentoStatus === 'CONFIRMADO'}
            label="Pagamento do passeio"
            hint={
              reserva.pagamentoStatus === 'CONFIRMADO'
                ? reserva.valorTotal != null
                  ? `pago — R$ ${reserva.valorTotal.toFixed(2)}`
                  : 'pago'
                : reserva.pagamentoStatus === 'EM_ANALISE'
                  ? 'comprovante em análise'
                  : reserva.pagamentoStatus === 'RECUSADO'
                    ? 'recusado — cobrar novamente'
                    : online
                      ? 'aguardando PIX do cliente'
                      : 'pendente — registrar na loja'
            }
          />
          <Etapa
            ok={!!reserva.documentoEmitidoEm}
            label="Documentos emitidos"
            hint={reserva.documentoEmitidoEm ? formatDateTime(reserva.documentoEmitidoEm) : 'pendente'}
          />
          <DocumentoPreviewButtons reservaId={reserva.id} className="pt-2" />
        </div>

        {/* Documentação EMA — entregáveis da escola (sem repetir GRU/comprovante, que vivem no Estágio) */}
        {ema && hab && (
          <>
            <Separator className="my-4" />
            <div className="space-y-2">
              <h4 className="text-sm font-medium">Documentação EMA</h4>
              <Etapa ok={identidadeOk} label="Documento de identidade (RG/CNH)" />
              <Etapa ok={selfieOk} label="Selfie / foto do cliente" />
              <Etapa ok={!!hab.videoaulaEm} label="Videoaula assistida" />
              <Etapa ok={!!hab.anexoSaude} label="Autodeclaração de saúde (5-C)" />
              <Etapa ok={!!hab.anexoRegras} label="Anexo de regras de navegação" />
              <Etapa ok={!!hab.anexoResidencia} label="Comprovante/Declaração de residência" />
              <Etapa ok={!!hab.instrutorId} label="Instrutor (atestado de demonstração)" />
            </div>

            {/* Devolutiva da Marinha — resposta manual por e-mail à loja após a emissão */}
            {!!reserva.documentoEmitidoEm && (
              <>
                <Separator className="my-4" />
                <div className="space-y-2">
                  <h4 className="text-sm font-medium">Confirmação da Marinha</h4>
                  <Etapa
                    ok={!!hab.marinhaConfirmadaEm}
                    label="CHA-MTA-E confirmada pela Marinha"
                    hint={
                      hab.marinhaConfirmadaEm
                        ? formatDateTime(hab.marinhaConfirmadaEm)
                        : 'aguardando devolutiva (e-mail da Marinha à loja)'
                    }
                  />
                  <p className="text-xs text-muted-foreground">
                    A confirmação chega por e-mail à loja. Anexá-la libera o reuso da
                    temporária pelo cliente por 30 dias.
                  </p>
                  {!devModo ? (
                    <Button
                      type="button"
                      variant="outline"
                      size="sm"
                      className="h-auto w-full whitespace-normal py-2"
                      onClick={() => setDevModo(true)}
                    >
                      <Upload className="mr-2 h-4 w-4 shrink-0" />
                      {hab.devolutivaDisponivel
                        ? 'Substituir devolutiva da Marinha'
                        : 'Anexar devolutiva da Marinha'}
                    </Button>
                  ) : (
                    <div className="space-y-2 rounded-md border border-dashed p-3">
                      <p className="text-xs font-medium">Documento devolvido pela Marinha (PDF ou foto)</p>
                      <FileUpload
                        label="Enviar/tirar foto da devolutiva"
                        accept="image/*,application/pdf"
                        tipoDocumento="GRU_COMPROVANTE"
                        onChange={(f) => setDevFile(f?.dataUrl)}
                      />
                      <div className="flex gap-2">
                        <Button
                          type="button"
                          size="sm"
                          disabled={!devFile || enviarDevolutiva.isPending}
                          onClick={() => enviarDevolutiva.mutate()}
                        >
                          {enviarDevolutiva.isPending ? 'Enviando…' : 'Confirmar CHA-MTA-E'}
                        </Button>
                        <Button
                          type="button"
                          size="sm"
                          variant="ghost"
                          onClick={() => {
                            setDevModo(false)
                            setDevFile(undefined)
                          }}
                        >
                          Cancelar
                        </Button>
                      </div>
                    </div>
                  )}
                  {hab.devolutivaDisponivel && (
                    <Button
                      type="button"
                      variant="outline"
                      size="sm"
                      className="w-full"
                      disabled={pdfBusy === 'devolutiva'}
                      onClick={baixarDevolutiva}
                    >
                      {pdfBusy === 'devolutiva' ? (
                        <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                      ) : (
                        <FileText className="mr-2 h-4 w-4" />
                      )}
                      Baixar devolutiva (PDF)
                    </Button>
                  )}
                </div>
              </>
            )}

            <Separator className="my-4" />
            <div className="space-y-2">
              <h4 className="text-sm font-medium">Dados pessoais (NORMAM-212)</h4>
              <p className="text-xs text-muted-foreground">
                Necessários para emitir os documentos (não bloqueiam a reserva).
              </p>
              <Etapa ok={!!reserva.cliente?.nacionalidade} label="Nacionalidade" />
              <Etapa ok={!!reserva.cliente?.naturalidade} label="Naturalidade" />
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

              {/* Comprovante manual — pago por outro meio ou verificação não funcionou */}
              {!compModo ? (
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  className="h-auto w-full whitespace-normal py-2"
                  onClick={() => setCompModo(true)}
                >
                  <Upload className="mr-2 h-4 w-4 shrink-0" />
                  Anexar comprovante (pago por outro meio)
                </Button>
              ) : (
                <div className="space-y-2 rounded-md border border-dashed p-3">
                  <p className="text-xs font-medium">Comprovante de pagamento (obrigatório)</p>
                  <FileUpload
                    label="Enviar/tirar foto do comprovante"
                    accept="image/*,application/pdf"
                    tipoDocumento="GRU_COMPROVANTE"
                    onChange={(f) => setCompFile(f?.dataUrl)}
                  />
                  <div className="flex gap-2">
                    <Button
                      type="button"
                      size="sm"
                      disabled={!compFile || enviarComprovante.isPending}
                      onClick={() => enviarComprovante.mutate()}
                    >
                      {enviarComprovante.isPending ? 'Enviando…' : 'Confirmar com comprovante'}
                    </Button>
                    <Button
                      type="button"
                      size="sm"
                      variant="ghost"
                      onClick={() => {
                        setCompModo(false)
                        setCompFile(undefined)
                      }}
                    >
                      Cancelar
                    </Button>
                  </div>
                </div>
              )}

              {hab?.gruNumero && (
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  className="w-full"
                  disabled={enviarEmailGru.isPending}
                  onClick={() => enviarEmailGru.mutate()}
                >
                  <Mail className="mr-2 h-4 w-4" />
                  {enviarEmailGru.isPending ? 'Enviando…' : 'Enviar GRU por e-mail ao cliente'}
                </Button>
              )}

              {temPix && (
                <div className="space-y-2 rounded-md border border-emerald-200 bg-emerald-50 p-3 dark:border-emerald-900 dark:bg-emerald-950/40">
                  <div className="flex items-center gap-2 text-sm font-medium">
                    <QrCode className="h-4 w-4" /> PIX da GRU
                  </div>
                  {hab?.gruPixExpiracao && (
                    <p className="text-xs text-muted-foreground">
                      vence {new Date(hab.gruPixExpiracao).toLocaleString('pt-BR')}
                    </p>
                  )}
                  {/* QR renderizado do copia-e-cola — persiste ao reabrir o atendimento */}
                  <div className="flex justify-center py-1">
                    <PixQrCode payload={hab?.gruPixCopiaECola} />
                  </div>
                  <div className="flex flex-wrap items-center gap-2">
                    <code className="w-full min-w-0 truncate rounded bg-background px-2 py-1 text-xs sm:flex-1">
                      {hab?.gruPixCopiaECola}
                    </code>
                    <Button
                      type="button"
                      size="sm"
                      variant="outline"
                      title="Copiar PIX"
                      className="h-10 w-10 sm:h-8 sm:w-8"
                      onClick={() => {
                        navigator.clipboard.writeText(hab!.gruPixCopiaECola!)
                        toast.success('PIX copiado.')
                      }}
                    >
                      <Copy className="h-3.5 w-3.5" />
                    </Button>
                    {(() => {
                      const href = waHref(
                        reserva.cliente?.telefone,
                        `Olá${reserva.cliente?.nome ? `, ${reserva.cliente.nome.split(' ')[0]}` : ''}! Segue o PIX da GRU${
                          hab?.gruValor != null ? ` (${formatCurrency(hab.gruValor)})` : ''
                        } para pagamento:\n\n${hab?.gruPixCopiaECola}`
                      )
                      return href ? (
                        <Button
                          asChild
                          type="button"
                          size="sm"
                          variant="outline"
                          title="Enviar PIX por WhatsApp"
                          className="h-10 w-10 text-emerald-600 hover:text-emerald-700 sm:h-8 sm:w-8 dark:text-emerald-400"
                        >
                          <a href={href} target="_blank" rel="noreferrer">
                            <WhatsAppGlyph className="h-3.5 w-3.5" />
                          </a>
                        </Button>
                      ) : null
                    })()}
                  </div>
                </div>
              )}

              {temBoleto && (
                <Button
                  type="button"
                  variant="outline"
                  className="w-full"
                  disabled={pdfBusy === 'baixarBoleto'}
                  onClick={baixarBoleto}
                >
                  <FileDown className="mr-2 h-4 w-4" />
                  {pdfBusy === 'baixarBoleto' ? 'Abrindo…' : 'Baixar boleto (PDF)'}
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
                    disabled={pdfBusy === 'gerarBoleto'}
                    onClick={gerarBoleto}
                  >
                    {pdfBusy === 'gerarBoleto' && <Loader2 className="mr-2 h-3.5 w-3.5 animate-spin" />}
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
              disabled={pdfBusy === 'comprovante'}
              onClick={baixarComprovante}
            >
              <Receipt className="mr-2 h-4 w-4" />
              {pdfBusy === 'comprovante' ? 'Abrindo…' : 'Baixar comprovante (PDF)'}
            </Button>
          </>
        )}
      </SheetContent>
    </Sheet>
  )
}
