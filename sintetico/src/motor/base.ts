// Blocos de base do motor de comportamento (fase E4): o acaso com semente, o relógio
// comprimido e as medidas. Nada aqui fala com a rede.

// ---- acaso reproduzível ---------------------------------------------------------------------------

/** Gerador com semente (mulberry32): a mesma semente repete as mesmas decisões de funil. */
export class Acaso {
  private a: number;

  constructor(semente: number) {
    this.a = semente >>> 0;
  }

  proximo(): number {
    this.a = (this.a + 0x6d2b79f5) | 0;
    let t = Math.imul(this.a ^ (this.a >>> 15), 1 | this.a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  }

  chance(p: number): boolean {
    return this.proximo() < p;
  }

  entre(min: number, max: number): number {
    return min + this.proximo() * (max - min);
  }

  inteiro(min: number, max: number): number {
    return Math.floor(this.entre(min, max + 1));
  }

  escolher<T>(itens: readonly T[]): T {
    return itens[Math.floor(this.proximo() * itens.length)];
  }

  /** Sorteio ponderado: `{ "60": 0.55, "30": 0.30 }` → uma das chaves, na proporção dos pesos. */
  ponderado(pesos: Record<string, number>): string {
    const total = Object.values(pesos).reduce((a, b) => a + b, 0);
    let alvo = this.proximo() * total;
    for (const [chave, peso] of Object.entries(pesos)) {
      alvo -= peso;
      if (alvo <= 0) return chave;
    }
    return Object.keys(pesos).at(-1) as string;
  }

  /** Um gerador filho, independente: cada jornada tem o seu, e a ordem de execução não muda o resultado. */
  filho(): Acaso {
    return new Acaso(Math.floor(this.proximo() * 0xffffffff));
  }
}

// ---- relógio comprimido ---------------------------------------------------------------------------

/**
 * O dia simulado corre `fator` vezes mais rápido que o real. Tempo simulado é em MINUTOS DO
 * DIA (540 = 09:00). As datas enviadas à API continuam REAIS — a API exige início no futuro
 * e cobra por tempo de relógio; o que se comprime é o intervalo entre os atos das pessoas.
 */
export class Relogio {
  readonly fator: number;
  private readonly inicioSim: number;
  private readonly inicioReal: number;

  constructor(inicioSim: number, fator: number, agoraReal: number = Date.now()) {
    this.inicioSim = inicioSim;
    this.fator = fator;
    this.inicioReal = agoraReal;
  }

  agora(): number {
    return this.inicioSim + ((Date.now() - this.inicioReal) / 60_000) * this.fator;
  }

  /** Quantos ms reais faltam para o minuto simulado `sim`. */
  msAte(sim: number): number {
    return Math.max(0, ((sim - this.agora()) / this.fator) * 60_000);
  }

  ate(sim: number): Promise<void> {
    return new Promise((ok) => setTimeout(ok, this.msAte(sim)));
  }

  esperar(minutosSim: number): Promise<void> {
    return this.ate(this.agora() + minutosSim);
  }

  /** Instante REAL em que o minuto simulado `sim` vai acontecer. */
  real(sim: number): Date {
    return new Date(Date.now() + this.msAte(sim));
  }

  static hhmm(sim: number): string {
    const m = Math.max(0, Math.round(sim));
    return `${String(Math.floor(m / 60) % 24).padStart(2, '0')}:${String(m % 60).padStart(2, '0')}`;
  }

  hora(): string {
    return Relogio.hhmm(this.agora());
  }
}

/** "09:30" → 570. */
export function minutosDoDia(hhmm: string): number {
  const [h, m] = hhmm.split(':').map(Number);
  return h * 60 + m;
}

/** Distribui `n` chegadas pelas horas do dia conforme a curva (pesos por hora cheia). */
export function sortearChegadas(acaso: Acaso, n: number, curva: Record<string, number>): number[] {
  const chegadas: number[] = [];
  for (let i = 0; i < n; i++) chegadas.push(Number(acaso.ponderado(curva)) * 60 + acaso.entre(0, 60));
  return chegadas.sort((a, b) => a - b);
}

// ---- medidas ---------------------------------------------------------------------------------------

export interface Falha { quando: string; jornada: string; passo: string; erro: string }

export class Medidas {
  readonly desfechos = new Map<string, number>();
  readonly passos = new Map<string, number[]>();
  readonly falhas: Falha[] = [];
  readonly contadores = new Map<string, number>();

  desfecho(jornada: string, resultado: string): void {
    const chave = `${jornada}|${resultado}`;
    this.desfechos.set(chave, (this.desfechos.get(chave) ?? 0) + 1);
  }

  contar(nome: string, n = 1): void {
    this.contadores.set(nome, (this.contadores.get(nome) ?? 0) + n);
  }

  latencia(passo: string, ms: number): void {
    if (!this.passos.has(passo)) this.passos.set(passo, []);
    (this.passos.get(passo) as number[]).push(ms);
  }

  static percentil(valores: number[], p: number): number {
    if (valores.length === 0) return 0;
    const v = [...valores].sort((a, b) => a - b);
    return v[Math.min(v.length - 1, Math.ceil((p / 100) * v.length) - 1)];
  }

  prometheus(): string {
    const l: string[] = ['# TYPE motor_jornadas_total counter'];
    for (const [chave, n] of this.desfechos) {
      const [jornada, resultado] = chave.split('|');
      l.push(`motor_jornadas_total{jornada="${jornada}",desfecho="${resultado}"} ${n}`);
    }
    l.push('# TYPE motor_passo_seconds summary');
    for (const [passo, ms] of this.passos) {
      for (const q of [50, 95]) l.push(`motor_passo_seconds{passo="${passo}",quantile="0.${q}"} ${(Medidas.percentil(ms, q) / 1000).toFixed(3)}`);
      l.push(`motor_passo_seconds_count{passo="${passo}"} ${ms.length}`);
    }
    l.push('# TYPE motor_falhas_total counter', `motor_falhas_total ${this.falhas.length}`);
    for (const [nome, n] of this.contadores) l.push(`# TYPE motor_${nome}_total counter`, `motor_${nome}_total ${n}`);
    return l.join('\n') + '\n';
  }

  relatorio(): { jornadas: Record<string, Record<string, number>>; contadores: Record<string, number>; passos: Record<string, { n: number; p50ms: number; p95ms: number; maxMs: number }>; falhas: Falha[] } {
    const jornadas: Record<string, Record<string, number>> = {};
    for (const [chave, n] of this.desfechos) {
      const [jornada, resultado] = chave.split('|');
      (jornadas[jornada] ??= {})[resultado] = n;
    }
    const passos: Record<string, { n: number; p50ms: number; p95ms: number; maxMs: number }> = {};
    for (const [passo, ms] of [...this.passos].sort()) {
      passos[passo] = { n: ms.length, p50ms: Math.round(Medidas.percentil(ms, 50)), p95ms: Math.round(Medidas.percentil(ms, 95)), maxMs: Math.round(Math.max(...ms)) };
    }
    return { jornadas, contadores: Object.fromEntries(this.contadores), passos, falhas: this.falhas };
  }
}
