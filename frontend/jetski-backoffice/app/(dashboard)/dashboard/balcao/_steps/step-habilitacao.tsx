'use client'

import { useEffect, useRef, useState } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Checkbox } from '@/components/ui/checkbox'
import { FileUpload } from '@/components/file-upload'
import { habilitacaoService, clientesService } from '@/lib/api/services'
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
  const [gruPago, setGruPago] = useState(false)
  const [pix, setPix] = useState<HabilitacaoGruResponse | null>(null)

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
    setGruPago(!!habSalva.gruPago)
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
        toast.success(r.reaproveitada ? 'GRU válida reaproveitada.' : 'GRU gerada com sucesso.')
      } else {
        toast.warning('Não foi possível gerar a GRU automaticamente. Preencha manualmente.')
      }
    },
    onError: () => toast.error('Falha ao gerar a GRU. Preencha manualmente.'),
  })

  const gerarBoleto = useMutation({
    mutationFn: async () => {
      const r = await habilitacaoService.gerarBoleto(atendimento.reserva!.id)
      if (!r.sucesso) return r
      const blob = await habilitacaoService.baixarBoleto(atendimento.reserva!.id)
      window.open(URL.createObjectURL(blob), '_blank')
      return r
    },
    onSuccess: (r) =>
      r.sucesso
        ? toast.success(r.reaproveitada ? 'Boleto reaproveitado.' : 'Boleto (PDF) gerado.')
        : toast.warning('Não foi possível gerar o boleto automaticamente.'),
    onError: () => toast.error('Falha ao gerar o boleto.'),
  })

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
      // EMA: persiste via + GRU (manual ou gerada). Pré-requisitos vão no passo seguinte.
      const h = await habilitacaoService.registrar(reservaId, {
        via: 'EMA',
        gruNumero: gruNumero || undefined,
        gruValor: gruValor ? Number(gruValor) : undefined,
        gruPago,
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
          <div className="flex items-center justify-between">
            <Label className="text-sm font-medium">GRU (taxa CHA-MTA-E)</Label>
            <div className="flex gap-2">
              <Button type="button" size="sm" variant="secondary" disabled={gerarGru.isPending} onClick={() => gerarGru.mutate()}>
                {gerarGru.isPending ? 'Gerando…' : 'Gerar PIX'}
              </Button>
              <Button type="button" size="sm" variant="outline" disabled={gerarBoleto.isPending} onClick={() => gerarBoleto.mutate()}>
                {gerarBoleto.isPending ? 'Gerando…' : 'Gerar boleto (PDF)'}
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
              {pix.pixQrPngBase64 && (
                // eslint-disable-next-line @next/next/no-img-element
                <img
                  src={`data:image/png;base64,${pix.pixQrPngBase64}`}
                  alt="QR Code PIX"
                  className="h-44 w-44 rounded bg-white p-1"
                />
              )}
              <div className="flex items-center gap-2">
                <Input readOnly value={pix.pixCopiaECola} className="font-mono text-xs" />
                <Button
                  type="button"
                  size="sm"
                  variant="outline"
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
              <Input value={gruNumero} onChange={(e) => setGruNumero(e.target.value)} />
            </div>
            <div>
              <Label className="text-xs">Valor (R$)</Label>
              <Input type="number" step="0.01" value={gruValor} onChange={(e) => setGruValor(e.target.value)} placeholder="23.13" />
            </div>
          </div>
          <label className="flex items-center gap-2 text-sm">
            <Checkbox checked={gruPago} onCheckedChange={(v) => setGruPago(!!v)} /> GRU paga
          </label>
        </div>
      )}

      <div className="flex justify-between">
        <Button type="button" variant="outline" onClick={onBack}>
          Voltar
        </Button>
        <Button type="button" disabled={avancar.isPending} onClick={() => avancar.mutate()}>
          {avancar.isPending ? 'Salvando…' : 'Avançar'}
        </Button>
      </div>
    </div>
  )
}
