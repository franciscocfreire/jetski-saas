"use server";

import { revalidatePath } from "next/cache";
import { platformFetch, PlatformApiError } from "./api";
import type {
  AberturaSuporte,
  ResetNivel,
  ResetResult,
  ReencryptResult,
  TenantExport,
} from "./types";

/**
 * Mutações de plataforma. Todas são server actions: o access token nunca chega
 * ao browser e o `revalidatePath` recarrega a árvore de server components.
 *
 * O retorno é sempre `Resultado` — a UI mostra o erro do backend em vez de
 * estourar. 403 aqui quase sempre significa "não é operador de plataforma"
 * (PlatformScopeInterceptor); 400 é regra de negócio (motivo obrigatório, slug
 * de confirmação errado, saldo insuficiente).
 */
export type Resultado<T = void> =
  | { ok: true; dados: T }
  | { ok: false; erro: string; status: number };

async function executar<T>(fn: () => Promise<T>, ...revalidar: string[]): Promise<Resultado<T>> {
  try {
    const dados = await fn();
    for (const p of revalidar) revalidatePath(p, "layout");
    return { ok: true, dados };
  } catch (e) {
    const err = e as PlatformApiError;
    return { ok: false, erro: mensagem(err), status: err.status ?? 0 };
  }
}

/** O backend devolve ErrorResponse JSON; extrai `message` quando houver. */
function mensagem(err: PlatformApiError): string {
  const bruto = err.message ?? "Falha inesperada";
  try {
    const json = JSON.parse(bruto);
    return json.message ?? json.error ?? bruto;
  } catch {
    return bruto;
  }
}

const POST = (path: string, body?: unknown) =>
  platformFetch<unknown>(path, {
    method: "POST",
    ...(body === undefined ? {} : { body: JSON.stringify(body) }),
  });

// ===================== Empresas =====================

export async function aprovarEmpresa(tenantId: string) {
  return executar(() => POST(`/v1/platform/tenants/${tenantId}/approve`), "/empresas");
}

export async function suspenderEmpresa(tenantId: string, motivo: string) {
  return executar(
    () => POST(`/v1/platform/tenants/${tenantId}/suspend`, { motivo: motivo || null }),
    "/empresas",
  );
}

export async function reativarEmpresa(tenantId: string) {
  return executar(() => POST(`/v1/platform/tenants/${tenantId}/reactivate`), "/empresas");
}

export async function habilitarEmissora(tenantId: string) {
  return executar(
    () => POST(`/v1/platform/tenants/${tenantId}/habilitar-emissora`),
    "/empresas",
  );
}

export async function desabilitarEmissora(tenantId: string) {
  return executar(
    () => POST(`/v1/platform/tenants/${tenantId}/desabilitar-emissora`),
    "/empresas",
  );
}

export async function mudarPlano(tenantId: string, planoId: string) {
  return executar(
    () => POST(`/v1/platform/tenants/${tenantId}/plano`, { planoId }),
    "/empresas",
  );
}

// ===================== Zona de perigo =====================

export async function exportarEmpresa(tenantId: string) {
  return executar(
    () => POST(`/v1/platform/tenants/${tenantId}/export`) as Promise<TenantExport>,
    "/empresas",
  );
}

export async function resetarEmpresa(
  tenantId: string,
  nivel: ResetNivel,
  confirmacaoSlug: string,
) {
  return executar(
    () =>
      POST(`/v1/platform/tenants/${tenantId}/reset`, {
        nivel,
        confirmacaoSlug,
      }) as Promise<ResetResult>,
    "/empresas",
  );
}

export async function excluirEmpresa(
  tenantId: string,
  modo: "CARENCIA" | "IMEDIATO",
  confirmacaoSlug: string,
) {
  return executar(
    () => POST(`/v1/platform/tenants/${tenantId}/excluir`, { modo, confirmacaoSlug }),
    "/empresas",
  );
}

export async function cancelarExclusao(tenantId: string) {
  return executar(
    () => POST(`/v1/platform/tenants/${tenantId}/cancelar-exclusao`),
    "/empresas",
  );
}

// ===================== Créditos =====================

export async function lancarCreditos(tenantId: string, quantidade: number, motivo: string) {
  return executar(
    () => POST(`/v1/platform/creditos/${tenantId}`, { quantidade, motivo }),
    "/creditos",
    "/empresas",
  );
}

export async function aprovarCompra(tenantId: string, compraId: string) {
  return executar(
    () => POST(`/v1/platform/creditos/compras/${tenantId}/${compraId}/aprovar`),
    "/creditos",
  );
}

