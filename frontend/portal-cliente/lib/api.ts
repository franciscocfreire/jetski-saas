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
  /** Contato é POR LOJA (como endereço). */
  telefone?: string;
  whatsapp?: string;
}

export interface IdentidadeCliente {
  cpf?: string;
  rg?: string;
  orgaoEmissor?: string;
  nacionalidade?: string;
  naturalidade?: string;
  estrangeiro?: boolean;
  dataNascimento?: string;
}

export interface CustomerSelf {
  nome: string;
  email: string;
  emailVerified: boolean;
  identidade?: IdentidadeCliente;
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

export async function updateSelf(
  accessToken: string, nome: string, identidade?: IdentidadeCliente
): Promise<void> {
  const res = await fetch(`${API_URL}/v1/customers/self`, {
    method: "PUT",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${accessToken}`,
    },
    body: JSON.stringify({ nome, ...(identidade ?? {}) }),
  });
  if (!res.ok) await parseError(res);
}

/** Atualiza telefone/WhatsApp do cliente NUMA loja (contato é por loja). */
export async function updateContatoLoja(
  accessToken: string, tenantId: string, telefone: string
): Promise<VinculoLoja> {
  const res = await fetch(`${API_URL}/v1/customers/self/lojas/${tenantId}/contato`, {
    method: "PUT",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${accessToken}`,
    },
    body: JSON.stringify({ telefone, whatsapp: telefone }),
  });
  if (!res.ok) await parseError(res);
  return res.json();
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
  notaMedia?: number;
  totalAvaliacoes?: number;
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

// ===================== CHA-MTA-E / EMA (P3) =====================

export interface EnderecoCliente {
  cep?: string; logradouro?: string; numero?: string;
  complemento?: string; bairro?: string; cidade?: string; uf?: string;
}

export interface DadosPessoais {
  nome?: string; cpf?: string; rg?: string; orgaoEmissor?: string;
  nacionalidade?: string; naturalidade?: string; estrangeiro?: boolean;
  dataNascimento?: string; telefone?: string; whatsapp?: string;
  endereco?: EnderecoCliente | null; completos: boolean;
}

export interface GruEstado {
  numero?: string; valor?: number; pago: boolean;
  pixCopiaECola?: string; pixExpiracao?: string;
  boletoDisponivel: boolean; comprovanteDisponivel: boolean;
  sucesso: boolean; reaproveitada: boolean; erroMensagem?: string;
}

export interface EmaEstado {
  dadosPessoaisCompletos: boolean;
  anexosPresentes: string[];
  videoaulaAssistida: boolean;
  anexoSaude: boolean; anexoRegras: boolean; anexoResidencia: boolean;
  usaLentes: boolean; usaAparelho: boolean;
  gru: GruEstado; resolvida: boolean;
}

const emaBase = (id: string) => `${API_URL}/v1/customers/reservas/${id}/ema`;

export async function getEmaEstado(token: string, id: string): Promise<EmaEstado> {
  const res = await fetch(emaBase(id), { headers: authHeaders(token), cache: "no-store" });
  if (!res.ok) await parseError(res);
  return res.json();
}

export async function getDadosPessoais(token: string, id: string): Promise<DadosPessoais> {
  const res = await fetch(`${emaBase(id)}/dados`, { headers: authHeaders(token), cache: "no-store" });
  if (!res.ok) await parseError(res);
  return res.json();
}

export async function putDadosPessoais(
  token: string, id: string, dados: Omit<DadosPessoais, "completos" | "nome">
): Promise<DadosPessoais> {
  const res = await fetch(`${emaBase(id)}/dados`, {
    method: "PUT", headers: authHeaders(token), body: JSON.stringify(dados),
  });
  if (!res.ok) await parseError(res);
  return res.json();
}

export async function uploadAnexoEma(
  token: string, id: string,
  tipo: "IDENTIDADE" | "SELFIE" | "COMPROVANTE_RESIDENCIA", conteudoBase64: string
): Promise<string[]> {
  const res = await fetch(`${emaBase(id)}/anexos`, {
    method: "POST", headers: authHeaders(token),
    body: JSON.stringify({ tipo, conteudoBase64 }),
  });
  if (!res.ok) await parseError(res);
  return res.json();
}

export async function putEmaFlags(token: string, id: string, flags: {
  videoaulaAssistida?: boolean; anexoSaude?: boolean; anexoRegras?: boolean;
  anexoResidencia?: boolean; usaLentes?: boolean; usaAparelho?: boolean;
}): Promise<EmaEstado> {
  const res = await fetch(emaBase(id), {
    method: "PUT", headers: authHeaders(token), body: JSON.stringify(flags),
  });
  if (!res.ok) await parseError(res);
  return res.json();
}

export async function gerarGruPix(token: string, id: string): Promise<GruEstado> {
  const res = await fetch(`${emaBase(id)}/gru/pix`, { method: "POST", headers: authHeaders(token) });
  if (!res.ok) await parseError(res);
  return res.json();
}

export async function gerarGruBoleto(token: string, id: string): Promise<GruEstado> {
  const res = await fetch(`${emaBase(id)}/gru/boleto`, { method: "POST", headers: authHeaders(token) });
  if (!res.ok) await parseError(res);
  return res.json();
}

export async function verificarGru(token: string, id: string):
    Promise<{ pago: boolean; situacao: string; comprovanteDisponivel: boolean }> {
  const res = await fetch(`${emaBase(id)}/gru/verificar`, { method: "POST", headers: authHeaders(token) });
  if (!res.ok) await parseError(res);
  return res.json();
}

export async function enviarComprovanteGru(token: string, id: string, conteudoBase64: string): Promise<EmaEstado> {
  const res = await fetch(`${emaBase(id)}/gru/comprovante`, {
    method: "POST", headers: authHeaders(token), body: JSON.stringify({ conteudoBase64 }),
  });
  if (!res.ok) await parseError(res);
  return res.json();
}

export async function baixarBoletoGru(token: string, id: string): Promise<Blob> {
  const res = await fetch(`${emaBase(id)}/gru/boleto/download`, { headers: authHeaders(token) });
  if (!res.ok) await parseError(res);
  return res.blob();
}

// ===================== Locações / avaliações (P4) =====================

export interface LocacaoCliente {
  id: string;
  lojaSlug: string;
  lojaNome: string;
  modeloNome?: string;
  jetskiSerie?: string;
  dataCheckIn?: string;
  dataCheckOut?: string;
  minutosUsados?: number;
  minutosFaturaveis?: number;
  valorBase?: number;
  valorTotal?: number;
  status: string;
  avaliacaoNota?: number;
  avaliacaoComentario?: string;
}

export interface FotoLocacao {
  id: string;
  tipo: string;
  downloadUrl?: string;
}

export interface LocacaoClienteDetalhe {
  locacao: LocacaoCliente;
  fotos: FotoLocacao[];
}

export async function minhasLocacoes(token: string): Promise<LocacaoCliente[]> {
  const res = await fetch(`${API_URL}/v1/customers/locacoes`, {
    headers: authHeaders(token), cache: "no-store",
  });
  if (!res.ok) await parseError(res);
  return res.json();
}

export async function getLocacao(token: string, id: string): Promise<LocacaoClienteDetalhe> {
  const res = await fetch(`${API_URL}/v1/customers/locacoes/${id}`, {
    headers: authHeaders(token), cache: "no-store",
  });
  if (!res.ok) await parseError(res);
  return res.json();
}

export async function avaliarLocacao(
  token: string, id: string, nota: number, comentario?: string
): Promise<LocacaoCliente> {
  const res = await fetch(`${API_URL}/v1/customers/locacoes/${id}/avaliacao`, {
    method: "POST", headers: authHeaders(token),
    body: JSON.stringify({ nota, comentario: comentario || undefined }),
  });
  if (!res.ok) await parseError(res);
  return res.json();
}

// ===================== Branding da loja (P4) =====================

export interface BrandingLoja {
  corPrimaria?: string;
  corSecundaria?: string;
  logoDataUrl?: string;
}

export async function getBrandingLoja(slug: string): Promise<BrandingLoja | null> {
  try {
    const res = await fetch(`${API_URL}/v1/public/lojas/${slug}/branding`, { cache: "no-store" });
    if (!res.ok) return null;
    return res.json();
  } catch {
    return null;
  }
}

// ===================== Recibo + Notificações (backlog P4) =====================

export async function baixarRecibo(token: string, locacaoId: string): Promise<Blob> {
  const res = await fetch(`${API_URL}/v1/customers/locacoes/${locacaoId}/recibo`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!res.ok) await parseError(res);
  return res.blob();
}

export interface NotificacaoCliente {
  id: string;
  lojaSlug: string;
  tipo: string;
  titulo: string;
  mensagem?: string;
  link?: string;
  lida: boolean;
  criadaEm: string;
}

export interface CaixaNotificacoes {
  naoLidas: number;
  itens: NotificacaoCliente[];
}

export async function getNotificacoes(token: string): Promise<CaixaNotificacoes> {
  const res = await fetch(`${API_URL}/v1/customers/notificacoes`, {
    headers: authHeaders(token), cache: "no-store",
  });
  if (!res.ok) await parseError(res);
  return res.json();
}

export async function marcarNotificacaoLida(token: string, id: string): Promise<void> {
  const res = await fetch(`${API_URL}/v1/customers/notificacoes/${id}/lida`, {
    method: "POST", headers: authHeaders(token),
  });
  if (!res.ok) await parseError(res);
}

export async function marcarTodasLidas(token: string): Promise<void> {
  const res = await fetch(`${API_URL}/v1/customers/notificacoes/lidas`, {
    method: "POST", headers: authHeaders(token),
  });
  if (!res.ok) await parseError(res);
}
