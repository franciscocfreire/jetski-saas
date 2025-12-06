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

// Item Opcional Module
export interface ItemOpcional extends BaseEntity {
  nome: string
  descricao?: string
  precoBase: number
  ativo: boolean
}

export interface ItemOpcionalCreateRequest {
  nome: string
  descricao?: string
  precoBase: number
  ativo?: boolean
}

// Selected optional item for check-in
export interface SelectedItemOpcional {
  itemOpcionalId: string
  quantidade: number
  valorNegociado?: number
}

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
  dataCheckIn?: string  // ISO 8601, optional - defaults to current time if not provided
  checklistSaidaJson?: string
  valorNegociado?: number
  motivoDesconto?: string
  modalidadePreco?: ModalidadePreco
  itensOpcionais?: SelectedItemOpcional[]
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

// Dashboard Metrics (cached)
export interface DashboardMetrics {
  receitaHoje: number
  receitaMes: number
  locacoesHoje: number
  locacoesMes: number
  dataReferencia: string
  inicioMes: string
  calculatedAt: string
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

// ==========================================
// User/Member Management Types
// ==========================================

// Member Summary
export interface MemberSummary {
  usuarioId: string
  email: string
  nome: string
  papeis: string[]
  ativo: boolean
  joinedAt: string
  lastUpdated: string
}

// Plan Limit Info
export interface PlanLimitInfo {
  maxUsuarios: number
  currentActive: number
  available: number
  limitReached: boolean
}

// List Members Response
export interface ListMembersResponse {
  members: MemberSummary[]
  totalCount: number
  activeCount: number
  inactiveCount: number
  planLimit: PlanLimitInfo
}

// Invite User Request
export interface InviteUserRequest {
  email: string
  nome: string
  papeis: string[]
}

// Update Roles Request
export interface UpdateRolesRequest {
  papeis: string[]
}

// Convite (Invitation) Summary
export interface ConviteSummary {
  id: string
  email: string
  nome: string
  papeis: string[]
  createdAt: string
  expiresAt: string
  status: 'PENDING' | 'EXPIRED'
  emailSentCount: number
  lastEmailSentAt?: string
}

// Available Roles
export const AVAILABLE_ROLES = [
  { value: 'ADMIN_TENANT', label: 'Administrador', description: 'Acesso total ao tenant' },
  { value: 'GERENTE', label: 'Gerente', description: 'Gestão de operações e equipe' },
  { value: 'OPERADOR', label: 'Operador', description: 'Check-in/out e operações de píer' },
  { value: 'VENDEDOR', label: 'Vendedor', description: 'Reservas e comissões' },
  { value: 'MECANICO', label: 'Mecânico', description: 'Ordens de manutenção' },
  { value: 'FINANCEIRO', label: 'Financeiro', description: 'Fechamentos e relatórios' },
] as const

export type RoleValue = typeof AVAILABLE_ROLES[number]['value']

// ==========================================
// Tenant Signup Types
// ==========================================

// Request for new user creating a tenant (public signup)
export interface TenantSignupRequest {
  razaoSocial: string
  slug: string
  cnpj?: string
  adminEmail: string
  adminNome: string
}

// Request for existing user creating a new tenant (authenticated)
export interface CreateTenantRequest {
  razaoSocial: string
  slug: string
  cnpj?: string
}

// Response for tenant signup/creation
export interface TenantSignupResponse {
  tenantId: string
  slug: string
  razaoSocial: string
  adminEmail?: string
  message: string
  trialExpiresAt: string
  requiresActivation: boolean
}

// Signup activation request
export interface SignupActivationRequest {
  token: string
  temporaryPassword: string
}

// Check slug availability response
export interface SlugAvailabilityResponse {
  available: boolean
}
