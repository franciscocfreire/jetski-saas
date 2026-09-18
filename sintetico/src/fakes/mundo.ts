/**
 * O "mundo" dos fakes: o que a Marinha e o PagTesouro sintéticos lembram (sessões, GRUs,
 * pagamentos), a configuração de cenário mexida pelo /_controle, a trilha de eventos para
 * asserções e os contadores do /metrics.
 *
 * Tudo em memória, de propósito: o espelho é descartável e um restart do fake equivale a
 * "o site da Marinha perdeu as sessões" — as GRUs pendentes passam a responder como expiradas.
 */
import { randomUUID, randomBytes } from 'node:crypto';
import { Tsa } from './tsa.ts';

export type Etapa = 'marinha' | 'bridge' | 'pagtesouro' | 'tsa';
export const ETAPAS: Etapa[] = ['marinha', 'bridge', 'pagtesouro', 'tsa'];

export interface Falha {
  /** 0 a 1: fração das chamadas da etapa que falham. */
  taxa: number;
  /** `erro` responde a falha típica da etapa; `pendurar` segura a conexão até o cliente desistir. */
  modo: 'erro' | 'pendurar';
}

export interface Config {
  falhas: Record<Etapa, Falha>;
  latenciaMs: Record<Etapa, number>;
  /** Bloqueio por volume observado no site real (~8–10 GRUs/dia por CPF). Desligado por padrão. */
  bloqueioCpf: { ligado: boolean; limite: number };
  /** Se definido, todo PIX vira pago sozinho após N segundos. Padrão: ninguém paga sozinho. */
  autoPagarAposSeg: number | null;
  pixValidadeMin: number;
}

export function configPadrao(): Config {
  return {
    falhas: { marinha: { taxa: 0, modo: 'erro' }, bridge: { taxa: 0, modo: 'erro' }, pagtesouro: { taxa: 0, modo: 'erro' }, tsa: { taxa: 0, modo: 'erro' } },
    latenciaMs: { marinha: 0, bridge: 0, pagtesouro: 0, tsa: 0 },
    bloqueioCpf: { ligado: false, limite: 10 },
    autoPagarAposSeg: null,
    pixValidadeMin: 30,
  };
}

export interface Sessao {
  cookie: string;
  criadaEm: number;
  cascata: Set<string>;
  cpf?: string;
  /** Sessão ASP é de uso único: depois do bridge, reabrir = "sem sessão". */
  consumida: boolean;
}

export interface Gru {
  idGru: string;
  meio: 'PIX' | 'BOLETO';
  cpf: string;
  nome: string;
  criadaEm: number;
  numeroReferencia: string;
  referencia: string;
  valor: number;
  descricao: string;
  idSessao?: string;
  pix?: { conteudo: string; expiraEm: number };
  pagaEm?: number;
  idPagamento?: string;
  arquivoPdf?: string;
}

export interface Evento {
  seq: number;
  em: string;
  tipo: string;
  etapa?: Etapa;
  cpf?: string;
  idGru?: string;
  idSessao?: string;
  detalhe?: string;
}

const VALOR_CHA = 60.32;
const DESCRICAO_CHA = '13508 - CHA EXAME/RENOV/2A VIA/CORRESP./EQUIVAL-TODAS CAT';
const SESSAO_TTL_MS = 20 * 60_000;
const MAX_EVENTOS = 5_000;

export function diaEmSaoPaulo(ms: number): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Sao_Paulo' }).format(new Date(ms));
}