export async function rejeitarCompra(tenantId: string, compraId: string, observacao: string) {
  return executar(
    () =>
      POST(`/v1/platform/creditos/compras/${tenantId}/${compraId}/rejeitar`, {
        observacao,
      }),
    "/creditos",
  );
}

export async function atualizarPrecoCredito(precoUnitario: number) {
  return executar(
    () =>
      platformFetch("/v1/platform/creditos/config", {
        method: "PUT",
        body: JSON.stringify({ precoUnitario }),
      }),
    "/creditos",
  );
}

// ===================== Faturamento =====================

export async function gerarFaturas() {
  return executar(() => POST("/v1/platform/faturas/gerar"), "/faturamento");
}

export async function confirmarFatura(tenantId: string, faturaId: string) {
  return executar(
    () => POST(`/v1/platform/faturas/${tenantId}/${faturaId}/confirmar`),
    "/faturamento",
  );
}

export async function cancelarFatura(tenantId: string, faturaId: string, observacao: string) {
  return executar(
    () => POST(`/v1/platform/faturas/${tenantId}/${faturaId}/cancelar`, { observacao }),
    "/faturamento",
  );
}

// ===================== Catálogo =====================

export async function salvarModulosDoPlano(planoId: string, modulos: string[]) {
  return executar(
    () =>
      platformFetch(`/v1/platform/planos/${planoId}/modulos`, {
        method: "PUT",
        body: JSON.stringify({ modulos }),
      }),
    "/catalogo",
  );
}

export async function salvarCapitania(
  id: string | null,
  req: {
    codigo: string;
    nome: string;
    uf?: string | null;
    emailOficial?: string | null;
    ativa?: boolean | null;
  },
) {
  return executar(
    () =>
      id
        ? platformFetch(`/v1/platform/capitanias/${id}`, {
            method: "PUT",
            body: JSON.stringify(req),
          })
        : POST("/v1/platform/capitanias", req),
    "/catalogo",
  );
}

export async function salvarImagemConfig(tipos: Record<string, { maxDimensao: number; qualidade: number }>) {
  return executar(
    () =>
      platformFetch("/v1/platform/documentos/imagem-config", {
        method: "PUT",
        body: JSON.stringify({ tipos }),
      }),
    "/catalogo",
  );
}

// ===================== Operadores da plataforma =====================

export async function concederAcesso(email: string, papeis: string[]) {
  return executar(
    () => POST("/v1/platform/operadores", { email, papeis }),
    "/operadores",
  );
}

export async function atualizarPapeis(usuarioId: string, papeis: string[]) {
  return executar(
    () =>
      platformFetch(`/v1/platform/operadores/${usuarioId}`, {
        method: "PUT",
        body: JSON.stringify({ papeis }),
      }),
    "/operadores",
  );
}

export async function revogarAcesso(usuarioId: string) {
  return executar(
    () =>
      platformFetch(`/v1/platform/operadores/${usuarioId}`, { method: "DELETE" }),
    "/operadores",
  );
}

// ===================== Sessão de suporte =====================

/**
 * Abre a sessão e devolve o CÓDIGO de handoff.
 *
 * O console vive em admin.* e o backoffice em app.*: cookie não atravessa. O que
 * volta aqui é um código de uso único e vida curta, que o backoffice troca pelo
 * cookie de sessão. O token nunca trafega na URL.
 */
export async function abrirSuporte(
  tenantId: string,
  motivo: string,
  somenteLeitura: boolean,
) {
  return executar(
    () =>
      POST(`/v1/platform/tenants/${tenantId}/suporte`, {
        motivo,
        somenteLeitura,
      }) as Promise<AberturaSuporte>,
    "/empresas",
  );
}

export async function revogarSuporte(sessaoId: string) {
  return executar(
    () => platformFetch(`/v1/platform/suporte/${sessaoId}`, { method: "DELETE" }),
    "/empresas",
    "/auditoria",
  );
}

// ===================== Configurações =====================

export async function reencryptSecrets() {
  return executar(
    () => POST("/v1/platform/secrets/reencrypt") as Promise<ReencryptResult>,
    "/configuracoes",
  );
}

// ===================== Segurança do console =====================

/**
 * Liga/desliga a exigência de 2FA a cada login no console.
 *
 * Revalida `/configuracoes` para a tela refletir o estado que o Keycloak passou a ter —
 * a mudança vale no login seguinte, sem reiniciar nada.
 */
export async function definir2FAConsole(exigeSempre: boolean) {
  return executar(
    () =>
      platformFetch("/v1/platform/seguranca/2fa-console", {
        method: "PUT",
        body: JSON.stringify({ exigeSempre }),
      }),
    "/configuracoes",
  );
}
