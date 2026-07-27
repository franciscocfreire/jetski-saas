import { platformFetch } from "./api";
import type {
  DashboardPlataforma,
  RegistroAuditoria,
  Seguranca2FAConsole,
  SaudePlataforma,
  FaturaPendente,
  Operador,
  OperadorAtual,
  RegistroSuporte,
  PapelInfo,
  ImagemCompressaoConfig,
  ModuloCatalogo,
  PlanoInfo,
  PlatformCapitania,
  PlatformCompraCreditos,
  PlatformEmissaoTenant,
  PlatformSaldoTenant,
  ResetNivel,
  TenantExport,
  TenantSummary,
} from "./types";

/**
 * Leituras de `/v1/platform/**`.
 *
 * Nenhuma delas envia `X-Tenant-Id` — o backend dispensa o header nessas rotas
 * desde a F0 e o alvo, quando existe, vai no path. É a diferença central em
 * relação ao `platformService` do backoffice, que precisava carimbar a empresa
 * em toda chamada.
 */
export const platform = {
  tenants: () => platformFetch<TenantSummary[]>("/v1/platform/tenants"),

  /** Read model (F4): um SELECT, sem varrer empresa a empresa. */
  dashboard: (dias = 30) =>
    platformFetch<DashboardPlataforma>(`/v1/platform/dashboard?dias=${dias}`),

  pendingSignups: () =>
    platformFetch<Array<Record<string, unknown>>>("/v1/platform/pending-signups"),

  planos: () => platformFetch<PlanoInfo[]>("/v1/platform/planos"),

  modulos: () => platformFetch<ModuloCatalogo[]>("/v1/platform/modulos"),

  faturasPendentes: () =>
    platformFetch<FaturaPendente[]>("/v1/platform/faturas/pendentes"),

  saldos: () => platformFetch<PlatformSaldoTenant[]>("/v1/platform/creditos"),

  comprasPendentes: () =>
    platformFetch<PlatformCompraCreditos[]>("/v1/platform/creditos/compras"),

  precoCredito: () =>
    platformFetch<{ precoUnitario: number }>("/v1/platform/creditos/config"),

  emissoes: (competencia?: string) =>
    platformFetch<PlatformEmissaoTenant[]>(
      `/v1/platform/metering/emissoes${competencia ? `?competencia=${competencia}` : ""}`,
    ),

  capitanias: () => platformFetch<PlatformCapitania[]>("/v1/platform/capitanias"),

  /** Trilha GLOBAL (tenant_id NULL) — o audit dual grava a mesma ação também na empresa. */
  auditoria: (acao?: string, limite = 100) =>
    platformFetch<RegistroAuditoria[]>(
      `/v1/platform/auditoria?limite=${limite}${acao ? `&acao=${encodeURIComponent(acao)}` : ""}`,
    ),

  acoesAuditoria: () => platformFetch<string[]>("/v1/platform/auditoria/acoes"),

  saude: () => platformFetch<SaudePlataforma>("/v1/platform/saude"),

  seguranca2FAConsole: () =>
    platformFetch<Seguranca2FAConsole>("/v1/platform/seguranca/2fa-console"),

  /** Quem sou eu: papéis do operador logado (o console não tem tenant p/ /v1/user/permissions). */
  me: () => platformFetch<OperadorAtual>("/v1/platform/me"),

  operadores: () => platformFetch<Operador[]>("/v1/platform/operadores"),

  /** Trilha de sessões de suporte (global ou de uma empresa). */
  sessoesSuporte: (tenantId?: string) =>
    platformFetch<RegistroSuporte[]>(
      `/v1/platform/suporte${tenantId ? `?tenantId=${tenantId}` : ""}`,
    ),

  papeisPlataforma: () => platformFetch<PapelInfo[]>("/v1/platform/operadores/papeis"),

  imagemConfig: () =>
    platformFetch<ImagemCompressaoConfig>("/v1/platform/documentos/imagem-config"),

  exports: (tenantId: string) =>
    platformFetch<TenantExport[]>(`/v1/platform/tenants/${tenantId}/exports`),

  /** Dry-run do reset: contagem por tabela do que o nível apagaria. */
  resetPreview: (tenantId: string, nivel: ResetNivel) =>
    platformFetch<Record<string, number>>(
      `/v1/platform/tenants/${tenantId}/reset-preview?nivel=${nivel}`,
    ),
};

/** Competência corrente (YYYY-MM) no fuso da operação. */
export function competenciaAtual(): string {
  const agora = new Date().toLocaleDateString("en-CA", {
    timeZone: "America/Sao_Paulo",
  });
  return agora.slice(0, 7);
}

export const BRL = new Intl.NumberFormat("pt-BR", {
  style: "currency",
  currency: "BRL",
});

export function dataCurta(iso?: string | null): string {
  if (!iso) return "—";
  const d = new Date(iso.length === 10 ? `${iso}T12:00:00` : iso);
  return Number.isNaN(d.getTime())
    ? "—"
    : d.toLocaleDateString("pt-BR", { timeZone: "America/Sao_Paulo" });
}
