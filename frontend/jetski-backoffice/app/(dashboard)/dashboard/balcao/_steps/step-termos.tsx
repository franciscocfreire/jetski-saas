'use client'

import { useState } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { toast } from 'sonner'
import { CheckCircle2 } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import { SignaturePad } from '@/components/signature-pad'
import { aceiteService } from '@/lib/api/services'
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
  const [assinatura, setAssinatura] = useState<string | null>(null)
  const [reassinar, setReassinar] = useState(false)

  // Reflete aceite já registrado (retomada / voltar pelo breadcrumb) p/ não perder a assinatura.
  const { data: aceiteExistente } = useQuery({
    queryKey: ['aceite', atendimento.reserva?.id],
    queryFn: () => aceiteService.get(atendimento.reserva!.id),
    enabled: !!atendimento.reserva?.id,
  })

  const registrar = useMutation({
    mutationFn: () =>
      aceiteService.registrar(atendimento.reserva!.id, {
        metodo: 'SIGNATURE_PAD',
        assinaturaBase64: assinatura!,
      }),
    onSuccess: () => {
      toast.success('Termos assinados.')
      onDone()
    },
    onError: () => toast.error('Falha ao registrar o aceite.'),
  })

  const jaAssinado = !!aceiteExistente && !reassinar

  return (
    <div className="space-y-5">
      <div className="max-h-56 overflow-y-auto whitespace-pre-line rounded-lg border bg-muted/20 p-4 text-sm text-muted-foreground">
        {TERMO}
      </div>

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
          <Button type="button" onClick={onDone}>
            Avançar
          </Button>
        ) : (
          <Button
            type="button"
            disabled={!assinatura || registrar.isPending}
            onClick={() => registrar.mutate()}
          >
            {registrar.isPending ? 'Registrando…' : 'Assinar e avançar'}
          </Button>
        )}
      </div>
    </div>
  )
}
