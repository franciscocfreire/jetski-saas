import { auth } from "./auth";

/**
 * Cliente HTTP do console.
 *
 * Diferença central para o backoffice: NÃO existe `X-Tenant-Id`. O console não
 * tem "empresa corrente" — o alvo, quando existe, vai no path
 * (`/v1/platform/tenants/{id}/...`). O backend aceita /v1/platform/** sem o
 * header desde a F0.
 *
 * Server-side (server components / route handlers) chama o backend pela rede
 * interna do Docker; no browser, o caminho relativo /api passa pelo nginx do
 * mesmo host — sem CORS.
 */
const INTERNAL = process.env.API_INTERNAL_URL ?? "http://backend:8090/api";

export class PlatformApiError extends Error {
  constructor(readonly status: number, message: string) {
    super(message);
    this.name = "PlatformApiError";
  }
}

export async function platformFetch<T>(
  path: string,
  init: RequestInit = {},
): Promise<T> {
  const session = await auth();
  if (!session?.accessToken) {
    throw new PlatformApiError(401, "Sessão expirada");
  }

  const response = await fetch(`${INTERNAL}${path}`, {
    ...init,
    headers: {
      "Content-Type": "application/json",
      ...(init.headers ?? {}),
      Authorization: `Bearer ${session.accessToken}`,
    },
    cache: "no-store",
  });

  if (!response.ok) {
    // 403 aqui normalmente significa "não é operador de plataforma"
    // (PlatformScopeInterceptor), não um problema de rota.
    const detalhe = await response.text().catch(() => "");
    throw new PlatformApiError(
      response.status,
      detalhe || `${response.status} em ${path}`,
    );
  }

  if (response.status === 204) {
    return undefined as T;
  }
  return response.json() as Promise<T>;
}
