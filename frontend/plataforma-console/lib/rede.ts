/**
 * Rede de emissão (EMISSAO_DELEGADA_SPEC §8.M): cada delegada logo abaixo da EAMA
 * emissora dela, quando a EAMA também está na lista (com filtro/busca, a delegada
 * cuja EAMA ficou de fora vira raiz e a tabela diz de quem ela é).
 */
export interface EmpresaNaRede {
  id: string;
  razaoSocial: string;
  papelEmissao?: string | null;
  emissoraTenantId?: string | null;
}

export function ordenarPorRede<T extends EmpresaNaRede>(empresas: T[]): { empresa: T; nivel: 0 | 1 }[] {
  const ids = new Set(empresas.map((e) => e.id));
  const filhas = new Map<string, T[]>();
  const raizes: T[] = [];
  for (const e of empresas) {
    if (e.papelEmissao === "DELEGADA" && e.emissoraTenantId && ids.has(e.emissoraTenantId)) {
      const lista = filhas.get(e.emissoraTenantId) ?? [];
      lista.push(e);
      filhas.set(e.emissoraTenantId, lista);
    } else {
      raizes.push(e);
    }
  }
  const resultado: { empresa: T; nivel: 0 | 1 }[] = [];
  for (const raiz of raizes) {
    resultado.push({ empresa: raiz, nivel: 0 });
    const doRaiz = (filhas.get(raiz.id) ?? []).slice().sort((a, b) => a.razaoSocial.localeCompare(b.razaoSocial));
    for (const filha of doRaiz) resultado.push({ empresa: filha, nivel: 1 });
  }
  return resultado;
}
