// Gancho opcional para a "Praia Sintética" (projeto separado: ~/repos/praia-sintetica) — uma
// visualização estilo jogo em que cada persona é um boneco que faz, em balões, o que está
// fazendo pelas APIs. O motor manda cada passo e cada desfecho por HTTP, em lotes, e
// NUNCA depende da praia: sem PRAIA_URL nada é enviado; praia fora do ar, o motor segue.
//
// Formato: o `EventoMotor` de shared/mapeamento.ts do projeto da praia — quem (id, nome,
// papel cru do motor, empresa), jornada (`portal#17`), nome do passo, resultado e latência.

export interface Quem { id: string; nome: string; papel: string; empresa?: string }

export interface EventoPraia {
  tipo: 'passo' | 'desfecho' | 'nota';
  ts: string;
  rodada: string;
  jornada: string;
  passo?: string;
  desfecho?: string;
  quem: Quem;
  loja?: string;
  resultado?: 'OK' | 'FALHOU';
  ms?: number;
  erro?: string;
  detalhes?: Record<string, unknown>;
}

export type Enviar = (lote: EventoPraia[]) => Promise<void>;

export class Praia {
  readonly url?: string;
  private readonly fila: EventoPraia[] = [];
  private timer?: NodeJS.Timeout;
  private falhas = 0;
  private readonly intervaloMs: number;
  private readonly enviar: Enviar;
  /** Quantos eventos já foram aceitos (relatório e testes). */
  enviados = 0;

  constructor(url: string | undefined, opcoes: { intervaloMs?: number; token?: string; enviar?: Enviar } = {}) {
    this.url = url?.replace(/\/+$/, '') || undefined;
    this.intervaloMs = opcoes.intervaloMs ?? 300;
    this.enviar =
      opcoes.enviar ??
      (async (lote) => {
        const r = await fetch(`${this.url}/api/eventos`, {
          method: 'POST',
          headers: { 'content-type': 'application/json', ...(opcoes.token ? { authorization: `Bearer ${opcoes.token}` } : {}) },
          body: JSON.stringify(lote),
          signal: AbortSignal.timeout(3000),
        });
        if (r.status >= 300) throw new Error(`HTTP ${r.status}`);
      });
  }

  get ligada(): boolean {
    return Boolean(this.url);
  }

  emitir(ev: Omit<EventoPraia, 'ts'> & { ts?: string }): void {
    if (!this.url) return;
    this.fila.push({ ts: new Date().toISOString(), ...ev });
    if (!this.timer) this.timer = setTimeout(() => void this.despachar(), this.intervaloMs);
  }

  private async despachar(): Promise<void> {
    this.timer = undefined;
    const lote = this.fila.splice(0, 500);
    if (lote.length === 0) return;
    try {
      await this.enviar(lote);
      this.enviados += lote.length;
      this.falhas = 0;
    } catch (e) {
      // Só avisa na primeira falha de uma sequência: a praia é acessório e não pode poluir o log do motor.
      if (this.falhas++ === 0) console.warn(`[praia] não alcancei ${this.url} (${e instanceof Error ? e.message : e}) — o motor segue sem a praia.`);
    }
    if (this.fila.length > 0) this.timer = setTimeout(() => void this.despachar(), this.intervaloMs);
  }

  /** Esvazia a fila antes de o processo terminar (desiste após 3 falhas seguidas). */
  async encerrar(): Promise<void> {
    if (this.timer) clearTimeout(this.timer);
    this.timer = undefined;
    while (this.fila.length > 0 && this.falhas < 3) await this.despachar();
  }
}

/** Nome apresentável a partir do e-mail sintético: `gerente.delegada-atol@…` → "gerente delegada-atol". */
export function nomePeloEmail(email: string): string {
  return email.split('@')[0].replace(/[._]+/g, ' ');
}
