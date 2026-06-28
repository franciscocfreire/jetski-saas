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
  RefreshCw,
  Save,
  Upload,
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
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Separator } from '@/components/ui/separator'
import { habilitacaoService, aceiteService, reservasService } from '@/lib/api/services'
import { formatCurrency, formatDate, formatDateTime } from '@/lib/utils'
import { PixQrCode } from '@/components/pix-qrcode'
import { waHref } from '@/components/whatsapp-link'
import { DocumentoPreviewButtons } from '@/components/documento-preview-buttons'
import type { Reserva } from '@/lib/api/types'

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
        dataFimPrevista: fim.toISOString(),
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

  const enviarEmailGru = useMutation({
    mutationFn: () => habilitacaoService.enviarEmailGru(reservaId!),
    onSuccess: (enviado) =>
      enviado
        ? toast.success('E-mail da GRU enviado ao cliente.')
        : toast.warning('Cliente sem e-mail ou GRU ainda não gerada.'),
    onError: () => toast.error('Falha ao enviar o e-mail da GRU.'),
  })

  if (!reserva) return null

  const online = reserva.cliente?.origem === 'PORTAL'
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

        {editavel && (
          <Button
            variant="outline"
            className="mt-2 w-full border-red-300 text-red-600 hover:bg-red-50 hover:text-red-700 dark:border-red-900 dark:hover:bg-red-950/40"
            disabled={cancelar.isPending}
            onClick={() => {
              if (window.confirm(`Cancelar a reserva de ${reserva.cliente?.nome ?? 'cliente'}? Esta ação não pode ser desfeita.`))
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
          {/* O passeio é pago no fim da locação; aqui o pagamento relevante é a GRU.
              Sinaliza quem já tem o comprovante anexado. */}
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
              <Etapa ok={!!hab.videoaulaEm} label="Videoaula assistida" />
              <Etapa ok={!!hab.anexoSaude} label="Autodeclaração de saúde (5-C)" />
              <Etapa ok={!!hab.anexoRegras} label="Anexo de regras de navegação" />
              <Etapa ok={!!hab.anexoResidencia} label="Comprovante/Declaração de residência" />
              <Etapa ok={!!hab.instrutorId} label="Instrutor (atestado de demonstração)" />
            </div>

            <Separator className="my-4" />
            <div className="space-y-2">
              <h4 className="text-sm font-medium">Dados pessoais (NORMAM-212)</h4>
              <p className="text-xs text-muted-foreground">
                Necessários para emitir os documentos (não bloqueiam a reserva).
              </p>
              <Etapa ok={!!reserva.cliente?.rg} label="RG" />
              <Etapa ok={!!reserva.cliente?.orgaoEmissor} label="Órgão emissor" />
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
                  className="w-full"
                  onClick={() => setCompModo(true)}
                >
                  <Upload className="mr-2 h-4 w-4" />
                  Anexar comprovante (pago por outro meio)
                </Button>
              ) : (
                <div className="space-y-2 rounded-md border border-dashed p-3">
                  <p className="text-xs font-medium">Comprovante de pagamento (obrigatório)</p>
                  <FileUpload
                    label="Enviar/tirar foto do comprovante"
                    accept="image/*,application/pdf"
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
                  <div className="flex items-center gap-2">
                    <code className="flex-1 truncate rounded bg-background px-2 py-1 text-xs">
                      {hab?.gruPixCopiaECola}
                    </code>
                    <Button
                      type="button"
                      size="sm"
                      variant="outline"
                      title="Copiar PIX"
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
                          className="text-emerald-600 hover:text-emerald-700 dark:text-emerald-400"
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
