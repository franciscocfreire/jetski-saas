/**
 * Cliente HTTP do portal (P0): endpoints públicos + customer-scoped.
 * Sem X-Tenant-Id — o escopo /v1/customers/** é multi-loja por construção.
 */

const API_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8090/api";

export interface VinculoLoja {
  tenantId: string;
  clienteId: string;
  slug: string;
  nome: string;
}

export interface CustomerSelf {
  nome: string;
  email: string;
  emailVerified: boolean;
  lojas: VinculoLoja[];
}

export class ApiError extends Error {
  constructor(public status: number, message: string) {
    super(message);
  }
}

async function parseError(res: Response): Promise<never> {
  let message = `Erro ${res.status}`;
  try {
    const body = await res.json();
    message = body.message ?? message;
  } catch {
    // corpo não-JSON — mantém mensagem genérica
  }
  throw new ApiError(res.status, message);
}

/** Cadastro público — cria a identidade global (Keycloak envia o e-mail de verificação). */
export async function signup(nome: string, email: string, senha: string): Promise<void> {
  const res = await fetch(`${API_URL}/v1/public/customers/signup`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ nome, email, senha }),
  });
  if (!res.ok) await parseError(res);
}

export async function getSelf(accessToken: string): Promise<CustomerSelf> {
  const res = await fetch(`${API_URL}/v1/customers/self`, {
    headers: { Authorization: `Bearer ${accessToken}` },
    cache: "no-store",
  });
  if (!res.ok) await parseError(res);
  return res.json();
}

export async function updateSelf(accessToken: string, nome: string): Promise<void> {
  const res = await fetch(`${API_URL}/v1/customers/self`, {
    method: "PUT",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${accessToken}`,
    },
    body: JSON.stringify({ nome }),
  });
  if (!res.ok) await parseError(res);
}
