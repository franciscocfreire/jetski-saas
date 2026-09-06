'use client'

import { Suspense, useCallback, useEffect, useMemo, useState } from 'react'
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
import { useVideoaulaObrigatoria } from '@/lib/hooks/use-videoaula-obrigatoria'
import {
  isStepKey,
  ordemDe,
  precisaOrientacoes,
  stepsAtivosPara,
  type Atendimento,
  type StepKey,
} from './types'
import { StepCliente } from './_steps/step-cliente'
import { StepDocumentos } from './_steps/step-documentos'
import { StepAluguel } from './_steps/step-aluguel'
import { StepHabilitacao } from './_steps/step-habilitacao'
import { StepOrientacoes } from './_steps/step-orientacoes'
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
// v3: navegação por CHAVE + passo "Orientações" (V063) — não hidratar v1/v2.
const STORAGE_KEY = 'balcao:wizard:v3'

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
  // Prefill do slot clicado na Agenda: começa atendimento NOVO com modelo/horário
  const prefillModeloId = search.get('modeloId')
  const prefillInicio = search.get('inicio')
  const temPrefill = !!(prefillModeloId || prefillInicio)
  const { obrigatoria: videoaulaObrigatoria } = useVideoaulaObrigatoria()

  const [stepKey, setStepKey] = useState<StepKey>('cliente')
  const [at, setAt] = useState<Atendimento>(() =>
    temPrefill
      ? { ...VAZIO, prefillModeloId: prefillModeloId ?? undefined, prefillInicio: prefillInicio ?? undefined }
      : VAZIO
  )
  const [resuming, setResuming] = useState(!!reservaId)
  // Maior passo já alcançado (ORDEM CANÔNICA, não índice visual) — permite navegar
  // livremente (ida e volta) entre os passos já visitados, inclusive ao retomar
  // uma reserva direto num passo avançado.
  const [maxOrdem, setMaxOrdem] = useState(0)
  useEffect(() => {
    setMaxOrdem((m) => Math.max(m, ordemDe(stepKey)))
  }, [stepKey])

  // Passos aplicáveis: "Orientações" só na via EMA (sempre exibida; a
  // obrigatoriedade de assistir até o fim é outro eixo — `videoaulaObrigatoria`).
  // eslint-disable-next-line react-hooks/exhaustive-deps
  const stepsAtivos = useMemo(() => stepsAtivosPara(at), [at.temCha])
  const orientacoesAtiva = precisaOrientacoes(at)
  // O passo atual conta como alcançado já neste render (o efeito de maxOrdem só
  // roda depois). Enquanto a videoaula OBRIGATÓRIA estiver pendente, o alcance
  // para em Orientações — é isto que impede pular pelo breadcrumb.
  const maxOrdemEfetivo = Math.max(maxOrdem, ordemDe(stepKey))
  const reachOrdem =
    orientacoesAtiva && videoaulaObrigatoria && !at.videoaulaEm
      ? Math.min(maxOrdemEfetivo, ordemDe('orientacoes'))
      : maxOrdemEfetivo
  const current = Math.max(0, stepsAtivos.findIndex((s) => s.key === stepKey))
  const maxStepAtivo = stepsAtivos.reduce((acc, s, i) => (ordemDe(s.key) <= reachOrdem ? i : acc), 0)

  /** Próximo/anterior passo ATIVO a partir de `key`, avaliado sobre o `at` informado. */
  const vizinho = useCallback(
    (key: StepKey, dir: 1 | -1, atRef: Atendimento): StepKey => {
      const ativos = stepsAtivosPara(atRef)
      const i = ativos.findIndex((s) => s.key === key)
      if (i < 0) {
        // passo atual não se aplica mais (ex.: virou CHA): vizinho pela ordem canônica
        const ord = ordemDe(key)
        const alvo =
          dir > 0
            ? ativos.find((s) => ordemDe(s.key) > ord) ?? ativos[ativos.length - 1]
            : [...ativos].reverse().find((s) => ordemDe(s.key) < ord) ?? ativos[0]
        return alvo.key
      }
      return ativos[Math.min(Math.max(i + dir, 0), ativos.length - 1)].key
    },
    []
  )
  // GOTCHA: os handlers de Habilitação/Orientações mudam `at` e navegam na mesma
  // tick — passar o `at` novo evita a closure stale de `stepsAtivos`.
  const proximo = (key: StepKey, atNovo: Atendimento = at) => setStepKey(vizinho(key, 1, atNovo))
  const anterior = (key: StepKey) => setStepKey(vizinho(key, -1, at))

  // Restaura o progresso salvo na aba (a menos que esteja retomando via
  // ?reserva= ou chegando com PREFILL do slot da Agenda — aí começa novo).
  const [hidratado, setHidratado] = useState(false)
  useEffect(() => {
    if (reservaId || temPrefill) {
      if (temPrefill) {
        try { sessionStorage.removeItem(STORAGE_KEY) } catch { /* ok */ }
        // limpa a URL p/ um F5 não re-semear o prefill sobre o progresso
        router.replace('/dashboard/balcao')
      }
      setHidratado(true)
      return
    }
    try {
      const raw = sessionStorage.getItem(STORAGE_KEY)
      if (raw) {
        const s = JSON.parse(raw) as { at?: Atendimento; stepKey?: unknown; maxOrdem?: number }
        if (s.at?.cliente) {
          setAt(s.at)
          const k: StepKey = isStepKey(s.stepKey) ? s.stepKey : 'cliente'
          setStepKey(k)
          setMaxOrdem(Math.max(s.maxOrdem ?? 0, ordemDe(k)))
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
      if (at.cliente) sessionStorage.setItem(STORAGE_KEY, JSON.stringify({ at, stepKey, maxOrdem }))
      else sessionStorage.removeItem(STORAGE_KEY)
    } catch {
      /* ignora falhas de storage */
    }
  }, [hidratado, at, stepKey, maxOrdem])

  // Saneamento: `stepsAtivos` pode mudar no meio do fluxo (CHA↔EMA, config
  // chegando tarde, sessão antiga). Se o passo atual sumiu ou está além do
  // alcance (videoaula pendente), reposiciona.
  useEffect(() => {
    if (!hidratado || resuming) return
    if (!stepsAtivos.some((s) => s.key === stepKey)) {
      setStepKey(vizinho(stepKey, 1, at))
      return
    }
    // Só acontece com a videoaula obrigatória pendente (sessão antiga / voltou de CHA p/ EMA).
    if (ordemDe(stepKey) > reachOrdem) setStepKey('orientacoes')
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [hidratado, resuming, stepsAtivos, stepKey, reachOrdem])

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
        const temCha = hab?.via === 'CHA'
        setAt({
          cliente,
          reserva,
          modelo,
          temComprovanteResidencia: false,
          temCha,
          instrutorId: hab?.instrutorId,
          habilitacaoResolvida,
          aceiteFeito,
          pagamentoRegistrado,
          videoaulaEm: hab?.videoaulaEm,
          videoaulaModo: hab?.videoaulaModo,
          videoaulaIdioma: hab?.videoaulaIdioma,
        })
        // 1º passo pendente: habilitação → orientações (EMA sem videoaula registrada; documentos
        // ficam antes, mas não são "pendência" de retomada) → termos → pagamento → emissão
        const videoaulaPendente = !!hab && hab.via === 'EMA' && precisaOrientacoes({ temCha }) && !hab.videoaulaEm
        setStepKey(
          !habilitacaoResolvida
            ? 'habilitacao'
            : videoaulaPendente
              ? 'orientacoes'
              : !aceiteFeito
                ? 'termos'
                : !pagamentoRegistrado
                  ? 'pagamento'
                  : 'emissao'
        )
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
    setStepKey('cliente')
    setMaxOrdem(0)
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
              Cadastro, habilitação, documentos, orientações, termos, pagamento e emissão.
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
            steps={[...stepsAtivos]}
            current={current}
            maxStep={maxStepAtivo}
            onStepClick={(i) => i <= maxStepAtivo && setStepKey(stepsAtivos[i].key)}
          />
        </CardHeader>
        <CardContent>
          {at.cliente && (
            <div className="mb-4 rounded-md bg-muted/40 px-3 py-2 text-xs text-muted-foreground">
              Atendimento: <span className="font-medium text-foreground">{at.cliente.nome}</span>
              {at.reserva && <> · reserva <span className="font-mono">{at.reserva.id.slice(0, 8)}</span></>}
            </div>
          )}

          {stepKey === 'cliente' && (
            <StepCliente
              atendimento={at}
              onDone={(cliente) => {
                setAt((a) => ({ ...a, cliente }))
                proximo('cliente')
              }}
            />
          )}

          {stepKey === 'aluguel' && (
            <StepAluguel
              atendimento={at}
              onBack={() => anterior('aluguel')}
              onDone={(reserva, modelo) => {
                setAt((a) => ({ ...a, reserva, modelo }))
                proximo('aluguel')
              }}
            />
          )}

          {stepKey === 'habilitacao' && (
            <StepHabilitacao
              atendimento={at}
              onBack={() => anterior('habilitacao')}
              onDone={(resolvida, temCha) => {
                const novo = { ...at, habilitacaoResolvida: resolvida, temCha }
                setAt(novo)
                proximo('habilitacao', novo)
              }}
            />
          )}

          {stepKey === 'orientacoes' && (
            <StepOrientacoes
              atendimento={at}
              onBack={() => anterior('orientacoes')}
              onDone={(patch) => {
                const novo = { ...at, ...patch }
                setAt(novo)
                proximo('orientacoes', novo)
              }}
            />
          )}

          {stepKey === 'documentos' && (
            <StepDocumentos
              atendimento={at}
              onBack={() => anterior('documentos')}
              onDone={(patch) => {
                setAt((a) => ({ ...a, ...patch }))
                proximo('documentos')
              }}
            />
          )}

          {stepKey === 'termos' && (
            <StepTermos
              atendimento={at}
              onBack={() => anterior('termos')}
              onDone={() => {
                setAt((a) => ({ ...a, aceiteFeito: true }))
                proximo('termos')
              }}
            />
          )}

          {stepKey === 'pagamento' && (
            <StepPagamento
              atendimento={at}
              onBack={() => anterior('pagamento')}
              onDone={(reserva) => {
                setAt((a) => ({ ...a, reserva, pagamentoRegistrado: true }))
                proximo('pagamento')
              }}
              onSkip={() => proximo('pagamento')}
            />
          )}

          {stepKey === 'emissao' && (
            <StepEmissao atendimento={at} onBack={() => anterior('emissao')} onReset={reset} />
          )}
        </CardContent>
      </Card>
    </div>
  )
}
