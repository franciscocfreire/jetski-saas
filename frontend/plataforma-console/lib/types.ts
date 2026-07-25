/** DTOs de `/v1/platform/**`. Espelham os records do backend (módulos tenant/creditos/metering). */

export interface TenantSummary {
  id: string;
  slug: string;
  razaoSocial: string;
  status: string;
  roles: string[];
  /** Plano da assinatura ativa; null sem assinatura. */
  plano?: string | null;
  /** Fim da assinatura ativa (yyyy-MM-dd) — no trial, quando os 14 dias vencem. */
  assinaturaFim?: string | null;
  /** Expurgo agendado (ISO); ausente = sem exclusão pendente. */
  exclusaoAgendadaEm?: string | null;
  /** Chaves do enum ModuloPlano; null/ausente = todos. */
  modulos?: string[] | null;
  emissoraHabilitada?: boolean;
  eamaRegistro?: string | null;
}

export interface PlanoInfo {
  id: string;
  nome: string;
  precoMensal: number;
  limites: string;
  /** JSON array de chaves de módulos (texto) ou null = todos. */
  modulos?: string | null;
}

export interface ModuloCatalogo {
  key: string;
  rotulo: string;
  descricao: string;
}

export interface FaturaPendente {
  fatura: {
    id: string;
    competencia: string;
    planoNome: string;
    valor: number;
    txidInformado?: string;
    vencimento: string;
  };
  tenantId: string;
  slug: string;
  razaoSocial: string;
}

export interface PlatformSaldoTenant {
  tenantId: string;
  slug: string;
  razaoSocial: string;
  saldo: number;
}

export interface PlatformCompraCreditos {
  id: string;
  tenantId: string;
  slug: string;
  razaoSocial: string;
  quantidade: number;
  valorPago?: number | null;
  precoUnitario?: number | null;
  /** Legado: número da transação digitado (antes do upload de comprovante). */
  pixTxid?: string | null;
  temComprovante: boolean;
  createdAt: string;
}

export interface PlatformEmissaoTenant {
  tenantId: string;
  slug: string;
  razaoSocial: string;
  documento: number;
  gru: number;
  previa: number;
  total: number;
}

export interface PlatformCapitania {
  id: string;
  codigo: string;
  nome: string;
  uf: string | null;
  emailOficial: string | null;
  ativa: boolean;
}

export interface TenantExport {
  key: string;
  bytes: number;
  tabelas: number;
  arquivos: number;
}

/** Níveis do reset — cada um é superconjunto do anterior. */
export type ResetNivel = "OPERACIONAL" | "FROTA" | "TOTAL";

export interface ResetResult {
  nivel: ResetNivel;
  apagados: Record<string, number>;
  totalLinhas: number;
}

export interface ReencryptResult {
  comSegredo: number;
  recifrados: number;
  falhas: number;
  criptografiaAtiva: boolean;
}

/** Papéis de plataforma (F2) — espelham o enum PapelPlataforma do backend. */
export type PapelPlataforma =
  | "PLATFORM_ADMIN"
  | "PLATFORM_SUPORTE"
  | "PLATFORM_FINANCEIRO"
  | "PLATFORM_LEITURA";

export interface PapelInfo {
  key: PapelPlataforma;
  rotulo: string;
  descricao: string;
}

export interface OperadorAtual {
  usuarioId: string | null;
  papeis: PapelPlataforma[];
  admin: boolean;
}

export interface RegistroAuditoria {
  id: string;
  acao: string;
  entidade: string | null;
  entidade_id: string | null;
  usuario_id: string | null;
  usuario_email: string | null;
  dados_anteriores: unknown;
  dados_novos: unknown;
  ip: string | null;
  created_at: string;
}

export interface SaudePlataforma {
  statusGeral: string;
  infra: Record<string, string>;
  operacao: Record<string, Record<string, unknown>>;
}

export interface DashboardPlataforma {
  dias: number;
  /** Quando o read model foi calculado; null = o job nunca rodou. */
  atualizadoEm: string | null;
  totais: Record<string, number>;
  serie: Array<{ dia: string; locacoes: number; receita_bruta: number; emissoes: number }>;
  topEmpresas: Array<{
    id: string; slug: string; razao_social: string;
    locacoes: number; receita_bruta: number; emissoes: number;
  }>;
}

export interface AberturaSuporte {
  sessaoId: string;
  codigo: string;
  expiraEm: string;
}

export interface RegistroSuporte {
  id: string;
  tenantId: string;
  tenantSlug: string;
  operadorId: string | null;
  operadorEmail: string | null;
  motivo: string;
  somenteLeitura: boolean;
  iniciadaEm: string;
  expiraEm: string;
  encerradaEm: string | null;
  ativa: boolean;
}

export interface Operador {
  usuarioId: string;
  email: string;
  nome: string;
  ativo: boolean;
  papeis: PapelPlataforma[];
  concedidoEm: string | null;
}

/** Preset de compressão por tipo de documento (IDENTIDADE, SELFIE, CHA, ...). */
export interface ImagemPreset {
  maxDimensao: number;
  qualidade: number;
}

export interface ImagemCompressaoConfig {
  tipos: Record<string, ImagemPreset>;
}
