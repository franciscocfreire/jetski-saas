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
import { StepPagamento } from './_steps/step-pagamento'
import { StepEmissao } from './_steps/step-emissao'

const VAZIO: Atendimento = {
  temComprovanteResidencia: false, // padrão: sem comprovante → Declaração de Residência (1-C)
  temCha: false, // padrão: cliente NÃO tem CHA → emite temporária (EMA + GRU)
  habilitacaoResolvida: false,
  aceiteFeito: false,
  pagamentoRegistrado: false,
}

// Persistência do progresso do wizard (sobrevive a F5 / recarregamento da aba —
// ex.: iOS recarrega a página ao voltar do boleto). Por aba (sessionStorage).
// v2: passo "Pagamento" inserido (índices mudaram — não hidratar estado v1).
const STORAGE_KEY = 'balcao:wizard:v2'

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
  // Maior passo já alcançado — permite navegar livremente (ida e volta) entre os
  // passos já visitados, inclusive ao retomar uma reserva direto num passo avançado.
  const [maxStep, setMaxStep] = useState(0)
  useEffect(() => {
    setMaxStep((m) => Math.max(m, step))
  }, [step])

  // Restaura o progresso salvo na aba (a menos que esteja retomando via ?reserva=).
  const [hidratado, setHidratado] = useState(false)
  useEffect(() => {
    if (reservaId) {
      setHidratado(true)
      return
    }
    try {
      const raw = sessionStorage.getItem(STORAGE_KEY)
      if (raw) {
        const s = JSON.parse(raw) as { at?: Atendimento; step?: number; maxStep?: number }
        if (s.at?.cliente) {
          setAt(s.at)
          if (typeof s.step === 'number') setStep(s.step)
          setMaxStep(Math.max(s.maxStep ?? 0, s.step ?? 0))
        }
      }
    } catch {
      /* sessionStorage indisponível/corrompido → começa do zero */
    }
    setHidratado(true)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  // Salva o progresso a cada mudança (depois de hidratar, p/ não sobrescrever com vazio).
  useEffect(() => {
    if (!hidratado) return
    try {
      if (at.cliente) sessionStorage.setItem(STORAGE_KEY, JSON.stringify({ at, step, maxStep }))
      else sessionStorage.removeItem(STORAGE_KEY)
    } catch {
      /* ignora falhas de storage */
    }
  }, [hidratado, at, step, maxStep])

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
        const pagamentoRegistrado = reserva.pagamentoStatus === 'CONFIRMADO'
        setAt({
          cliente,
          reserva,
          modelo,
          temComprovanteResidencia: false,
          temCha: hab?.via === 'CHA',
          instrutorId: hab?.instrutorId,
          habilitacaoResolvida,
          aceiteFeito,
          pagamentoRegistrado,
        })
        // 1º passo pendente: habilitação (2) → termos (4) → pagamento (5) → emissão (6)
        setStep(!habilitacaoResolvida ? 2 : !aceiteFeito ? 4 : !pagamentoRegistrado ? 5 : 6)
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
    setMaxStep(0)
    try {
      sessionStorage.removeItem(STORAGE_KEY)
    } catch {
      /* ignora */
    }
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
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex items-center gap-3">
          <Store className="h-7 w-7 shrink-0 text-primary" />
          <div className="min-w-0">
            <h1 className="text-xl font-bold sm:text-2xl">Balcão — Atendimento assistido</h1>
            <p className="text-sm text-muted-foreground">
              Cadastro, documentos, pagamento, habilitação, termos e emissão.
            </p>
          </div>
        </div>
        <Button type="button" variant="outline" size="sm" onClick={reset} className="w-full sm:w-auto">
          <RotateCcw size={14} className="mr-1" /> Novo atendimento
        </Button>
      </div>

      <Card>
        <CardHeader>
          <Stepper
            steps={[...BALCAO_STEPS]}
            current={step}
            maxStep={maxStep}
            onStepClick={(i) => i <= maxStep && setStep(i)}
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
              atendimento={at}
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
            <StepHabilitacao
              atendimento={at}
              onBack={() => setStep(1)}
              onDone={(resolvida, temCha) => {
                setAt((a) => ({ ...a, habilitacaoResolvida: resolvida, temCha }))
                setStep(3)
              }}
            />
          )}

          {step === 3 && (
            <StepDocumentos
              atendimento={at}
              onBack={() => setStep(2)}
              onDone={(patch) => {
                setAt((a) => ({ ...a, ...patch }))
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
            <StepPagamento
              atendimento={at}
              onBack={() => setStep(4)}
              onDone={(reserva) => {
                setAt((a) => ({ ...a, reserva, pagamentoRegistrado: true }))
                setStep(6)
              }}
              onSkip={() => setStep(6)}
            />
          )}

          {step === 6 && (
            <StepEmissao atendimento={at} onBack={() => setStep(5)} onReset={reset} />
          )}
        </CardContent>
      </Card>
    </div>
  )
}
