'use client'

import { useEffect, useRef, useState } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { toast } from 'sonner'
import { AlertTriangle, CheckCircle2 } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { FileUpload } from '@/components/file-upload'
import { PixQrCode } from '@/components/pix-qrcode'
import { habilitacaoService, clientesService } from '@/lib/api/services'
import { abrirPdfBlob } from '@/lib/pdf'
import type { Atendimento } from '../types'
import type { HabilitacaoGruResponse } from '@/lib/api/types'

/**
 * Passo Habilitação: decisão "tem CHA?" (default: NÃO → emite temporária).
 * - CHA: dados da CHA + foto do comprovante.
 * - EMA: gera a GRU (PIX/boleto) agora — o cliente paga em paralelo. Os demais
 *   pré-requisitos (5-C, instrutor, anexos) ficam no passo seguinte.
 */
export function StepHabilitacao({
  atendimento,
  onBack,
  onDone,
}: {
  atendimento: Atendimento
  onBack: () => void
  onDone: (resolvida: boolean, temCha: boolean) => void
}) {
  const [temCha, setTemCha] = useState(atendimento.temCha)
  const via = temCha ? 'CHA' : 'EMA'
  const clienteId = atendimento.cliente!.id

  // CHA
  const [chaCategoria, setChaCategoria] = useState('')
  const [chaNumero, setChaNumero] = useState('')
  const [chaValidade, setChaValidade] = useState('')
  const [chaFoto, setChaFoto] = useState<string | undefined>(undefined)
  // EMA / GRU
  const [gruNumero, setGruNumero] = useState('')
  const [gruValor, setGruValor] = useState('')
  const [pago, setPago] = useState(false)
  const [pix, setPix] = useState<HabilitacaoGruResponse | null>(null)
  const [temSessaoPix, setTemSessaoPix] = useState(false)
  // Comprovante manual (pago por outro meio / verificação falhou)
  const [modoComprovante, setModoComprovante] = useState(false)
  const [comprovante, setComprovante] = useState<string | undefined>(undefined)

  // Foto da CHA já enviada (carrega automático, pode trocar).
  const { data: chaFotoUrl } = useQuery({
    queryKey: ['cliente-anexo-cha', clienteId],
    queryFn: async () => {
      const lista = await clientesService.listarAnexos(clienteId)
      if (!lista.some((a) => a.tipo === 'CHA')) return undefined
      const blob = await clientesService.baixarAnexo(clienteId, 'CHA').catch(() => null)
      return blob ? URL.createObjectURL(blob) : undefined
    },
    enabled: !!clienteId,
  })

  // Pré-preenche da habilitação salva (retomada / breadcrumb).
  const { data: habSalva } = useQuery({
    queryKey: ['habilitacao', atendimento.reserva?.id],
    queryFn: () => habilitacaoService.get(atendimento.reserva!.id),
    enabled: !!atendimento.reserva?.id,
  })
  const prefilled = useRef(false)
  useEffect(() => {
    if (!habSalva || prefilled.current) return
    prefilled.current = true
    if (habSalva.chaCategoria) setChaCategoria(habSalva.chaCategoria)
    if (habSalva.chaNumero) setChaNumero(habSalva.chaNumero)
    if (habSalva.chaValidade) setChaValidade(habSalva.chaValidade)
    if (habSalva.gruNumero) setGruNumero(habSalva.gruNumero)
    if (habSalva.gruValor != null) setGruValor(String(habSalva.gruValor))
    setPago(!!habSalva.gruPago)
    if (habSalva.gruPixCopiaECola) setTemSessaoPix(true)
    if (habSalva.gruPixCopiaECola) {
      setPix({
        sucesso: true,
        reaproveitada: true,
        gruNumero: habSalva.gruNumero,
        gruValor: habSalva.gruValor,
        pixCopiaECola: habSalva.gruPixCopiaECola,
        pixExpiracao: habSalva.gruPixExpiracao,
      })
    }
  }, [habSalva])

  const gerarGru = useMutation({
    mutationFn: () => habilitacaoService.gerarGru(atendimento.reserva!.id),
    onSuccess: (r) => {
      setPix(r)
      if (r.sucesso) {
        if (r.gruNumero) setGruNumero(r.gruNumero)
        if (r.gruValor != null) setGruValor(String(r.gruValor))
        setTemSessaoPix(true)
        toast.success(r.reaproveitada ? 'GRU válida reaproveitada.' : 'GRU gerada com sucesso.')
      } else {
        toast.warning('Não foi possível gerar a GRU automaticamente. Preencha manualmente.')
      }
    },
    onError: () => toast.error('Falha ao gerar a GRU. Preencha manualmente.'),
  })

  const verificar = useMutation({
    mutationFn: () => habilitacaoService.verificarPagamento(atendimento.reserva!.id),
    onSuccess: (r) => {
      if (r.pago) {
        setPago(true)
        toast.success('Pagamento confirmado — GRU paga.')
      } else if (r.situacao === 'EXPIRADO') {
        toast.warning('A sessão do PIX expirou. Gere um novo PIX/boleto ou anexe o comprovante.')
      } else {
        toast.info('Pagamento ainda não identificado. Tente novamente ou anexe o comprovante.')
      }
    },
    onError: () => toast.error('Falha ao verificar o pagamento.'),
  })

  const enviarComprovante = useMutation({
    mutationFn: () => habilitacaoService.registrarComprovante(atendimento.reserva!.id, comprovante!),
    onSuccess: () => {
      setPago(true)
      setModoComprovante(false)
      toast.success('Comprovante registrado — GRU marcada como paga.')
    },
    onError: () => toast.error('Falha ao registrar o comprovante.'),
  })

  // Abre a aba no clique (abrirPdfBlob) p/ o boleto funcionar no iOS Safari.
  const [boletoBusy, setBoletoBusy] = useState(false)
  const gerarBoleto = () => {
    setBoletoBusy(true)
    abrirPdfBlob(async () => {
      const r = await habilitacaoService.gerarBoleto(atendimento.reserva!.id)
      if (!r.sucesso) {
        toast.warning('Não foi possível gerar o boleto automaticamente.')
        throw new Error('boleto não gerado')
      }
      if (r.gruNumero) setGruNumero(r.gruNumero)
      if (r.gruValor != null) setGruValor(String(r.gruValor))
      toast.success(r.reaproveitada ? 'Boleto reaproveitado.' : 'Boleto (PDF) gerado.')
      return habilitacaoService.baixarBoleto(atendimento.reserva!.id)
    }, 'boleto.pdf')
      .catch(() => {})
      .finally(() => setBoletoBusy(false))
  }

  const avancar = useMutation({
    mutationFn: async () => {
      const reservaId = atendimento.reserva!.id
      if (temCha) {
        const h = await habilitacaoService.registrar(reservaId, {
          via: 'CHA',
          chaCategoria: chaCategoria || undefined,
          chaNumero: chaNumero || undefined,
          chaValidade: chaValidade || undefined,
        })
        if (chaFoto) await clientesService.uploadAnexo(clienteId, 'CHA', chaFoto).catch(() => null)
        return !!h.resolvida
      }
      // EMA: persiste via + número da GRU (referência) + instrutor (coletado no
      // Passeio & Preço). O pagamento NÃO é marcado aqui — só via "Verificar
      // pagamento" (PIX) ou anexo do comprovante. Sem isso, a reserva segue como
      // pendência e a Marinha não é notificada.
      const h = await habilitacaoService.registrar(reservaId, {
        via: 'EMA',
        gruNumero: gruNumero || undefined,
        instrutorId: atendimento.instrutorId || undefined,
      })
      return !!h.resolvida
    },
    onSuccess: (resolvida) => onDone(resolvida, temCha),
    onError: () => toast.error('Falha ao registrar habilitação.'),
  })

  return (
    <div className="space-y-5">
      <div className="space-y-2 rounded-lg border p-4">
        <Label className="text-sm font-medium">O cliente já tem habilitação (CHA/CHV)?</Label>
        <div className="flex flex-wrap gap-2">
          <Button type="button" variant={temCha ? 'default' : 'outline'} size="sm" onClick={() => setTemCha(true)}>
            Sim, já tem CHA/CHV
          </Button>
          <Button type="button" variant={!temCha ? 'default' : 'outline'} size="sm" onClick={() => setTemCha(false)}>
            Não → emitir temporária (EMA + GRU)
          </Button>
        </div>
        {!temCha && (
          <p className="text-xs text-muted-foreground">
            Gere o PIX/boleto da GRU agora — o cliente paga enquanto você segue com pré-requisitos, documentos e termos.
          </p>
        )}
      </div>

      {via === 'CHA' ? (
        <div className="space-y-4">
          <div className="grid gap-3 sm:grid-cols-3">
            <div>
              <Label className="text-xs">Categoria</Label>
              <Input value={chaCategoria} onChange={(e) => setChaCategoria(e.target.value)} placeholder="Motonauta" />
            </div>
            <div>
              <Label className="text-xs">Número</Label>
              <Input value={chaNumero} onChange={(e) => setChaNumero(e.target.value)} />
            </div>
            <div>
              <Label className="text-xs">Validade</Label>
              <Input type="date" value={chaValidade} onChange={(e) => setChaValidade(e.target.value)} />
            </div>
          </div>
          <div>
            <Label className="mb-1 block text-xs">Foto da CHA/CHV (comprovante)</Label>
            <FileUpload
              label="Enviar/tirar foto da CHA"
              accept="image/*"
              initialUrl={chaFotoUrl ?? undefined}
              onChange={(f) => setChaFoto(f?.dataUrl)}
            />
          </div>
        </div>
      ) : (
        <div className="space-y-3 rounded-lg border p-4">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <Label className="text-sm font-medium">GRU (taxa CHA-MTA-E)</Label>
            <div className="flex flex-wrap gap-2">
              <Button type="button" size="sm" variant="secondary" disabled={gerarGru.isPending} onClick={() => gerarGru.mutate()}>
                {gerarGru.isPending ? 'Gerando…' : 'Gerar PIX'}
              </Button>
              <Button type="button" size="sm" variant="outline" disabled={boletoBusy} onClick={gerarBoleto}>
                {boletoBusy ? 'Gerando…' : 'Gerar boleto (PDF)'}
              </Button>
            </div>
          </div>

          {pix?.sucesso && pix.pixCopiaECola && (
            <div className="space-y-3 rounded-md border border-emerald-200 bg-emerald-50 p-3 text-sm dark:border-emerald-900 dark:bg-emerald-950/40">
              <div className="flex flex-wrap items-center gap-x-4 gap-y-1">
                {pix.gruValor != null && (
                  <span>
                    Valor: <strong>R$ {pix.gruValor.toFixed(2)}</strong>
                  </span>
                )}
                {pix.pixExpiracao && (
                  <span className="text-muted-foreground">
                    vence {new Date(pix.pixExpiracao).toLocaleString('pt-BR')}
                  </span>
                )}
                {pix.reaproveitada && <span className="text-muted-foreground">(GRU já existente)</span>}
              </div>
              {/* PNG oficial na geração; ao retomar (sem PNG), renderiza do copia-e-cola */}
              {pix.pixQrPngBase64 ? (
                // eslint-disable-next-line @next/next/no-img-element
                <img
                  src={`data:image/png;base64,${pix.pixQrPngBase64}`}
                  alt="QR Code PIX"
                  className="h-44 w-44 rounded bg-white p-1"
                />
              ) : (
                <PixQrCode payload={pix.pixCopiaECola} size={176} />
              )}
              <div className="flex flex-col gap-2 sm:flex-row sm:items-center">
                <Input readOnly value={pix.pixCopiaECola} className="min-w-0 font-mono text-xs" />
                <Button
                  type="button"
                  size="sm"
                  variant="outline"
                  className="h-10 w-full sm:h-8 sm:w-auto"
                  onClick={() => {
                    navigator.clipboard.writeText(pix.pixCopiaECola!)
                    toast.success('PIX copia-e-cola copiado.')
                  }}
                >
                  Copiar
                </Button>
              </div>
            </div>
          )}

          {pix && !pix.sucesso && (
            <p className="rounded-md border border-amber-200 bg-amber-50 p-2 text-xs text-amber-800 dark:border-amber-900 dark:bg-amber-950/40 dark:text-amber-200">
              Geração automática indisponível ({pix.erroCodigo}). Gere a GRU no site da Marinha e preencha número/valor abaixo.
            </p>
          )}

          <div className="grid gap-3 sm:grid-cols-2">
            <div>
              <Label className="text-xs">Número da GRU</Label>
              <Input
                value={gruNumero}
                onChange={(e) => setGruNumero(e.target.value)}
                placeholder="(preenchido ao gerar)"
              />
            </div>
            <div>
              <Label className="text-xs">Valor (R$)</Label>
              <div className="flex h-10 items-center rounded-md border bg-muted/40 px-3 text-sm">
                {gruValor ? (
                  `R$ ${Number(gruValor).toFixed(2)}`
                ) : (
                  <span className="text-muted-foreground">consta no boleto/GRU</span>
                )}
              </div>
              <p className="mt-1 text-[11px] text-muted-foreground">
                Valor oficial da taxa — não editável.
              </p>
            </div>
          </div>
        </div>
      )}

      {/* Confirmação do pagamento da GRU (EMA) */}
      {via === 'EMA' && (
        <div className="space-y-3 rounded-lg border p-4">
          <Label className="text-sm font-medium">Confirmação do pagamento da GRU</Label>
          {pago ? (
            <div className="flex items-center gap-2 rounded-md border border-emerald-200 bg-emerald-50 p-3 text-sm text-emerald-800 dark:border-emerald-900 dark:bg-emerald-950/40 dark:text-emerald-200">
              <CheckCircle2 className="h-4 w-4 shrink-0" /> GRU paga — comprovante anexado à documentação.
            </div>
          ) : (
            <>
              <p className="text-xs text-muted-foreground">
                A Marinha só é notificada com a GRU paga. Confirme pelo PIX ou anexe o comprovante.
              </p>
              <div className="flex flex-wrap gap-2">
                {temSessaoPix && (
                  <Button
                    type="button"
                    variant="secondary"
                    size="sm"
                    disabled={verificar.isPending}
                    onClick={() => verificar.mutate()}
                  >
                    {verificar.isPending ? 'Verificando…' : 'Verificar pagamento do PIX'}
                  </Button>
                )}
                {!modoComprovante && (
                  <Button type="button" variant="outline" size="sm" onClick={() => setModoComprovante(true)}>
                    Paguei por outro meio / a verificação não funcionou
                  </Button>
                )}
              </div>

              {modoComprovante && (
                <div className="space-y-2 rounded-md border border-dashed p-3">
                  <Label className="text-xs">Comprovante de pagamento (obrigatório)</Label>
                  <FileUpload
                    label="Enviar/tirar foto do comprovante"
                    accept="image/*,application/pdf"
                    onChange={(f) => setComprovante(f?.dataUrl)}
                  />
                  <div className="flex gap-2">
                    <Button
                      type="button"
                      size="sm"
                      disabled={!comprovante || enviarComprovante.isPending}
                      onClick={() => enviarComprovante.mutate()}
                    >
                      {enviarComprovante.isPending ? 'Enviando…' : 'Confirmar pagamento com comprovante'}
                    </Button>
                    <Button
                      type="button"
                      size="sm"
                      variant="ghost"
                      onClick={() => {
                        setModoComprovante(false)
                        setComprovante(undefined)
                      }}
                    >
                      Cancelar
                    </Button>
                  </div>
                </div>
              )}

              <p className="text-[11px] text-amber-700 dark:text-amber-400">
                Se prosseguir sem confirmar, a reserva fica marcada como <b>GRU não paga</b> (pendência)
                e o e-mail à Marinha não é enviado até o comprovante ser anexado.
              </p>
            </>
          )}
        </div>
      )}

      <div className="flex flex-wrap items-center justify-between gap-2">
        <Button type="button" variant="outline" onClick={onBack}>
          Voltar
        </Button>
        <div className="flex flex-wrap items-center gap-2">
          {via === 'EMA' && !pago && (
            <Button
              type="button"
              variant="outline"
              disabled={avancar.isPending}
              onClick={() => avancar.mutate()}
              className="border-amber-400 text-amber-700 hover:bg-amber-50 dark:text-amber-300 dark:hover:bg-amber-950/40"
            >
              <AlertTriangle className="mr-1 h-4 w-4" />
              Prosseguir sem confirmar a GRU
            </Button>
          )}
          <Button
            type="button"
            disabled={avancar.isPending || (via === 'EMA' && !pago)}
            onClick={() => avancar.mutate()}
          >
            {avancar.isPending ? 'Salvando…' : 'Avançar'}
          </Button>
        </div>
      </div>
    </div>
  )
}
