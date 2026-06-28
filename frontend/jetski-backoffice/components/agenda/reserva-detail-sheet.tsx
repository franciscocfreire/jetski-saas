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
import type { Reserva } from '@/lib/api/types'

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
