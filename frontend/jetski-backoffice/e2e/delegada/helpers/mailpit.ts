import { request, type APIRequestContext } from '@playwright/test';
import { MAILPIT_URL } from './ambiente';

/**
 * Leitura do Mailpit pela API (v1). A caixa é COMPARTILHADA com quem usa o dev e com o
 * wizard.mjs: nunca limpar. O isolamento vem dos endereços únicos de cada execução.
 */

export interface Endereco {
  Name: string;
  Address: string;
}

export interface MensagemResumo {
  ID: string;
  From: Endereco;
  To: Endereco[];
  Cc?: Endereco[];
  ReplyTo?: Endereco[];
  Subject: string;
  Date?: string;
  Created?: string;
  Attachments?: number;
}

export interface Anexo {
  FileName: string;
  ContentType: string;
  Size: number;
}

export interface Mensagem extends MensagemResumo {
  ReplyTo: Endereco[];
  HTML: string;
  Text: string;
  Attachments: Anexo[] & number;
}

export interface FiltroMensagem {
  /** Destinatário exato. */
  para: string;
  /** Trecho do assunto (opcional). */
  assunto?: string;
  /** Quantas mensagens devem existir para o filtro (default 1). */
  quantidade?: number;
  timeoutMs?: number;
}

let contexto: APIRequestContext | null = null;

async function api(): Promise<APIRequestContext> {
  if (!contexto) contexto = await request.newContext({ baseURL: MAILPIT_URL });
  return contexto;
}

export async function descartarContextoMailpit(): Promise<void> {
  await contexto?.dispose();
  contexto = null;
}

function consulta({ para, assunto }: FiltroMensagem): string {
  const partes = [`to:"${para}"`];
  if (assunto) partes.push(`subject:"${assunto.replace(/"/g, '')}"`);
  return partes.join(' ');
}

const quando = (m: MensagemResumo) => Date.parse(m.Date ?? m.Created ?? '') || 0;

/** Busca sem esperar; mais recentes primeiro. */
export async function buscar(filtro: FiltroMensagem): Promise<MensagemResumo[]> {
  const ctx = await api();
  const res = await ctx.get('/api/v1/search', {
    params: { query: consulta(filtro), limit: '50' },
  });
  if (!res.ok()) throw new Error(`Mailpit search ${res.status()}: ${await res.text()}`);
  const corpo = (await res.json()) as { messages?: MensagemResumo[] };
  // O search casa por substring no assunto; o destinatário é conferido de novo aqui.
  return (corpo.messages ?? [])
    .filter((m) => m.To.some((t) => t.Address.toLowerCase() === filtro.para.toLowerCase()))
    .sort((a, b) => quando(b) - quando(a));
}

export async function lerMensagem(id: string): Promise<Mensagem> {
  const ctx = await api();
  const res = await ctx.get(`/api/v1/message/${id}`);
  if (!res.ok()) throw new Error(`Mailpit message ${id} ${res.status()}: ${await res.text()}`);
  return (await res.json()) as Mensagem;
}

/**
 * Espera até existirem `quantidade` mensagens para o filtro e devolve as completas,
 * mais recentes primeiro. O envio da emissão é assíncrono e passa por dois SMTPs.
 */
export async function esperarMensagens(filtro: FiltroMensagem): Promise<Mensagem[]> {
  const quantidade = filtro.quantidade ?? 1;
  const limite = Date.now() + (filtro.timeoutMs ?? 90_000);
  let ultimas: MensagemResumo[] = [];
  while (Date.now() < limite) {
    ultimas = await buscar(filtro);
    if (ultimas.length >= quantidade) {
      return Promise.all(ultimas.slice(0, quantidade).map((m) => lerMensagem(m.ID)));
    }
    await new Promise((r) => setTimeout(r, 1_500));
  }
  throw new Error(
    `Mailpit: esperava ${quantidade} mensagem(ns) para ${consulta(filtro)}, ` +
      `achei ${ultimas.length}: ${ultimas.map((m) => m.Subject).join(' | ') || '—'}`,
  );
}

export async function esperarMensagem(filtro: FiltroMensagem): Promise<Mensagem> {
  const [m] = await esperarMensagens({ ...filtro, quantidade: 1 });
  return m;
}

/** Nomes dos anexos, qualquer que seja a forma que a API devolver. */
export function nomesDosAnexos(m: Mensagem): string[] {
  const a = (m as unknown as { Attachments?: Anexo[] }).Attachments;
  return Array.isArray(a) ? a.map((x) => x.FileName) : [];
}
