import type { Cliente, Reserva, Modelo } from '@/lib/api/types'
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
  // habilitação/aceite/emissão preenchidos nos passos seguintes
  habilitacaoResolvida: boolean
  aceiteFeito: boolean
}

export const BALCAO_STEPS = [
  { key: 'cliente', label: 'Cliente' },
  { key: 'aluguel', label: 'Passeio & Preço' },
  { key: 'habilitacao', label: 'Habilitação' },
  { key: 'documentos', label: 'Documentos' },
  { key: 'termos', label: 'Termos' },
  { key: 'emissao', label: 'Emissão' },
] as const
