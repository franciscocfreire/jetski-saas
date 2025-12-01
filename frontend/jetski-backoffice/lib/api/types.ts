// Base types
export interface BaseEntity {
  id: string
  tenantId: string
  createdAt?: string
  updatedAt?: string
}

// Jetski Module
export type JetskiStatus = 'DISPONIVEL' | 'LOCADO' | 'MANUTENCAO' | 'INATIVO'

export interface Modelo extends BaseEntity {
  nome: string
  fabricante?: string
  potenciaHp?: number
  capacidadePessoas: number
  precoBaseHora: number
  toleranciaMin?: number
  taxaHoraExtra?: number
  incluiCombustivel?: boolean
  caucao?: number
  fotoReferenciaUrl?: string
  pacotesJson?: string
  ativo: boolean
}

export interface Jetski extends BaseEntity {
  modeloId: string
  modelo?: Modelo
  serie: string  // Backend field name
  ano?: number
  horimetroAtual: number  // Backend field name
  status: JetskiStatus
  ativo: boolean
}

export interface JetskiCreateRequest {
  modeloId: string
  serie: string  // Backend field name
  ano?: number
  horimetroAtual?: number  // Backend field name
  status?: JetskiStatus
}

export interface JetskiUpdateRequest extends Partial<JetskiCreateRequest> {
  ativo?: boolean
}

// Cliente Module
export interface Cliente extends BaseEntity {
  nome: string
  email?: string
  telefone?: string
  cpf?: string
  observacoes?: string
}

export interface ClienteCreateRequest {
  nome: string
  email?: string
  telefone?: string
  cpf?: string
  observacoes?: string
}

// Vendedor Module
export interface Vendedor extends BaseEntity {
  nome: string
  email?: string
  telefone?: string
  comissaoPercentual: number
  ativo: boolean
}

export interface VendedorCreateRequest {
  nome: string
  email?: string
  telefone?: string
  comissaoPercentual: number
}

// Reserva Module
export type ReservaStatus = 'PENDENTE' | 'CONFIRMADA' | 'CANCELADA' | 'CONCLUIDA'
export type ReservaPrioridade = 'NORMAL' | 'ALTA' | 'URGENTE'

export interface Reserva extends BaseEntity {
  modeloId: string
  modelo?: Modelo
  jetskiId?: string
  jetski?: Jetski
  clienteId: string
  cliente?: Cliente
  vendedorId?: string
  vendedor?: Vendedor
  dataInicio: string
  dataFimPrevista: string
  status: ReservaStatus
  prioridade: ReservaPrioridade
  sinalPago: boolean
  valorSinal?: number
  observacoes?: string
}

export interface ReservaCreateRequest {
  modeloId: string
  clienteId: string
  vendedorId?: string
  dataInicio: string
  dataFimPrevista: string
  prioridade?: ReservaPrioridade
  observacoes?: string
}

// Locacao Module
export type LocacaoStatus = 'EM_CURSO' | 'FINALIZADA' | 'CANCELADA'
export type ModalidadePreco = 'PRECO_FECHADO' | 'DIARIA' | 'MEIA_DIARIA'

export interface Locacao extends BaseEntity {
  reservaId?: string
  jetskiId: string
  clienteId?: string
  vendedorId?: string

  // Denormalized fields from backend for display
  jetskiSerie?: string
  jetskiModeloNome?: string
  clienteNome?: string

  // Check-in data
  dataCheckIn: string
  horimetroInicio: number
  duracaoPrevista?: number

  // Check-out data
  dataCheckOut?: string
  horimetroFim?: number
  minutosUsados?: number
  minutosFaturaveis?: number

  // Negotiated price
  valorNegociado?: number
  motivoDesconto?: string

  // Pricing mode
  modalidadePreco?: ModalidadePreco

  // Values
  valorBase?: number
  valorItensOpcionais?: number
  valorTotal?: number

  // Status
  status: LocacaoStatus
  observacoes?: string

  // Checklists (RN05)
  checklistSaidaJson?: string
  checklistEntradaJson?: string
}

export interface CheckInFromReservaRequest {
  reservaId: string
  horimetroInicio: number
  observacoes?: string
  checklistSaidaJson?: string
  valorNegociado?: number
  motivoDesconto?: string
  modalidadePreco?: ModalidadePreco
}

export interface CheckInWalkInRequest {
  jetskiId: string
  clienteId?: string
  vendedorId?: string
  horimetroInicio: number
  duracaoPrevista: number  // Required - minimum 15 minutes
  observacoes?: string
  checklistSaidaJson?: string
  valorNegociado?: number
  motivoDesconto?: string
  modalidadePreco?: ModalidadePreco
}

export interface CheckOutRequest {
  horimetroFim: number
  observacoes?: string
  checklistEntradaJson: string
  skipPhotos?: boolean
}

// Manutencao Module
export type ManutencaoTipo = 'PREVENTIVA' | 'CORRETIVA'
export type ManutencaoStatus = 'ABERTA' | 'EM_ANDAMENTO' | 'CONCLUIDA' | 'CANCELADA'
export type ManutencaoPrioridade = 'BAIXA' | 'MEDIA' | 'ALTA' | 'URGENTE'

export interface Manutencao extends BaseEntity {
  jetskiId: string
  jetski?: Jetski
  mecanicoId?: string
  tipo: ManutencaoTipo
  prioridade: ManutencaoPrioridade
  dtAbertura: string
  dtPrevistaInicio?: string
  dtInicioReal?: string
  dtPrevistaFim?: string
  dtConclusao?: string
  descricaoProblema: string
  diagnostico?: string
  solucao?: string
  pecasJson?: string
  valorPecas?: number
  valorMaoObra?: number
  valorTotal?: number
  horimetroAbertura?: number
  horimetroConclusao?: number
  status: ManutencaoStatus
  observacoes?: string
}

export interface ManutencaoCreateRequest {
  jetskiId: string
  mecanicoId?: string
  tipo: ManutencaoTipo
  prioridade?: ManutencaoPrioridade
  descricaoProblema: string
  observacoes?: string
}

// Fechamento Module
export interface FechamentoDiario extends BaseEntity {
  data: string
  totalLocacoes: number
  valorTotalBruto: number
  valorTotalLiquido: number
  totalCombustivel: number
  totalComissoes: number
  fechado: boolean
}

export interface FechamentoMensal extends BaseEntity {
  mes: number
  ano: number
  totalLocacoes: number
  valorTotalBruto: number
  valorTotalLiquido: number
  totalCombustivel: number
  totalComissoes: number
  totalManutencao: number
  fechado: boolean
}

// User Tenants
export interface TenantSummary {
  id: string
  slug: string
  razaoSocial: string
  status: string
  roles: string[]
}

export interface UserTenantsResponse {
  accessType: 'LIMITED' | 'UNRESTRICTED'
  totalTenants: number
  message?: string
  tenants: TenantSummary[]
}

// Pagination
export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  size: number
  number: number
  first: boolean
  last: boolean
}

// API Response wrapper
export interface ApiResponse<T> {
  data: T
  message?: string
}

// API Error
export interface ApiError {
  status: number
  message: string
  errors?: Record<string, string[]>
}
