'use client'

import { useEffect, useRef, useState } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { toast } from 'sonner'
import Link from 'next/link'
import { Button } from '@/components/ui/button'
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

/**
 * Pré-requisitos EMA (CHA-MTA-E): videoaula, anexos, autodeclaração de saúde (5-C)
 * e instrutor (Atestado de Demonstração 5-B-1). A GRU/PIX é feita no passo anterior.
 */
export function StepPreRequisitos({
  atendimento,
  onBack,
  onDone,
}: {
  atendimento: Atendimento
  onBack: () => void
  onDone: (resolvida: boolean) => void
}) {
  const ema = !atendimento.temCha
  const { currentTenant } = useTenantStore()

  const [videoaula, setVideoaula] = useState(false)
  const [anexoSaude, setAnexoSaude] = useState(false)
  const [anexoRegras, setAnexoRegras] = useState(false)
  const [anexoResidencia, setAnexoResidencia] = useState(!atendimento.temComprovanteResidencia)
  const [usaLentes, setUsaLentes] = useState(false)
  const [usaAparelho, setUsaAparelho] = useState(false)
  const [instrutorId, setInstrutorId] = useState('')

  const { data: instrutores } = useQuery({
    queryKey: ['instrutores', currentTenant?.id],
    queryFn: () => instrutoresService.list(),
    enabled: !!currentTenant && ema,
  })

  // Pré-preenche da habilitação já salva (retomada / breadcrumb).
  const { data: habSalva } = useQuery({
    queryKey: ['habilitacao', atendimento.reserva?.id],
    queryFn: () => habilitacaoService.get(atendimento.reserva!.id),
    enabled: !!atendimento.reserva?.id && ema,
  })
  const prefilled = useRef(false)
  useEffect(() => {
    if (!habSalva || prefilled.current) return
    prefilled.current = true
    setVideoaula(!!habSalva.videoaulaEm)
    setAnexoSaude(!!habSalva.anexoSaude)
    setAnexoRegras(!!habSalva.anexoRegras)
    setAnexoResidencia(!!habSalva.anexoResidencia)
    setUsaLentes(!!habSalva.usaLentes)
    setUsaAparelho(!!habSalva.usaAparelho)
    if (habSalva.instrutorId) setInstrutorId(habSalva.instrutorId)
  }, [habSalva])

  const registrar = useMutation({
    mutationFn: () =>
      habilitacaoService.registrar(atendimento.reserva!.id, {
        via: 'EMA',
        videoaulaAssistida: videoaula,
        anexoSaude,
        anexoRegras,
        anexoResidencia,
        usaLentes,
        usaAparelho,
        instrutorId: instrutorId || undefined,
      }),
    onSuccess: (h) => {
      toast.success('Pré-requisitos registrados.')
      onDone(!!h.resolvida)
    },
    onError: () => toast.error('Falha ao registrar os pré-requisitos.'),
  })

  if (!ema) {
    return (
      <div className="space-y-5">
        <div className="rounded-md bg-muted/40 px-3 py-2 text-sm text-muted-foreground">
          Habilitação por CHA/CHV — sem pré-requisitos de EMA.
        </div>
        <div className="flex justify-between">
          <Button type="button" variant="outline" onClick={onBack}>
            Voltar
          </Button>
          <Button type="button" onClick={() => onDone(true)}>
            Avançar
          </Button>
        </div>
      </div>
    )
  }

  return (
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
          <Checkbox checked={anexoRegras} onCheckedChange={(v) => setAnexoRegras(!!v)} /> Ciência das regras de navegação
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

      <div className="flex justify-between">
        <Button type="button" variant="outline" onClick={onBack}>
          Voltar
        </Button>
        <Button type="button" disabled={registrar.isPending} onClick={() => registrar.mutate()}>
          {registrar.isPending ? 'Registrando…' : 'Registrar pré-requisitos'}
        </Button>
      </div>
    </div>
  )
}
