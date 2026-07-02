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
  exibirNoMarketplace?: boolean
}

// Modelo Midia (Photos/Videos)
export type TipoMidia = 'IMAGEM' | 'VIDEO'

export interface ModeloMidia {
  id: string
  modeloId: string
  tipo: TipoMidia
  url: string
  thumbnailUrl?: string
  ordem: number
  principal: boolean
  titulo?: string
  createdAt?: string
}

export interface ModeloMidiaCreateRequest {
  tipo: TipoMidia
  url: string
  thumbnailUrl?: string
  ordem?: number
  principal?: boolean
  titulo?: string
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
export type ClienteOrigem = 'PORTAL' | 'BALCAO'
export type ClienteStatusConta = 'PRE_CONTA' | 'CONVIDADA' | 'ATIVA' | 'SEM_LOGIN'

export interface Cliente extends BaseEntity {
  nome: string
  email?: string
  telefone?: string
  whatsapp?: string
  /** Documento (CPF) — campo canônico do backend. */
  documento?: string
  rg?: string
  orgaoEmissor?: string
  nacionalidade?: string
  naturalidade?: string
  /** @deprecated usar `documento` (mantido p/ compat. de telas antigas). */
  cpf?: string
  estrangeiro?: boolean
  dataNascimento?: string
  genero?: string
  enderecoJson?: string
  termoAceite?: boolean
  origem?: ClienteOrigem
  statusConta?: ClienteStatusConta
  ativo?: boolean
  observacoes?: string
}

export interface ClienteCreateRequest {
  nome: string
  email?: string
  telefone?: string
  cpf?: string
  documento?: string
  rg?: string
  orgaoEmissor?: string
  nacionalidade?: string
  naturalidade?: string
  estrangeiro?: boolean
  whatsapp?: string
  dataNascimento?: string
  genero?: string
  enderecoJson?: string
  termoAceite?: boolean
  observacoes?: string
}

/** Pré-conta de balcão (atendimento assistido). */
export interface ClientePreContaRequest {
  nome: string
  documento?: string
  email?: string
  telefone?: string
  whatsapp?: string
}

// PIX Key Types
export type TipoChavePix = 'CPF' | 'CNPJ' | 'EMAIL' | 'TELEFONE' | 'ALEATORIA'

export const TIPOS_CHAVE_PIX = [
  { value: 'CPF', label: 'CPF' },
  { value: 'CNPJ', label: 'CNPJ' },
  { value: 'EMAIL', label: 'E-mail' },
  { value: 'TELEFONE', label: 'Telefone' },
  { value: 'ALEATORIA', label: 'Chave Aleatória' },
] as const

// Vendedor Module
export interface Vendedor extends BaseEntity {
  nome: string
  email?: string
  telefone?: string
  chavePix?: string
  tipoChavePix?: TipoChavePix
  comissaoPercentual: number
  diariaBase?: number
  ativo: boolean
}

export interface VendedorCreateRequest {
  nome: string
  email?: string
  telefone?: string
  chavePix?: string
  tipoChavePix?: TipoChavePix
  comissaoPercentual: number
  diariaBase?: number
}

export interface VendedorUpdateRequest {
  nome?: string
  email?: string
  telefone?: string
  chavePix?: string
  tipoChavePix?: TipoChavePix
  comissaoPercentual?: number
  diariaBase?: number
}

// Reserva Module
export type ReservaStatus =
  | 'RASCUNHO'
  | 'PENDENTE'
  | 'CONFIRMADA'
  | 'CANCELADA'
  | 'FINALIZADA'
  | 'EXPIRADA'
export type ReservaPrioridade = 'ALTA' | 'BAIXA'
export type PagamentoTipo = 'SINAL' | 'TOTAL'
export type PagamentoStatus = 'AGUARDANDO' | 'EM_ANALISE' | 'CONFIRMADO' | 'RECUSADO'

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
  sinalPagoEm?: string
  // Pagamento (balcão / validação)
  pagamentoTipo?: PagamentoTipo
  pagamentoStatus?: PagamentoStatus
  pagamentoMotivoRecusa?: string
  valorTotal?: number
  documentoEmitidoEm?: string
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

// Balcão — pagamento / habilitação / aceite / emissão / claim
export interface ConfirmarPagamentoRequest {
  tipo: PagamentoTipo
  valorPago?: number
}

export interface RecusarPagamentoRequest {
  motivo: string
}

export type HabilitacaoVia = 'CHA' | 'EMA'

export interface HabilitacaoRequest {
  via: HabilitacaoVia
  // CHA
  chaCategoria?: string
  chaNumero?: string
  chaValidade?: string
  // EMA
  videoaulaAssistida?: boolean
  anexoSaude?: boolean
  anexoRegras?: boolean
  anexoResidencia?: boolean
  // Autodeclaração de saúde (5-C)
  usaLentes?: boolean
  usaAparelho?: boolean
  // Instrutor (EAMA) do Atestado de Demonstração (5-B-1)
  instrutorId?: string
  // GRU (EMA)
  gruNumero?: string
  gruValor?: number
  gruPago?: boolean
}

// Instrutor (EAMA) — Anexo 5-B-1
export interface Instrutor extends BaseEntity {
  nome: string
  rg?: string
  orgaoEmissor?: string
  cpf?: string
  cha?: string
  dataEmissao?: string
  temAssinatura?: boolean
  ativo: boolean
}

export interface InstrutorCreateRequest {
  nome: string
  rg?: string
  orgaoEmissor?: string
  cpf?: string
  cha?: string
  dataEmissao?: string
  /** PNG da assinatura em base64 (dataURL); embutida no 5-B-1. */
  assinaturaBase64?: string
}

export interface Habilitacao {
  reservaId: string
  via: HabilitacaoVia
  chaCategoria?: string
  chaNumero?: string
  chaValidade?: string
  videoaulaEm?: string
  anexoSaude: boolean
  anexoRegras: boolean
  anexoResidencia: boolean
  usaLentes?: boolean
  usaAparelho?: boolean
  instrutorId?: string
  gruNumero?: string
  gruValor?: number
  gruPago: boolean
  gruPagoEm?: string
  gruPixCopiaECola?: string
  gruPixExpiracao?: string
  gruBoletoDisponivel?: boolean
  gruComprovanteDisponivel?: boolean
  resolvida: boolean
}

/** Resposta da verificação de pagamento do PIX da GRU. */
export interface HabilitacaoGruPagamentoResponse {
  pago: boolean
  situacao: string
  comprovanteDisponivel: boolean
}

/** Resposta da geração automática da GRU + PIX (Marinha/PagTesouro). */
export interface HabilitacaoGruResponse {
  sucesso: boolean
  reaproveitada: boolean
  gruNumero?: string
  gruValor?: number
  gruPago?: boolean
  pixCopiaECola?: string
  pixQrPngBase64?: string
  pixExpiracao?: string
  idMarinha?: string
  erroCodigo?: string
  erroMensagem?: string
}

/** Resposta da geração do boleto da GRU (PDF). O PDF é baixado via endpoint de stream. */
export interface HabilitacaoGruBoletoResponse {
  sucesso: boolean
  reaproveitada: boolean
  idMarinha?: string
  gruNumero?: string
  gruValor?: number
  erroCodigo?: string
  erroMensagem?: string
}

export type AceiteMetodo = 'SIGNATURE_PAD' | 'PAPEL'

export interface AceiteRequest {
  metodo: AceiteMetodo
  /** PNG em base64 (dataURL ou puro); obrigatório p/ SIGNATURE_PAD. */
  assinaturaBase64?: string
}

export interface Aceite {
  reservaId: string
  metodo: AceiteMetodo
  assinaturaS3Key?: string
  hashSha256?: string
  aceitoEm: string
}

/** Resultado de POST /reservas/{id}/emitir-documentos. */
export interface ResultadoEmissao {
  documentoId: string
  s3Key: string
  hashSha256: string
  downloadUrl: string
  gruNumero?: string
  gruValor?: string
  enviadoMarinha: boolean
  enviadoCliente: boolean
  docCompleta: boolean
  pendencias: string[]
}

/** Documento emitido (consulta por cliente). */
export interface DocumentoEmitido {
  id: string
  reservaId: string
  clienteId?: string
  clienteNome?: string
  emitidoEm: string
  hashSha256?: string
  downloadUrl?: string
}

export interface ClaimResult {
  clienteId: string
  token: string
  link: string
  expiraEm: string
  canais: string
  enviado: boolean
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
  vendedorNome?: string

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

// Request for editing a finalized rental (before daily closing)
export interface EditFinalizadaRequest {
  dataCheckIn?: string
  dataCheckOut?: string
  horimetroInicio?: number
  horimetroFim?: number
  minutosUsados?: number
  minutosFaturaveis?: number
  valorBase?: number
  valorNegociado?: number
  valorTotal?: number
  combustivelCusto?: number
  vendedorId?: string
  motivoDesconto?: string
  observacoes?: string
  motivoEdicao: string  // Required - audit trail
}

// Manutencao Module
export type ManutencaoTipo = 'PREVENTIVA' | 'CORRETIVA'
export type ManutencaoStatus = 'ABERTA' | 'EM_ANDAMENTO' | 'AGUARDANDO_PECAS' | 'CONCLUIDA' | 'CANCELADA'
export type ManutencaoPrioridade = 'BAIXA' | 'MEDIA' | 'ALTA' | 'URGENTE'

// Resumo do jetski retornado na resposta de manutenção
export interface JetskiResumo {
  id: string
  serie: string
  modeloNome?: string
  status?: string
}

export interface Manutencao extends BaseEntity {
  jetskiId: string
  jetski?: JetskiResumo
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

export interface ManutencaoFinishRequest {
  horimetroFechamento: number
  valorPecas?: number
  valorMaoObra?: number
  observacoesFinais?: string
}

// Fechamento Module
export type FechamentoStatus = 'aberto' | 'fechado' | 'aprovado'

export interface FechamentoDiarioResponse {
  id: string
  dtReferencia: string
  operadorId?: string
  // Consolidacao
  totalLocacoes: number
  totalFaturado: number
  totalCombustivel: number
  totalComissoes: number
  totalDinheiro: number
  totalCartao: number
  totalPix: number
  totalDespesasOperacionais?: number
  totalDiariasVendedores?: number
  // Status & Lock
  status: FechamentoStatus
  dtFechamento?: string
  bloqueado: boolean
  // Metadata
  observacoes?: string
  divergenciasJson?: string
  createdAt: string
  updatedAt: string
  // Hash e divergencia
  valoresHash?: string
  hasDivergencia?: boolean
}

// Locacao alterada apos consolidacao
export interface LocacaoAlterada {
  locacaoId: string
  clienteNome?: string
  jetskiIdentificacao?: string
  dataCheckOut?: string
  valorAnterior?: number
  valorAtual?: number
  diferenca?: number
  dataAlteracao?: string
  alteradoPor?: string
}

// Divergencia detectada em fechamento
export interface DivergenciaResponse {
  fechamentoId: string
  dtReferencia: string
  status: FechamentoStatus
  // Valores armazenados (consolidacao anterior)
  totalLocacoesArmazenado: number
  totalFaturadoArmazenado: number
  totalCombustivelArmazenado: number
  totalComissoesArmazenado: number
  // Valores atuais (recalculados)
  totalLocacoesAtual: number
  totalFaturadoAtual: number
  totalCombustivelAtual: number
  totalComissoesAtual: number
  // Diferencas
  diferencaLocacoes: number
  diferencaFaturado: number
  diferencaCombustivel: number
  diferencaComissoes: number
  // Lista detalhada de locacoes alteradas
  locacoesAlteradas?: LocacaoAlterada[]
  ultimaConsolidacao?: string
  mensagem?: string
}

export interface FechamentoMensalResponse {
  id: string
  ano: number
  mes: number
  operadorId?: string
  // Consolidação
  totalLocacoes: number
  totalFaturado: number
  totalCustos: number
  totalComissoes: number
  totalManutencoes: number
  totalDespesasOperacionais?: number
  totalDiariasVendedores?: number
  resultadoLiquido: number
  // Status & Lock
  status: FechamentoStatus
  dtFechamento?: string
  bloqueado: boolean
  // Metadata
  observacoes?: string
  relatorioUrl?: string
  createdAt: string
  updatedAt: string
}

// Legacy aliases (mantidos para compatibilidade)
export type FechamentoDiario = FechamentoDiarioResponse
export type FechamentoMensal = FechamentoMensalResponse

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

// Platform (super admin)
export interface PendingTenant {
  tenantId: string
  slug: string
  razaoSocial: string
  cnpj: string | null
  createdAt: string
}

export interface TenantStatusResult {
  tenantId: string
  status: string
  message: string
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

// Auditoria Module
export type AuditoriaAcao = 'CHECK_IN' | 'CHECK_OUT' | string
export type AuditoriaEntidade = 'LOCACAO' | 'JETSKI' | 'RESERVA' | string

export interface Auditoria {
  id: string
  acao: AuditoriaAcao
  entidade: AuditoriaEntidade
  entidadeId: string
  usuarioId?: string
  usuarioNome: string
  ip?: string
  traceId?: string
  createdAt: string
  dadosAnteriores?: Record<string, unknown>
  dadosNovos?: Record<string, unknown>
}

export interface AuditoriaFilters {
  acao?: string
  entidade?: string
  entidadeId?: string
  usuarioId?: string
  dataInicio?: string
  dataFim?: string
}

export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  size: number
  number: number
  first: boolean
  last: boolean
  empty: boolean
}

// ==========================================
// Despesa Operacional Module
// ==========================================

export type CategoriaDespesa =
  | 'DIARIA_FUNCIONARIO'
  | 'REFEICAO'
  | 'COMBUSTIVEL_PROPRIO'
  | 'LIMPEZA'
  | 'TAXA_ADMINISTRATIVA'
  | 'TRANSPORTE'
  | 'MATERIAL_ESCRITORIO'
  | 'OUTROS'

export type StatusDespesa = 'PENDENTE' | 'APROVADA' | 'REJEITADA' | 'PAGA'

export interface DespesaOperacional extends BaseEntity {
  dtReferencia: string
  categoria: CategoriaDespesa
  descricao?: string
  valor: number
  responsavelId?: string
  status: StatusDespesa
  aprovadoPor?: string
  aprovadoEm?: string
  pagoPor?: string
  pagoEm?: string
  referenciaPagamento?: string
  observacoes?: string
}

export interface DespesaOperacionalCreateRequest {
  dtReferencia: string
  categoria: CategoriaDespesa
  descricao?: string
  valor: number
  responsavelId?: string
  observacoes?: string
}

export interface DespesaOperacionalUpdateRequest {
  dtReferencia?: string
  categoria?: CategoriaDespesa
  descricao?: string
  valor?: number
  responsavelId?: string
  observacoes?: string
}

export interface PagarDespesaRequest {
  referenciaPagamento?: string
}

export const CATEGORIAS_DESPESA = [
  { value: 'DIARIA_FUNCIONARIO', label: 'Diária Funcionário', icon: 'user' },
  { value: 'REFEICAO', label: 'Refeição', icon: 'utensils' },
  { value: 'COMBUSTIVEL_PROPRIO', label: 'Combustível Interno', icon: 'fuel' },
  { value: 'LIMPEZA', label: 'Limpeza', icon: 'sparkles' },
  { value: 'TAXA_ADMINISTRATIVA', label: 'Taxa Administrativa', icon: 'receipt' },
  { value: 'TRANSPORTE', label: 'Transporte', icon: 'car' },
  { value: 'MATERIAL_ESCRITORIO', label: 'Material Escritório', icon: 'package' },
  { value: 'OUTROS', label: 'Outros', icon: 'more-horizontal' },
] as const

export const STATUS_DESPESA = [
  { value: 'PENDENTE', label: 'Pendente', color: 'yellow' },
  { value: 'APROVADA', label: 'Aprovada', color: 'blue' },
  { value: 'REJEITADA', label: 'Rejeitada', color: 'red' },
  { value: 'PAGA', label: 'Paga', color: 'green' },
] as const

// ==========================================
// Dashboard Financeiro Module
// ==========================================

export type IndicadorFinanceiro = 'POSITIVO' | 'NEGATIVO' | 'NEUTRO'

export interface DiaFinanceiro {
  data: string
  receita: number
  despesasOperacionais: number
  combustivel: number
  comissoes: number
  diariasVendedores: number
  manutencoes: number
  totalDespesas: number
  saldo: number
  indicador: IndicadorFinanceiro
  statusFechamento?: FechamentoStatus
  temFechamento: boolean
}

export interface CalendarioFinanceiroResponse {
  ano: number
  mes: number
  totalReceitas: number
  totalDespesas: number
  saldoMes: number
  diasPositivos: number
  diasNegativos: number
  diasNeutros: number
  dias: DiaFinanceiro[]
}

export interface ReceitaDespesaDia {
  data: string
  receita: number
  despesasOperacionais: number
  combustivel: number
  comissoes: number
  diariasVendedores: number
  manutencoes: number
  totalDespesas: number
  saldo: number
}

export interface DRESimplificado {
  ano: number
  mes: number
  // (+) Receita Bruta
  receitaBruta: number
  // (-) Deduções
  deducoes: number
  // (=) Receita Líquida
  receitaLiquida: number
  // (-) Custos Variáveis
  combustivel: number
  comissoes: number
  totalCustosVariaveis: number
  // (=) Lucro Bruto
  lucroBruto: number
  // (-) Despesas Operacionais
  despesasDiarias: number
  diariasVendedores: number
  despesasRefeicao: number
  despesasTransporte: number
  despesasLimpeza: number
  outrasDesepsas: number
  totalDespesasOperacionais: number
  // (-) Manutenções
  manutencoes: number
  // (=) Resultado Líquido
  resultadoLiquido: number
  // Margem %
  margemLiquida: number
}

export interface ComissaoPendenteItem {
  id: string
  vendedorId: string
  vendedorNome?: string
  valor: number
  dtReferencia: string
}

export interface DespesaPendenteItem {
  id: string
  dtReferencia: string
  categoria: CategoriaDespesa
  descricao?: string
  valor: number
}

export interface FechamentoAbertoItem {
  id: string
  dtReferencia: string
  totalFaturado: number
}

export interface RegistrosPendentes {
  // Comissões
  quantidadeComissoesPendentes: number
  totalComissoesPendentes: number
  comissoesPendentes: ComissaoPendenteItem[]
  // Despesas pendentes de aprovação
  quantidadeDespesasPendentes: number
  totalDespesasPendentes: number
  despesasPendentes: DespesaPendenteItem[]
  // Despesas aguardando pagamento
  quantidadeDespesasAguardandoPagamento: number
  totalDespesasAguardandoPagamento: number
  // Dias sem fechamento
  quantidadeDiasSemFechamento: number
  diasSemFechamento: string[]
  // Fechamentos abertos
  quantidadeFechamentosAbertos: number
  fechamentosAbertos: FechamentoAbertoItem[]
  // Manutenções
  quantidadeManutencoesAbertas: number
  totalManutencoesAbertas?: number
}

// ==========================================
// Vendedor Management Module (Enhanced)
// ==========================================

export type VendedorTipo = 'INTERNO' | 'PARCEIRO'

// Vendedor with commission summary (for list views)
export interface VendedorResumo {
  id: string
  nome: string
  email?: string
  tipo: VendedorTipo
  ativo: boolean
  diariaBase: number
  totalPendentes: number
  totalAprovadas: number
  totalPagas: number
  qtdLocacoes: number
}

// Bonus status for a seller
export interface BonusStatus {
  elegivel: boolean
  metaAtual: number
  metaNecessaria: number
  valorBonus: number
  vendasFaltando: number
}

// Vendedor with full details including bonus status
export interface VendedorDetalhe extends VendedorResumo {
  tenantId: string
  documento?: string
  telefone?: string
  qtdAcimaPrecoBase: number
  bonusStatus: BonusStatus
  createdAt?: string
  updatedAt?: string
}

// Bulk payment request
export interface PagamentoLoteRequest {
  referenciaPagamento: string
  observacao?: string
}

// Bulk payment response
export interface PagamentoLoteResponse {
  vendedorId: string
  nomeVendedor: string
  qtdComissoesPagas: number
  valorTotalPago: number
  dataHoraPagamento: string
  referenciaPagamento: string
}

// Bonus types
export type StatusBonus = 'PENDENTE' | 'APROVADO' | 'PAGO' | 'CANCELADO'

export interface BonusVendedor {
  id: string
  tenantId: string
  vendedorId: string
  metaAtingida: number
  valorBonus: number
  status: StatusBonus
  aprovadoPor?: string
  aprovadoEm?: string
  pagoPor?: string
  pagoEm?: string
  referenciaPagamento?: string
  createdAt: string
  updatedAt: string
}

// Comissao status types
export type StatusComissao = 'PENDENTE' | 'APROVADA' | 'PAGA' | 'CANCELADA'

export interface Comissao {
  id: string
  tenantId: string
  locacaoId: string
  vendedorId: string
  status: StatusComissao
  dataLocacao: string
  valorTotalLocacao: number
  valorComissionavel: number
  valorComissao: number
  percentualAplicado?: number
  vendaAcimaPrecoBase?: boolean
  aprovadoPor?: string
  aprovadoEm?: string
  pagoPor?: string
  pagoEm?: string
  referenciaPagamento?: string
  createdAt: string
}

// ==========================================
// Tenant Configuration Module
// ==========================================

// Configuração de comissões e bônus do tenant
export interface ComissaoConfig {
  percentualPadrao: number
  percentualAbaixoBase: number
  bonusAtivo: boolean
  bonusMetaVendas: number | null
  bonusValor: number | null
}

// Request para atualizar configuração de comissões
export interface ComissaoConfigRequest {
  percentualPadrao: number
  percentualAbaixoBase: number
  bonusAtivo: boolean
  bonusMetaVendas?: number | null
  bonusValor?: number | null
}

// Dados gerais/e-mail da empresa (tenant)
export interface TenantGeralConfig {
  slug?: string
  cnpj?: string
  razaoSocial?: string
  cidade?: string
  marinhaEmail?: string
  emailRemetente?: string
  smtpHost?: string
  smtpPort?: number
  smtpUsername?: string
  smtpFrom?: string
  smtpStarttls?: boolean
  smtpConfigurado?: boolean
}

export interface TenantGeralConfigRequest {
  razaoSocial?: string
  cidade?: string
  marinhaEmail?: string
  emailRemetente?: string
  smtpHost?: string
  smtpPort?: number
  smtpUsername?: string
  smtpPassword?: string
  smtpFrom?: string
  smtpStarttls?: boolean
}

/** Seções do documento que podem ir (ou não) a um destino na emissão. */
export interface DocumentoConfigDestino {
  residencia: boolean
  saude: boolean
  instrutor: boolean
  termo: boolean
  anexoIdentidade: boolean
  anexoComprovante: boolean
  anexoSelfie: boolean
  comprovanteGru: boolean
}

/** Itens exigidos para liberar o e-mail à Marinha (EMA). */
export interface DocumentoObrigatoriosMarinha {
  identidade: boolean
  selfie: boolean
  saude: boolean
  regras: boolean
  residencia: boolean
  instrutor: boolean
  nacionalidade: boolean
  naturalidade: boolean
}

/** Parametrização de emissão: o que vai para Marinha vs Cliente + obrigatórios. */
export interface DocumentoConfig {
  marinha: DocumentoConfigDestino
  cliente: DocumentoConfigDestino
  obrigatoriosMarinha: DocumentoObrigatoriosMarinha
}

// Reforço jurídico da assinatura eletrônica (página de auditoria + carimbo de tempo).
export interface AssinaturaConfig {
  paginaAuditoria: boolean
  carimboTempo: { ativo: boolean; tsaUrl?: string | null }
  otp: { ativo: boolean; canal: 'EMAIL' | 'WHATSAPP' }
  pades: { cliente: boolean; marinha: boolean }
}

/** White-label do tenant. Nulos ⇒ identidade padrão Meu Jet. Logo vem como data URL. */
export interface Branding {
  corPrimaria?: string | null
  corSecundaria?: string | null
  logoDataUrl?: string | null
}

// ==========================================
// Metering de Emissões (base da cobrança futura)
// ==========================================

/** Uso mensal do tenant. total = documento + gru (prévia não é cobrável). */
export interface EmissaoMensal {
  competencia: string // "YYYY-MM"
  documento: number
  gru: number
  previa: number
  total: number
}

/** Uso de emissões de uma empresa na competência (visão super admin). */
export interface PlatformEmissaoTenant {
  tenantId: string
  slug: string
  razaoSocial: string
  documento: number
  gru: number
  previa: number
  total: number
}

// ==========================================
// Créditos de Emissão (pré-pago)
// ==========================================

export interface SaldoCreditos {
  saldo: number
}

/** Linha do extrato do ledger (append-only). */
export interface CreditoLancamento {
  id: string
  tipo: 'ADESAO' | 'AJUSTE' | 'CONSUMO' | 'ESTORNO'
  quantidade: number
  saldoApos: number
  motivo?: string | null
  createdAt: string
}

/** Saldo de créditos de uma empresa (visão super admin). */
export interface PlatformSaldoTenant {
  tenantId: string
  slug: string
  razaoSocial: string
  saldo: number
}

// ==========================================
// Presença de Vendedores Module (Diárias)
// ==========================================

export type TipoPresenca = 'INTEGRAL' | 'MEIA_DIARIA'

export interface PresencaVendedorRequest {
  vendedorId: string
  tipo: TipoPresenca
  valorAjustado?: number
  motivoAjuste?: string
}

export interface RegistrarPresencasRequest {
  dtReferencia: string
  presencas: PresencaVendedorRequest[]
}

export interface PresencaVendedorResponse {
  id: string
  vendedorId: string
  vendedorNome?: string
  dtReferencia: string
  tipo: TipoPresenca
  valorDiariaBase?: number
  valorDiariaCalculado: number
  valorAjustado?: number
  valorEfetivo: number
  motivoAjuste?: string
  createdAt?: string
}

export interface ResumoDiariasResponse {
  dtReferencia: string
  totalVendedoresPresentes: number
  totalIntegral: number
  totalMeiaDiaria: number
  totalDiarias: number
  detalhes: PresencaVendedorResponse[]
}

// Constants for tipo presença
export const TIPOS_PRESENCA = [
  { value: 'INTEGRAL', label: 'Integral', fator: 1.0 },
  { value: 'MEIA_DIARIA', label: 'Meia Diária', fator: 0.5 },
] as const

// ==========================================
// Pagamento Vendedor Module
// ==========================================

// Payment types
export type TipoPagamento = 'PIX' | 'DINHEIRO'

export const TIPOS_PAGAMENTO = [
  { value: 'PIX', label: 'PIX' },
  { value: 'DINHEIRO', label: 'Dinheiro' },
] as const

// Pending payment summary for a seller
export interface PendenciasPagamento {
  vendedorId: string
  vendedorNome: string
  vendedorEmail?: string
  // PIX Info
  chavePix?: string
  tipoChavePix?: TipoChavePix
  temPixCadastrado: boolean
  // Commissions (approved)
  valorComissoes: number
  qtdComissoes: number
  // Daily Allowances (not paid)
  valorDiarias: number
  qtdDiarias: number
  // Bonus (approved)
  valorBonus: number
  qtdBonus: number
  // Total
  valorTotal: number
  qtdTotal: number
}

// Individual pending item for partial payment
export interface ItemPendente {
  id: string
  tipo: 'COMISSAO' | 'DIARIA' | 'BONUS'
  dataReferencia: string
  descricao: string
  valor: number
}

// Detailed pending items for partial payment
export interface DetalhesPendencias {
  vendedorId: string
  vendedorNome: string
  chavePix?: string
  tipoChavePix?: TipoChavePix
  temPixCadastrado: boolean
  itens: ItemPendente[]
  valorTotal: number
}

// Request to register a payment
export interface RegistrarPagamentoRequest {
  tipoPagamento: TipoPagamento
  referenciaPagamento?: string
  observacoes?: string
  // Optional: for partial payment (if empty, pays all)
  comissaoIds?: string[]
  presencaIds?: string[]
  bonusIds?: string[]
}

// Payment record response
export interface PagamentoVendedor {
  id: string
  tenantId: string
  vendedorId: string
  vendedorNome: string
  // Payment Type
  tipoPagamento: TipoPagamento
  // Values
  valorComissoes: number
  valorDiarias: number
  valorBonus: number
  valorTotal: number
  // PIX Snapshot
  chavePix?: string
  tipoChavePix?: TipoChavePix
  // Payment Reference
  referenciaPagamento?: string
  comprovanteUrl?: string
  // Quantities
  qtdComissoes: number
  qtdDiarias: number
  qtdBonus: number
  // Period
  periodoInicio?: string
  periodoFim?: string
  // Audit
  pagoPor?: string
  observacoes?: string
  createdAt: string
}

// ==========================================
// Despesa Manutencao Module
// ==========================================

export type StatusDespesaManutencao = 'PENDENTE' | 'APROVADA' | 'REJEITADA' | 'PAGA' | 'CANCELADA'

export interface DespesaManutencao extends BaseEntity {
  osManutencaoId: string
  osNumero?: string
  dtVencimento: string
  numeroParcela: number
  totalParcelas: number
  descricaoParcela?: string
  valor: number
  status: StatusDespesaManutencao
  aprovadoPor?: string
  aprovadoEm?: string
  pagoPor?: string
  pagoEm?: string
  referenciaPagamento?: string
  observacoes?: string
  // Dados da OS para contexto
  jetskiNome?: string
  descricaoProblema?: string
  valorTotalOS?: number
}

export interface GerarDespesaManutencaoRequest {
  numeroParcelas: number
  primeiroVencimento: string
  observacoes?: string
}

export interface PagarDespesaManutencaoRequest {
  referenciaPagamento?: string
}

export interface RejeitarDespesaRequest {
  motivo?: string
}

export const STATUS_DESPESA_MANUTENCAO = [
  { value: 'PENDENTE', label: 'Pendente', color: 'yellow' },
  { value: 'APROVADA', label: 'Aprovada', color: 'blue' },
  { value: 'REJEITADA', label: 'Rejeitada', color: 'red' },
  { value: 'PAGA', label: 'Paga', color: 'green' },
  { value: 'CANCELADA', label: 'Cancelada', color: 'gray' },
] as const
