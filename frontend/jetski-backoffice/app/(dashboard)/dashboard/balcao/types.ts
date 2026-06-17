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
  // habilitação/aceite/emissão preenchidos nos passos 4-6
  habilitacaoResolvida: boolean
  aceiteFeito: boolean
}

export const BALCAO_STEPS = [
  { key: 'cliente', label: 'Cliente' },
  { key: 'documentos', label: 'Documentos' },
  { key: 'aluguel', label: 'Aluguel & Pagamento' },
  { key: 'habilitacao', label: 'Habilitação' },
  { key: 'termos', label: 'Termos' },
  { key: 'emissao', label: 'Emissão' },
] as const
