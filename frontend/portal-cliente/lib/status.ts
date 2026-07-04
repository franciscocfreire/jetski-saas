/**
 * Dicionário ÚNICO de status → apresentação (D1 do design).
 * Toda tela usa este módulo — nenhuma página mapeia status→cor por conta
 * própria (evita divergência entre telas, como o antigo bug Balcão/Portal).
 */

export type Tone = "slate" | "green" | "amber" | "red" | "brand";

export interface StatusView {
  label: string;
  tone: Tone;
}

/** Estado consolidado de uma reserva aos olhos do CLIENTE. */
export function statusReserva(r: {
  status: string;
  pagamento?: { status?: string };
}): StatusView {
  switch (r.status) {
    case "CANCELADA":
      return { label: "Cancelada", tone: "red" };
    case "EXPIRADA":
      return { label: "Expirada", tone: "slate" };
    case "FINALIZADA":
      return { label: "Concluída", tone: "slate" };
  }
  switch (r.pagamento?.status) {
    case "CONFIRMADO":
      return { label: "Garantida", tone: "green" };
    case "EM_ANALISE":
      return { label: "Pagamento em análise", tone: "amber" };
    case "RECUSADO":
      return { label: "Pagamento recusado", tone: "red" };
    default:
      return { label: "Aguardando pagamento", tone: "amber" };
  }
}

/** Estado de uma locação no histórico. */
export function statusLocacao(l: {
  status: string;
  avaliacaoNota?: number | null;
}): StatusView {
  if (l.status !== "FINALIZADA") return { label: "Em curso", tone: "brand" };
  if (l.avaliacaoNota != null) return { label: `Avaliada (${l.avaliacaoNota}★)`, tone: "green" };
  return { label: "Avalie sua experiência", tone: "amber" };
}
