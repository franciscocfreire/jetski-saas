'use client'

import { Suspense, useEffect, useState } from 'react'
import { useRouter, useSearchParams } from 'next/navigation'
import { toast } from 'sonner'
import { Store, RotateCcw, Loader2 } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader } from '@/components/ui/card'
import { Stepper } from '@/components/stepper'
import {
  reservasService,
  clientesService,
  modelosService,
  habilitacaoService,
  aceiteService,
} from '@/lib/api/services'
import { BALCAO_STEPS, type Atendimento } from './types'
import { StepCliente } from './_steps/step-cliente'
import { StepDocumentos } from './_steps/step-documentos'
import { StepAluguel } from './_steps/step-aluguel'
import { StepHabilitacao } from './_steps/step-habilitacao'
import { StepTermos } from './_steps/step-termos'
import { StepEmissao } from './_steps/step-emissao'

const VAZIO: Atendimento = {
  temComprovanteResidencia: true,
  temCha: true,
  habilitacaoResolvida: false,
  aceiteFeito: false,
}

export default function BalcaoPage() {
  return (
    <Suspense fallback={<div className="p-6 text-sm text-muted-foreground">Carregando…</div>}>
      <BalcaoWizard />
    </Suspense>
  )
}

function BalcaoWizard() {
  const router = useRouter()
  const search = useSearchParams()
  const reservaId = search.get('reserva')

  const [step, setStep] = useState(0)
  const [at, setAt] = useState<Atendimento>(VAZIO)
  const [resuming, setResuming] = useState(!!reservaId)

  // Retomada: carrega uma reserva existente e pula para o passo pendente.
  useEffect(() => {
    if (!reservaId) return
    let cancel = false
    ;(async () => {
      try {
        const reserva = await reservasService.getById(reservaId)
        const [cliente, modelo, hab, aceite] = await Promise.all([
          clientesService.getById(reserva.clienteId),
          modelosService.getById(reserva.modeloId),
          habilitacaoService.get(reservaId),
          aceiteService.get(reservaId),
        ])
        if (cancel) return
        const habilitacaoResolvida = !!hab?.resolvida
        const aceiteFeito = !!aceite
        setAt({
          cliente,
          reserva,
          modelo,
          temComprovanteResidencia: true,
          temCha: hab?.via === 'CHA',
          habilitacaoResolvida,
          aceiteFeito,
        })
        // 1º passo pendente: habilitação (3) → termos (4) → emissão (5)
        setStep(!habilitacaoResolvida ? 3 : !aceiteFeito ? 4 : 5)
      } catch {
        toast.error('Não foi possível retomar o atendimento.')
      } finally {
        if (!cancel) setResuming(false)
      }
    })()
    return () => {
      cancel = true
    }
  }, [reservaId])

  function reset() {
    setAt(VAZIO)
    setStep(0)
    if (reservaId) router.replace('/dashboard/balcao')
  }

  if (resuming) {
    return (
      <div className="flex h-[50vh] items-center justify-center gap-2 text-muted-foreground">
        <Loader2 className="h-5 w-5 animate-spin" /> Retomando atendimento…
      </div>
    )
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
            <StepAluguel
              atendimento={at}
              onBack={() => setStep(0)}
              onDone={(reserva, modelo) => {
                setAt((a) => ({ ...a, reserva, modelo }))
                setStep(2)
              }}
            />
          )}

          {step === 2 && (
            <StepDocumentos
              atendimento={at}
              onBack={() => setStep(1)}
              onDone={(patch) => {
                setAt((a) => ({ ...a, ...patch }))
                setStep(3)
              }}
            />
          )}

          {step === 3 && (
            <StepHabilitacao
              atendimento={at}
              onBack={() => setStep(2)}
              onDone={(resolvida) => {
                setAt((a) => ({ ...a, habilitacaoResolvida: resolvida }))
                setStep(4)
              }}
            />
          )}

          {step === 4 && (
            <StepTermos
              atendimento={at}
              onBack={() => setStep(3)}
              onDone={() => {
                setAt((a) => ({ ...a, aceiteFeito: true }))
                setStep(5)
              }}
            />
          )}

          {step === 5 && (
            <StepEmissao atendimento={at} onBack={() => setStep(4)} onReset={reset} />
          )}
        </CardContent>
      </Card>
    </div>
  )
}
