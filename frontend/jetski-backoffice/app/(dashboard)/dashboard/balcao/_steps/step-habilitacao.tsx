'use client'

import { useState } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { toast } from 'sonner'
import Link from 'next/link'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Checkbox } from '@/components/ui/checkbox'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { habilitacaoService, instrutoresService } from '@/lib/api/services'
import { useTenantStore } from '@/lib/store/tenant-store'
import type { Atendimento } from '../types'
import type { HabilitacaoGruResponse, HabilitacaoRequest } from '@/lib/api/types'

export function StepHabilitacao({
  atendimento,
  onBack,
  onDone,
}: {
  atendimento: Atendimento
  onBack: () => void
  onDone: (resolvida: boolean) => void
}) {
  const via = atendimento.temCha ? 'CHA' : 'EMA'
  // CHA
  const [chaCategoria, setChaCategoria] = useState('')
  const [chaNumero, setChaNumero] = useState('')
  const [chaValidade, setChaValidade] = useState('')
  // EMA
  const [videoaula, setVideoaula] = useState(false)
  const [anexoSaude, setAnexoSaude] = useState(false)
  const [anexoRegras, setAnexoRegras] = useState(false)
  const [anexoResidencia, setAnexoResidencia] = useState(!atendimento.temComprovanteResidencia)
  const [gruNumero, setGruNumero] = useState('')
  const [gruValor, setGruValor] = useState('')
  const [gruPago, setGruPago] = useState(false)
  const [pix, setPix] = useState<HabilitacaoGruResponse | null>(null)
  // Autodeclaração de saúde (5-C)
  const [usaLentes, setUsaLentes] = useState(false)
  const [usaAparelho, setUsaAparelho] = useState(false)
  // Instrutor (5-B-1)
  const [instrutorId, setInstrutorId] = useState('')

  const { currentTenant } = useTenantStore()
  const { data: instrutores } = useQuery({
    queryKey: ['instrutores', currentTenant?.id],
    queryFn: () => instrutoresService.list(),
    enabled: !!currentTenant && via === 'EMA',
  })

  const registrar = useMutation({
    mutationFn: () => {
      const req: HabilitacaoRequest =
        via === 'CHA'
          ? { via, chaCategoria, chaNumero, chaValidade: chaValidade || undefined }
          : {
              via,
              videoaulaAssistida: videoaula,
              anexoSaude,
              anexoRegras,
              anexoResidencia,
              usaLentes,
              usaAparelho,
              instrutorId: instrutorId || undefined,
              gruNumero: gruNumero || undefined,
              gruValor: gruValor ? Number(gruValor) : undefined,
              gruPago,
            }
      return habilitacaoService.registrar(atendimento.reserva!.id, req)
    },
    onSuccess: (h) => {
      if (h.resolvida) {
        toast.success('Habilitação resolvida.')
        onDone(true)
      } else {
        toast.warning('Habilitação registrada, mas ainda pendente (CHA/GRU).')
        onDone(false)
      }
    },
    onError: () => toast.error('Falha ao registrar habilitação.'),
  })

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
    onSuccess: (r) => {
      if (r.sucesso) {
        toast.success(r.reaproveitada ? 'Boleto reaproveitado.' : 'Boleto (PDF) gerado.')
      } else {
        toast.warning('Não foi possível gerar o boleto automaticamente. Preencha manualmente.')
      }
    },
    onError: () => toast.error('Falha ao gerar o boleto. Preencha manualmente.'),
  })

  return (
    <div className="space-y-5">
      <div className="rounded-md bg-muted/40 px-3 py-2 text-sm">
        Via: <strong>{via === 'CHA' ? 'CHA/CHV (já habilitado)' : 'EMA + GRU (CHA-MTA-E)'}</strong>
      </div>

      {via === 'CHA' ? (
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
      ) : (
        <div className="space-y-4">
          <div className="space-y-2 rounded-lg border p-4">
            <Label className="text-sm font-medium">Pré-requisitos (EMA)</Label>
            <label className="flex items-center gap-2 text-sm">
              <Checkbox checked={videoaula} onCheckedChange={(v) => setVideoaula(!!v)} /> Videoaula assistida
            </label>
            <label className="flex items-center gap-2 text-sm">
              <Checkbox checked={anexoSaude} onCheckedChange={(v) => setAnexoSaude(!!v)} /> Declaração de saúde
            </label>
            <label className="flex items-center gap-2 text-sm">
              <Checkbox checked={anexoRegras} onCheckedChange={(v) => setAnexoRegras(!!v)} /> Ciência das regras
            </label>
            <label className="flex items-center gap-2 text-sm">
              <Checkbox checked={anexoResidencia} onCheckedChange={(v) => setAnexoResidencia(!!v)} /> Comprovante/Declaração de residência
            </label>
          </div>
          <div className="space-y-2 rounded-lg border p-4">
            <Label className="text-sm font-medium">Autodeclaração de saúde (Anexo 5-C)</Label>
            <label className="flex items-center gap-2 text-sm">
              <Checkbox checked={usaLentes} onCheckedChange={(v) => setUsaLentes(!!v)} /> Faço uso de lentes de correção visual
            </label>
            <label className="flex items-center gap-2 text-sm">
              <Checkbox checked={usaAparelho} onCheckedChange={(v) => setUsaAparelho(!!v)} /> Faço uso de aparelho de correção auditiva
            </label>
          </div>
          <div className="space-y-2 rounded-lg border p-4">
            <Label className="text-sm font-medium">Instrutor (Atestado de Demonstração 5-B-1)</Label>
            {(instrutores ?? []).length === 0 ? (
              <p className="text-xs text-muted-foreground">
                Nenhum instrutor cadastrado.{' '}
                <Link href="/dashboard/instrutores" className="text-primary underline" target="_blank">
                  Cadastrar instrutor
                </Link>
              </p>
            ) : (
              <Select value={instrutorId} onValueChange={setInstrutorId}>
                <SelectTrigger>
                  <SelectValue placeholder="Selecione o instrutor" />
                </SelectTrigger>
                <SelectContent>
                  {(instrutores ?? []).map((i) => (
                    <SelectItem key={i.id} value={i.id}>
                      {i.nome}
                      {i.cha ? ` — CHA ${i.cha}` : ''}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            )}
          </div>
          <div className="space-y-3 rounded-lg border p-4">
            <div className="flex items-center justify-between">
              <Label className="text-sm font-medium">GRU (taxa CHA-MTA-E)</Label>
              <div className="flex gap-2">
                <Button
                  type="button"
                  size="sm"
                  variant="secondary"
                  disabled={gerarGru.isPending}
                  onClick={() => gerarGru.mutate()}
                >
                  {gerarGru.isPending ? 'Gerando…' : 'Gerar PIX'}
                </Button>
                <Button
                  type="button"
                  size="sm"
                  variant="outline"
                  disabled={gerarBoleto.isPending}
                  onClick={() => gerarBoleto.mutate()}
                >
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
                  {pix.reaproveitada && (
                    <span className="text-muted-foreground">(GRU já existente)</span>
                  )}
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
                Geração automática indisponível ({pix.erroCodigo}). Gere a GRU no site da Marinha e
                preencha número/valor abaixo.
              </p>
            )}

            <div className="grid gap-3 sm:grid-cols-2">
              <div>
                <Label className="text-xs">Número da GRU</Label>
                <Input value={gruNumero} onChange={(e) => setGruNumero(e.target.value)} />
              </div>
              <div>
                <Label className="text-xs">Valor (R$)</Label>
                <Input
                  type="number"
                  step="0.01"
                  value={gruValor}
                  onChange={(e) => setGruValor(e.target.value)}
                  placeholder="23.13"
                />
              </div>
            </div>
            <label className="flex items-center gap-2 text-sm">
              <Checkbox checked={gruPago} onCheckedChange={(v) => setGruPago(!!v)} /> GRU paga
            </label>
          </div>
        </div>
      )}

      <div className="flex justify-between">
        <Button type="button" variant="outline" onClick={onBack}>
          Voltar
        </Button>
        <Button type="button" disabled={registrar.isPending} onClick={() => registrar.mutate()}>
          {registrar.isPending ? 'Registrando…' : 'Registrar habilitação'}
        </Button>
      </div>
    </div>
  )
}
