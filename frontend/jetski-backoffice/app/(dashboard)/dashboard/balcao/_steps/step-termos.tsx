'use client'

import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import { SignaturePad } from '@/components/signature-pad'
import { aceiteService } from '@/lib/api/services'
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

  return (
    <div className="space-y-5">
      <div className="max-h-56 overflow-y-auto whitespace-pre-line rounded-lg border bg-muted/20 p-4 text-sm text-muted-foreground">
        {TERMO}
      </div>

      <div>
        <Label className="mb-2 block text-sm font-medium">
          Assinatura do locatário ({atendimento.cliente?.nome})
        </Label>
        <SignaturePad onChange={setAssinatura} />
      </div>

      <div className="flex justify-between">
        <Button type="button" variant="outline" onClick={onBack}>
          Voltar
        </Button>
        <Button
          type="button"
          disabled={!assinatura || registrar.isPending}
          onClick={() => registrar.mutate()}
        >
          {registrar.isPending ? 'Registrando…' : 'Assinar e avançar'}
        </Button>
      </div>
    </div>
  )
}
