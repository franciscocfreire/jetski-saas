// Otimizador da fila de embarque.
//
// Problema: cada "party" precisa de N jets ao MESMO tempo por uma duração; os jets
// liberam em horários diferentes; entre um passeio e o próximo há um tempo de
// embarque/desembarque (turnaround). Objetivo: escalonar para usar a frota o quanto
// antes (maximizar utilização/ganho), sem deixar jets ociosos esperando formar grupo.
//
// Tudo em "minutos a partir de agora". Função pura, sem dependências de framework.

export type JetEstado = {
  id: string
  serie: string
  /** Minutos a partir de agora até o jet ficar PRONTO p/ o próximo embarque. 0 = já pronto. */
  prontoEm: number
}

export type Party = {
  id: string
  label: string
  /** Jets simultâneos (tamanho do grupo que anda junto). */
  jets: number
  /** Duração do passeio, em minutos. */
  duracaoMin: number
  /** Ordem de chegada (FIFO): menor = chegou antes. */
  ordem: number
  reservaIds: string[]
}

export type Alocacao = {
  party: Party
  /** Minutos a partir de agora em que a party embarca. */
  inicio: number
  jetIds: string[]
}

function escalonar(
  jets: JetEstado[],
  parties: Party[],
  turnaroundMin: number,
  escolher: (cands: { party: Party; inicio: number }[]) => { party: Party; inicio: number }
): Alocacao[] {
  if (jets.length === 0 || parties.length === 0) return []
  const pronto = jets.map((j) => ({ id: j.id, t: Math.max(0, j.prontoEm) }))
  const pend = [...parties]
  const out: Alocacao[] = []
  let guard = 0
  while (pend.length && guard++ < 4000) {
    const cands = pend
      .filter((p) => p.jets <= pronto.length) // grupo não pode ser maior que a frota
      .map((p) => {
        const ord = pronto
          .map((j, i) => ({ i, t: j.t }))
          .sort((a, b) => a.t - b.t)
          .slice(0, p.jets)
        return { party: p, inicio: Math.max(...ord.map((e) => e.t)), idxs: ord.map((e) => e.i) }
      })
    if (cands.length === 0) break
    const alvo = escolher(cands.map(({ party, inicio }) => ({ party, inicio })))
    const c = cands.find((x) => x.party.id === alvo.party.id)!
    const jetIds: string[] = []
    for (const i of c.idxs) {
      jetIds.push(pronto[i].id)
      pronto[i] = { id: pronto[i].id, t: c.inicio + c.party.duracaoMin + turnaroundMin }
    }
    out.push({ party: c.party, inicio: c.inicio, jetIds })
    pend.splice(pend.indexOf(c.party), 1)
  }
  return out
}

/**
 * Plano OTIMIZADO: a cada passo escolhe a party de menor início possível;
 * desempate por grupo maior (evita ociosidade esperando a dupla), depois FIFO.
 */
export function otimizarFila(jets: JetEstado[], parties: Party[], turnaroundMin: number): Alocacao[] {
  return escalonar(jets, parties, turnaroundMin, (cands) =>
    cands.reduce((best, c) =>
      c.inicio < best.inicio - 1e-9
        ? c
        : Math.abs(c.inicio - best.inicio) < 1e-9 &&
            (c.party.jets > best.party.jets ||
              (c.party.jets === best.party.jets && c.party.ordem < best.party.ordem))
          ? c
          : best
    )
  )
}

/** Plano FIFO (ordem de chegada) — baseline para mostrar o ganho da sugestão. */
export function planoFifo(jets: JetEstado[], parties: Party[], turnaroundMin: number): Alocacao[] {
  return escalonar(jets, parties, turnaroundMin, (cands) =>
    cands.reduce((best, c) => (c.party.ordem < best.party.ordem ? c : best))
  )
}

/** Espera ponderada (jet-min): soma de inicio×jets. Quanto menor, melhor a utilização. */
export function esperaPonderada(plano: Alocacao[]): number {
  return plano.reduce((s, a) => s + a.inicio * a.party.jets, 0)
}
