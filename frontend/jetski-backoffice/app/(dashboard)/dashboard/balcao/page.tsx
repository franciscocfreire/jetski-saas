'use client'

import { useState } from 'react'
import { Store, RotateCcw } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader } from '@/components/ui/card'
import { Stepper } from '@/components/stepper'
import { BALCAO_STEPS, type Atendimento } from './types'
import { StepCliente } from './_steps/step-cliente'
import { StepDocumentos } from './_steps/step-documentos'
import { StepAluguel } from './_steps/step-aluguel'

const VAZIO: Atendimento = {
  temComprovanteResidencia: true,
  temCha: true,
  habilitacaoResolvida: false,
  aceiteFeito: false,
}

export default function BalcaoPage() {
  const [step, setStep] = useState(0)
  const [at, setAt] = useState<Atendimento>(VAZIO)

  function reset() {
    setAt(VAZIO)
    setStep(0)
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <Store className="h-7 w-7 text-primary" />
          <div>
            <h1 className="text-2xl font-bold">Balcão — Atendimento assistido</h1>
            <p className="text-sm text-muted-foreground">
              Cadastro, documentos, pagamento, habilitação, termos e emissão.
            </p>
          </div>
        </div>
        <Button type="button" variant="outline" size="sm" onClick={reset}>
          <RotateCcw size={14} className="mr-1" /> Novo atendimento
        </Button>
      </div>

      <Card>
        <CardHeader>
          <Stepper
            steps={[...BALCAO_STEPS]}
            current={step}
            onStepClick={(i) => i < step && setStep(i)}
          />
        </CardHeader>
        <CardContent>
          {at.cliente && (
            <div className="mb-4 rounded-md bg-muted/40 px-3 py-2 text-xs text-muted-foreground">
              Atendimento: <span className="font-medium text-foreground">{at.cliente.nome}</span>
              {at.reserva && <> · reserva <span className="font-mono">{at.reserva.id.slice(0, 8)}</span></>}
            </div>
          )}

          {step === 0 && (
            <StepCliente
              onDone={(cliente) => {
                setAt((a) => ({ ...a, cliente }))
                setStep(1)
              }}
            />
          )}

          {step === 1 && (
            <StepDocumentos
              atendimento={at}
              onBack={() => setStep(0)}
              onDone={(patch) => {
                setAt((a) => ({ ...a, ...patch }))
                setStep(2)
              }}
            />
          )}

          {step === 2 && (
            <StepAluguel
              atendimento={at}
              onBack={() => setStep(1)}
              onDone={(reserva, modelo) => {
                setAt((a) => ({ ...a, reserva, modelo }))
                setStep(3)
              }}
            />
          )}

          {step >= 3 && (
            <div className="space-y-4 py-8 text-center">
              <p className="text-muted-foreground">
                Passos <strong>Habilitação → Termos → Emissão</strong> chegam na próxima etapa (F3.5).
              </p>
              <div className="flex justify-center gap-2">
                <Button type="button" variant="outline" onClick={() => setStep(2)}>
                  Voltar
                </Button>
                <Button type="button" onClick={reset}>
                  Concluir atendimento
                </Button>
              </div>
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
