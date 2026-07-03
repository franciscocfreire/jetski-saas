/**
 * Cliente HTTP do portal (P0): endpoints públicos + customer-scoped.
 * Sem X-Tenant-Id — o escopo /v1/customers/** é multi-loja por construção.
 */

// Browser: NEXT_PUBLIC_API_URL (relativo "/api" atrás do nginx — sem CORS; ou
// absoluto em dev standalone). Server components (SSR): URL interna do docker
// (API_INTERNAL_URL) — URL relativa não resolve no servidor.
const API_URL =
  typeof window === "undefined"
    ? process.env.API_INTERNAL_URL ??
      process.env.NEXT_PUBLIC_API_URL ??
      "http://localhost:8090/api"
    : process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8090/api";

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

// ===================== Marketplace público (P1) =====================

export interface MarketplaceMidia {
  id: string;
  tipo: string;
  url: string;
  thumbnailUrl?: string;
  ordem: number;
  principal: boolean;
  titulo?: string;
}

export interface MarketplaceModelo {
  id: string;
  tenantId: string;
  lojaSlug: string;
  nome: string;
  fabricante?: string;
  capacidadePessoas: number;
  precoBaseHora: number;
  precoPacote30min?: number;
  fotoReferenciaUrl?: string;
  empresaNome: string;
  empresaWhatsapp?: string;
  localizacao: string;
  prioridade: number;
  midias: MarketplaceMidia[];
}

export function fotoPrincipal(m: MarketplaceModelo): string | undefined {
  const img = m.midias?.find((x) => x.principal && x.tipo !== "VIDEO") ??
    m.midias?.find((x) => x.tipo !== "VIDEO");
  return img?.url ?? m.fotoReferenciaUrl ?? undefined;
}

export async function listModelos(): Promise<MarketplaceModelo[]> {
  const res = await fetch(`${API_URL}/v1/public/marketplace/modelos`, { cache: "no-store" });
  if (!res.ok) await parseError(res);
  return res.json();
}

export async function getModeloPublico(id: string): Promise<MarketplaceModelo> {
  const res = await fetch(`${API_URL}/v1/public/marketplace/modelos/${id}`, { cache: "no-store" });
  if (!res.ok) await parseError(res);
  return res.json();
}

export interface Disponibilidade {
  totalJetskis: number;
  reservasGarantidas: number;
  totalReservas: number;
  maximoReservas: number;
  aceitaComSinal: boolean;
  aceitaSemSinal: boolean;
  vagasGarantidas: number;
  vagasRegulares: number;
}

export async function getDisponibilidade(
  slug: string, modeloId: string, dataInicio: string, dataFimPrevista: string
): Promise<Disponibilidade> {
  const params = new URLSearchParams({ modeloId, dataInicio, dataFimPrevista });
  const res = await fetch(`${API_URL}/v1/public/lojas/${slug}/disponibilidade?${params}`, {
    cache: "no-store",
  });
  if (!res.ok) await parseError(res);
  return res.json();
}

// ===================== Reservas do cliente (P1) =====================

export interface PagamentoReserva {
  tipo?: "SINAL" | "TOTAL";
  status: "AGUARDANDO" | "EM_ANALISE" | "CONFIRMADO" | "RECUSADO";
  motivoRecusa?: string;
  valorTotal: number;
  valorSinal: number;
  valorInformado?: number;
  pixChave?: string;
  pixCopiaEColaSinal?: string;
  pixCopiaEColaTotal?: string;
}

export interface ReservaCliente {
  id: string;
  lojaSlug: string;
  lojaNome: string;
  lojaCnpj?: string;
  modeloId: string;
  modeloNome: string;
  dataInicio: string;
  dataFimPrevista: string;
  status: string;
  observacoes?: string;
  pagamento: PagamentoReserva;
}

export interface ChecklistReserva {
  emailVerified: boolean;
  pagamentoStatus: string;
  pagamentoTipo?: string;
  pagamentoOk: boolean;
  habilitacaoOk: boolean;
  termosOk: boolean;
  garantida: boolean;
  prontaParaCheckin: boolean;
}

function authHeaders(token: string) {
  return { "Content-Type": "application/json", Authorization: `Bearer ${token}` };
}

