import type { CondicaoResumo, FormaCondicao, TipoCondicao } from "./types";

/**
 * Textos da condição comercial. Módulo comum (sem "use client"): a lista de empresas
 * e o detalhe são server components e também descrevem a condição.
 */

export const ROTULO_TIPO: Record<TipoCondicao, string> = {
  PILOTO: "Piloto",
  CORTESIA: "Cortesia",
  PARCERIA: "Parceria",
  NEGOCIADO: "Negociado",
};

export const ROTULO_FORMA: Record<FormaCondicao, string> = {
  ISENCAO: "Isenção total",
  PERCENTUAL: "Desconto (%)",
  VALOR_FIXO: "Valor fixo mensal",
};

const BRL = new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" });

/** "isenta", "−50%" ou "R$ 99,00/mês". */
export function descreverForma(c: Pick<CondicaoResumo, "forma" | "valor">): string {
  if (c.forma === "ISENCAO") return "isenta";
  if (c.forma === "PERCENTUAL") return `−${Number(c.valor)}%`;
  return `${BRL.format(Number(c.valor ?? 0))}/mês`;
}

/** Mesma regra do backend (CondicaoComercialService.valorEfetivo). */
export function valorEfetivo(
  precoPlano: number,
  c: Pick<CondicaoResumo, "forma" | "valor"> | null | undefined,
): number {
  if (!c) return precoPlano;
  if (c.forma === "ISENCAO") return 0;
  if (c.forma === "PERCENTUAL") {
    return Math.round(precoPlano * (100 - Number(c.valor)) ) / 100;
  }
  return Math.min(Number(c.valor ?? 0), precoPlano);
}

/** Hoje (yyyy-MM-dd) no fuso da operação. */
export function hojeIso(): string {
  return new Date().toLocaleDateString("en-CA", { timeZone: "America/Sao_Paulo" });
}
