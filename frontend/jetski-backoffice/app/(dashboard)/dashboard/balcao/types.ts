import type { Cliente, Reserva, Modelo, VideoaulaIdioma, VideoaulaModo } from '@/lib/api/types'
import type { Address } from '@/components/address-form'

/** Estado compartilhado do atendimento de balcão (passa entre os passos). */
export type Atendimento = {
  cliente?: Cliente
  reserva?: Reserva
  modelo?: Modelo
  endereco?: Address
  temComprovanteResidencia: boolean
  temCha: boolean
  /** Instrutor (Atestado 5-B-1) — coletado no Passeio & Preço (EMA). */
  instrutorId?: string
  // habilitação/aceite/pagamento/emissão preenchidos nos passos seguintes
  habilitacaoResolvida: boolean
  aceiteFeito: boolean
  /** Pagamento presencial integral registrado (ou pulado conscientemente). */
  pagamentoRegistrado: boolean
  /** Videoaula (EMA, V063): vem de reserva_habilitacao — preenchida em Orientações ou na retomada. */
  videoaulaEm?: string
  videoaulaModo?: VideoaulaModo
  videoaulaIdioma?: VideoaulaIdioma
  /** Prefill do slot clicado na Agenda (consumido no passo Passeio & Preço). */
  prefillModeloId?: string
  prefillInicio?: string
}

/**
 * Ordem canônica dos passos. A navegação é por CHAVE (não por índice): o passo
 * "Orientações" só existe na via EMA (cliente sem CHA), então o índice visual
 * muda por atendimento — ver `stepsAtivosPara`.
 */
export const BALCAO_STEPS = [
  { key: 'cliente', label: 'Cliente' },
  { key: 'aluguel', label: 'Passeio & Preço' },
  { key: 'habilitacao', label: 'Habilitação' },
  { key: 'documentos', label: 'Documentos' },
  { key: 'orientacoes', label: 'Orientações' },
  { key: 'termos', label: 'Termos' },
  { key: 'pagamento', label: 'Pagamento' },
  { key: 'emissao', label: 'Emissão' },
] as const

export type StepKey = (typeof BALCAO_STEPS)[number]['key']
export type BalcaoStep = (typeof BALCAO_STEPS)[number]

export const isStepKey = (k: unknown): k is StepKey =>
  typeof k === 'string' && BALCAO_STEPS.some((s) => s.key === k)

/** Posição canônica do passo (comparações de "alcance" independem dos passos ativos). */
export const ordemDe = (k: StepKey) => BALCAO_STEPS.findIndex((s) => s.key === k)

/**
 * O passo Orientações se aplica a este atendimento? Sempre na via EMA: a videoaula
 * é SEMPRE exibida; o que a empresa liga/desliga (V063) é a obrigação de assistir
 * até o fim para liberar o Continuar.
 */
export const precisaOrientacoes = (at: Pick<Atendimento, 'temCha'>) => !at.temCha

/** Passos aplicáveis a este atendimento — função pura, usada no render E nos handlers. */
export function stepsAtivosPara(at: Pick<Atendimento, 'temCha'>): readonly BalcaoStep[] {
  return BALCAO_STEPS.filter((s) => s.key !== 'orientacoes' || precisaOrientacoes(at))
}