export async function criarReserva(token: string, req: {
  lojaSlug: string; modeloId: string; dataInicio: string; dataFimPrevista: string;
  observacoes?: string; pagamentoTipo?: "SINAL" | "TOTAL"; cpf?: string; telefone?: string;
}): Promise<ReservaCliente> {
  const res = await fetch(`${API_URL}/v1/customers/reservas`, {
    method: "POST", headers: authHeaders(token), body: JSON.stringify(req),
  });
  if (!res.ok) await parseError(res);
  return res.json();
}

export async function minhasReservas(token: string): Promise<ReservaCliente[]> {
  const res = await fetch(`${API_URL}/v1/customers/reservas`, {
    headers: authHeaders(token), cache: "no-store",
  });
  if (!res.ok) await parseError(res);
  return res.json();
}

export async function getReserva(token: string, id: string): Promise<ReservaCliente> {
  const res = await fetch(`${API_URL}/v1/customers/reservas/${id}`, {
    headers: authHeaders(token), cache: "no-store",
  });
  if (!res.ok) await parseError(res);
  return res.json();
}

export async function getChecklist(token: string, id: string): Promise<ChecklistReserva> {
  const res = await fetch(`${API_URL}/v1/customers/reservas/${id}/checklist`, {
    headers: authHeaders(token), cache: "no-store",
  });
  if (!res.ok) await parseError(res);
  return res.json();
}

export async function anexarComprovante(token: string, id: string, req: {
  tipo: "SINAL" | "TOTAL"; valorInformado: number; contentType: string; dataBase64: string;
}): Promise<ReservaCliente> {
  const res = await fetch(`${API_URL}/v1/customers/reservas/${id}/comprovante`, {
    method: "POST", headers: authHeaders(token), body: JSON.stringify(req),
  });
  if (!res.ok) await parseError(res);
  return res.json();
}

// ===================== Termos / aceite (P2) =====================

export interface OtpStatus {
  ativo: boolean;
  canal?: string;
  verificado: boolean;
}

export interface OtpEnvio {
  ativo: boolean;
  canal?: string;
  destinoMascarado?: string;
  whatsappUrl?: string;
  mensagem?: string;
}

export async function getOtpStatus(token: string, id: string): Promise<OtpStatus> {
  const res = await fetch(`${API_URL}/v1/customers/reservas/${id}/aceite/otp`, {
    headers: authHeaders(token), cache: "no-store",
  });
  if (!res.ok) await parseError(res);
  return res.json();
}

export async function enviarOtp(token: string, id: string): Promise<OtpEnvio> {
  const res = await fetch(`${API_URL}/v1/customers/reservas/${id}/aceite/otp/enviar`, {
    method: "POST", headers: authHeaders(token),
  });
  if (!res.ok) await parseError(res);
  return res.json();
}

export async function verificarOtp(token: string, id: string, codigo: string): Promise<boolean> {
  const res = await fetch(`${API_URL}/v1/customers/reservas/${id}/aceite/otp/verificar`, {
    method: "POST", headers: authHeaders(token), body: JSON.stringify({ codigo }),
  });
  if (!res.ok) await parseError(res);
  return (await res.json()).verificado === true;
}

export async function assinarTermo(token: string, id: string, assinaturaBase64: string): Promise<void> {
  const res = await fetch(`${API_URL}/v1/customers/reservas/${id}/aceite`, {
    method: "POST", headers: authHeaders(token), body: JSON.stringify({ assinaturaBase64 }),
  });
  if (!res.ok) await parseError(res);
}

// ===================== Habilitação — caminho A (P2) =====================

export interface HabilitacaoCliente {
  via?: "CHA" | "EMA";
  chaCategoria?: string;
  chaNumero?: string;
  chaValidade?: string;
  resolvida: boolean;
  temFotoCha: boolean;
}

export async function getHabilitacao(token: string, id: string): Promise<HabilitacaoCliente> {
  const res = await fetch(`${API_URL}/v1/customers/reservas/${id}/habilitacao`, {
    headers: authHeaders(token), cache: "no-store",
  });
  if (!res.ok) await parseError(res);
  return res.json();
}

export async function enviarCha(token: string, id: string, req: {
  categoria?: string; numero: string; validade?: string; fotoBase64?: string;
}): Promise<HabilitacaoCliente> {
  const res = await fetch(`${API_URL}/v1/customers/reservas/${id}/habilitacao/cha`, {
    method: "POST", headers: authHeaders(token), body: JSON.stringify(req),
  });
  if (!res.ok) await parseError(res);
  return res.json();
}