/** "dd/MM/yyyy HH:mm" em America/Sao_Paulo — o formato de `dataExpiracao` do PagTesouro. */
export function dataHoraBr(ms: number): string {
  const p = Object.fromEntries(
    new Intl.DateTimeFormat('pt-BR', { timeZone: 'America/Sao_Paulo', day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit', hourCycle: 'h23' })
      .formatToParts(new Date(ms))
      .map((x) => [x.type, x.value]),
  );
  return `${p.day}/${p.month}/${p.year} ${p.hour}:${p.minute}`;
}

export class Mundo {
  config: Config = configPadrao();
  readonly sessoes = new Map<string, Sessao>();
  readonly grus = new Map<string, Gru>();
  readonly porSessaoPagTesouro = new Map<string, Gru>();
  readonly contribuintes = new Map<string, string | null>();
  readonly eventos: Evento[] = [];
  readonly contadores = new Map<string, number>();
  /** A TSA sintética (RFC 3161): chave e certificado nascem com o mundo e sobrevivem ao reset. */
  readonly tsa = new Tsa();
  // Parte do relógio: um restart do fake não volta a emitir números de GRU já entregues.
  private seqGru = 9_000_000 + (Math.floor(Date.now() / 1000) % 900_000);
  private seqEvento = 0;
  agora: () => number = () => Date.now();
  sorteio: () => number = () => Math.random();

  reset(): void {
    this.config = configPadrao();
    this.sessoes.clear();
    this.grus.clear();
    this.porSessaoPagTesouro.clear();
    this.contribuintes.clear();
    this.eventos.length = 0;
    this.registrar({ tipo: 'RESET' });
  }

  // ---- eventos e métricas ---------------------------------------------------------------

  registrar(e: Omit<Evento, 'seq' | 'em'>): void {
    this.eventos.push({ seq: ++this.seqEvento, em: new Date(this.agora()).toISOString(), ...e });
    if (this.eventos.length > MAX_EVENTOS) this.eventos.splice(0, this.eventos.length - MAX_EVENTOS);
  }

  contar(nome: string, rotulos: Record<string, string>, valor = 1): void {
    const chave = `${nome}{${Object.entries(rotulos).map(([k, v]) => `${k}="${v}"`).join(',')}}`;
    this.contadores.set(chave, (this.contadores.get(chave) ?? 0) + valor);
  }

  metricas(): string {
    const tipos = new Map<string, string[]>();
    for (const [chave, valor] of this.contadores) {
      const nome = chave.slice(0, chave.indexOf('{'));
      if (!tipos.has(nome)) tipos.set(nome, []);
      (tipos.get(nome) as string[]).push(`${chave} ${Number(valor.toFixed(6))}`);
    }
    const linhas: string[] = [];
    for (const [nome, series] of tipos) linhas.push(`# TYPE ${nome} counter`, ...series);
    const situacoes = { PENDENTE: 0, CONCLUIDO: 0, EXPIRADO: 0 };
    for (const g of this.grus.values()) if (g.meio === 'PIX') situacoes[this.situacao(g)]++;
    linhas.push('# TYPE fakes_grus gauge', ...Object.entries(situacoes).map(([s, n]) => `fakes_grus{situacao="${s}"} ${n}`));
    linhas.push('# TYPE fakes_sessoes_marinha gauge', `fakes_sessoes_marinha ${this.sessoes.size}`);
    return linhas.join('\n') + '\n';
  }

  // ---- Marinha ----------------------------------------------------------------------------

  abrirSessao(): Sessao {
    const limite = this.agora() - SESSAO_TTL_MS;
    for (const [k, s] of this.sessoes) if (s.criadaEm < limite) this.sessoes.delete(k);
    const letras = (n: number) => Array.from(randomBytes(n), (b) => String.fromCharCode(65 + (b % 26))).join('');
    const cookie = `ASPSESSIONID${letras(8)}=${letras(24)}`;
    const s: Sessao = { cookie, criadaEm: this.agora(), cascata: new Set(), consumida: false };
    this.sessoes.set(cookie, s);
    return s;
  }

  sessaoDe(cabecalhoCookie: string | undefined): Sessao | undefined {
    for (const par of (cabecalhoCookie ?? '').split(';')) {
      const s = this.sessoes.get(par.trim());
      if (s && !s.consumida) return s;
    }
    return undefined;
  }

  nomeDoContribuinte(cpf: string, padrao: (cpf: string) => string | null): string | null {
    return this.contribuintes.has(cpf) ? (this.contribuintes.get(cpf) as string | null) : padrao(cpf);
  }

  criarGru(meio: Gru['meio'], cpf: string, nome: string): Gru {
    const n = ++this.seqGru;
    const ano = new Date(this.agora()).getFullYear();
    const g: Gru = {
      idGru: String(n),
      meio,
      cpf,
      nome,
      criadaEm: this.agora(),
      numeroReferencia: `6089310${String(n % 1_000_000).padStart(6, '0')}${ano}`,
      referencia: String(80_000_000 + (n % 10_000_000)),
      valor: VALOR_CHA,
      descricao: DESCRICAO_CHA,
    };
    this.grus.set(g.idGru, g);
    return g;
  }

  grusDoCpfHoje(cpf: string): number {
    const hoje = diaEmSaoPaulo(this.agora());
    let n = 0;
    for (const g of this.grus.values()) if (g.cpf === cpf && g.idSessao && diaEmSaoPaulo(g.criadaEm) === hoje) n++;
    return n;
  }

  cpfBloqueado(cpf: string): boolean {
    const b = this.config.bloqueioCpf;
    return b.ligado && this.grusDoCpfHoje(cpf) >= b.limite;
  }

  abrirSessaoPagTesouro(g: Gru): string {
    g.idSessao = randomUUID();
    this.porSessaoPagTesouro.set(g.idSessao, g);
    return g.idSessao;
  }

  // ---- PagTesouro -------------------------------------------------------------------------

  situacao(g: Gru): 'PENDENTE' | 'CONCLUIDO' | 'EXPIRADO' {
    const auto = this.config.autoPagarAposSeg;
    if (!g.pagaEm && auto !== null && g.pix && this.agora() >= g.criadaEm + auto * 1000 && this.agora() < g.pix.expiraEm) {
      this.pagar(g, 'AUTOMATICO');
    }
    if (g.pagaEm) return 'CONCLUIDO';
    if (g.pix && this.agora() >= g.pix.expiraEm) return 'EXPIRADO';
    return 'PENDENTE';
  }

  pagar(g: Gru, origem: string): void {
    if (g.pagaEm) return;
    g.pagaEm = this.agora();
    g.idPagamento = randomUUID().replaceAll('-', '').slice(0, 26);
    this.contar('fakes_pagamentos_total', { origem });
    this.registrar({ tipo: 'PAGAMENTO', etapa: 'pagtesouro', cpf: g.cpf, idGru: g.idGru, idSessao: g.idSessao, detalhe: origem });
  }

  /** Aceita o idSessao do PagTesouro, o id_gru da Marinha ou o número de referência. */
  localizar(chave: string): Gru | undefined {
    return this.porSessaoPagTesouro.get(chave) ?? this.grus.get(chave) ?? [...this.grus.values()].find((g) => g.numeroReferencia === chave);
  }
}
