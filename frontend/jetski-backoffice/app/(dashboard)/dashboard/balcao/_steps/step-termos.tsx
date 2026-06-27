'use client'

import { useEffect, useRef, useState } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { toast } from 'sonner'
import { CheckCircle2 } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import { Checkbox } from '@/components/ui/checkbox'
import { SignaturePad } from '@/components/signature-pad'
import { aceiteService, habilitacaoService } from '@/lib/api/services'
import { formatDateTime } from '@/lib/utils'
import type { Atendimento } from '../types'

const TERMO = `TERMO DE RESPONSABILIDADE E CIÊNCIA DE RISCOS

O LOCATÁRIO declara estar apto à condução da embarcação/moto aquática, ciente
das normas de segurança (NORMAM-212/DPC), do uso obrigatório de colete salva-vidas,
dos limites da área de navegação e das condições de devolução do equipamento.
Responsabiliza-se por danos causados por uso indevido e autoriza a cobrança de
caução conforme contratado.`

export function StepTermos({
  atendimento,
  onBack,
  onDone,
}: {
  atendimento: Atendimento
  onBack: () => void
  onDone: () => void
}) {
  const ema = !atendimento.temCha
  const [assinatura, setAssinatura] = useState<string | null>(null)
  const [reassinar, setReassinar] = useState(false)
  // Declarações do locatário (conferidas na hora de assinar): ciência das regras,
  // videoaula e autodeclaração de saúde (Anexo 5-C).
  const [cienteRegras, setCienteRegras] = useState(false)
  const [videoaula, setVideoaula] = useState(false)
  const [usaLentes, setUsaLentes] = useState(false)
  const [usaAparelho, setUsaAparelho] = useState(false)

  // Reflete aceite já registrado (retomada / voltar pelo breadcrumb) p/ não perder a assinatura.
  const { data: aceiteExistente } = useQuery({
    queryKey: ['aceite', atendimento.reserva?.id],
    queryFn: () => aceiteService.get(atendimento.reserva!.id),
    enabled: !!atendimento.reserva?.id,
  })

  // Pré-preenche a autodeclaração de saúde da habilitação salva (retomada).
  const { data: habSalva } = useQuery({
    queryKey: ['habilitacao', atendimento.reserva?.id],
    queryFn: () => habilitacaoService.get(atendimento.reserva!.id),
    enabled: !!atendimento.reserva?.id && ema,
  })
  const prefilled = useRef(false)
  useEffect(() => {
    if (!habSalva || prefilled.current) return
    prefilled.current = true
    setCienteRegras(!!habSalva.anexoRegras)
    setVideoaula(!!habSalva.videoaulaEm)
    setUsaLentes(!!habSalva.usaLentes)
    setUsaAparelho(!!habSalva.usaAparelho)
  }, [habSalva])

  const jaAssinado = !!aceiteExistente && !reassinar

  const concluir = useMutation({
    mutationFn: async () => {
      // EMA: grava as declarações conferidas pelo cliente (saúde 5-C, regras, videoaula).
      if (ema) {
        await habilitacaoService
          .registrar(atendimento.reserva!.id, {
            via: 'EMA',
            anexoSaude: true,
            anexoRegras: cienteRegras,
            videoaulaAssistida: videoaula,
            usaLentes,
            usaAparelho,
          })
          .catch(() => null)
      }
      // Só registra o aceite se ainda não houver assinatura (ou se reassinando).
      if (!jaAssinado) {
        await aceiteService.registrar(atendimento.reserva!.id, {
          metodo: 'SIGNATURE_PAD',
          assinaturaBase64: assinatura!,
        })
      }
    },
    onSuccess: () => {
      toast.success(jaAssinado ? 'Declarações atualizadas.' : 'Termos assinados.')
      onDone()
    },
    onError: () => toast.error('Falha ao registrar os termos.'),
  })

  return (
    <div className="space-y-5">
      <div className="max-h-56 overflow-y-auto whitespace-pre-line rounded-lg border bg-muted/20 p-4 text-sm text-muted-foreground">
        {TERMO}
      </div>

      {ema && (
        <div className="space-y-3 rounded-lg border p-4">
          <Label className="text-sm font-medium">Declarações do locatário</Label>
          <p className="text-xs text-muted-foreground">
            Confirme com o cliente antes de assinar.
          </p>
          <label className="flex items-center gap-2 text-sm">
            <Checkbox checked={cienteRegras} onCheckedChange={(v) => setCienteRegras(!!v)} /> Declaro
            ciência das regras de navegação (NORMAM-212)
          </label>
          <label className="flex items-center gap-2 text-sm">
            <Checkbox checked={videoaula} onCheckedChange={(v) => setVideoaula(!!v)} /> Assisti à
            videoaula de orientação
          </label>
          <div className="space-y-2 pt-1">
            <p className="text-xs font-medium text-muted-foreground">
              Autodeclaração de saúde (Anexo 5-C)
            </p>
            <label className="flex items-center gap-2 text-sm">
              <Checkbox checked={usaLentes} onCheckedChange={(v) => setUsaLentes(!!v)} /> Faço uso de
              lentes de correção visual
            </label>
            <label className="flex items-center gap-2 text-sm">
              <Checkbox checked={usaAparelho} onCheckedChange={(v) => setUsaAparelho(!!v)} /> Faço uso
              de aparelho de correção auditiva
            </label>
          </div>
        </div>
      )}

      {jaAssinado ? (
        <div className="flex items-center justify-between rounded-lg border border-emerald-300 bg-emerald-50 p-4 dark:bg-emerald-950/30">
          <p className="flex items-center gap-2 text-sm font-medium text-emerald-700">
            <CheckCircle2 className="h-5 w-5" />
            Termos já assinados
            {aceiteExistente?.aceitoEm && (
              <span className="font-normal text-muted-foreground">
                em {formatDateTime(aceiteExistente.aceitoEm)}
              </span>
            )}
          </p>
          <Button type="button" variant="ghost" size="sm" onClick={() => setReassinar(true)}>
            Assinar novamente
          </Button>
        </div>
      ) : (
        <div>
          <Label className="mb-2 block text-sm font-medium">
            Assinatura do locatário ({atendimento.cliente?.nome})
          </Label>
          <SignaturePad onChange={setAssinatura} />
        </div>
      )}

      <div className="flex justify-between">
        <Button type="button" variant="outline" onClick={onBack}>
          Voltar
        </Button>
        {jaAssinado ? (
          <Button type="button" disabled={concluir.isPending} onClick={() => concluir.mutate()}>
            {concluir.isPending ? 'Salvando…' : 'Avançar'}
          </Button>
        ) : (
          <Button
            type="button"
            disabled={!assinatura || concluir.isPending}
            onClick={() => concluir.mutate()}
          >
            {concluir.isPending ? 'Registrando…' : 'Assinar e avançar'}
          </Button>
        )}
      </div>
    </div>
  )
}
