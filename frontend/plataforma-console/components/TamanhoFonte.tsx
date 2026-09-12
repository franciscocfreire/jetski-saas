"use client";

import { useEffect, useState } from "react";
import { Minus, Plus, Type } from "lucide-react";

/**
 * Ajuste do tamanho do texto — acessibilidade real de quem opera a plataforma
 * no celular, muitas vezes em pé e no sol.
 *
 * Mexe no font-size da RAIZ: como o Tailwind dimensiona em rem, o layout todo
 * acompanha (texto, espaçamentos, alvos de toque), em vez de só o corpo do texto
 * crescer e estourar as caixas.
 *
 * A escolha fica no localStorage e é reaplicada por um script inline no layout,
 * ANTES da primeira pintura: sem isso a página apareceria no tamanho padrão e
 * saltaria para o escolhido — pior que não ter o recurso.
 */
export const CHAVE_FONTE = "mj-console-fonte";
const NIVEIS = [14, 16, 18, 20] as const;
const PADRAO = 16;

export function TamanhoFonte({
  variante = "escuro",
  compacto = false,
}: {
  variante?: "escuro" | "claro";
  /** Só os botões − e +: no header do celular, o indicador espremeria o título. */
  compacto?: boolean;
}) {
  const [px, setPx] = useState<number>(PADRAO);

  useEffect(() => {
    try {
      const salvo = Number(localStorage.getItem(CHAVE_FONTE));
      if ((NIVEIS as readonly number[]).includes(salvo)) setPx(salvo);
    } catch {
      // localStorage bloqueado (janela privada): segue no padrão
    }
  }, []);

  function aplicar(novo: number) {
    setPx(novo);
    document.documentElement.style.fontSize = `${novo}px`;
    try {
      localStorage.setItem(CHAVE_FONTE, String(novo));
    } catch {
      // sem persistência, mas o ajuste vale para esta sessão
    }
  }

  const i = NIVEIS.indexOf(px as (typeof NIVEIS)[number]);
  const posicao = i < 0 ? NIVEIS.indexOf(PADRAO) : i;
  const percentual = Math.round((px / PADRAO) * 100);

  const escuro = variante === "escuro";
  const botao = escuro
    ? "text-brand-200 hover:bg-brand-800 hover:text-white disabled:opacity-40"
    : "text-ink-500 hover:bg-slate-100 hover:text-ink-900 disabled:opacity-40";
  const rotulo = escuro ? "text-brand-300" : "text-ink-300";

  return (
    <div className="flex items-center gap-1.5">
      {!compacto && <Type className={`h-3.5 w-3.5 shrink-0 ${rotulo}`} aria-hidden="true" />}
      <span className="sr-only">Tamanho do texto</span>

      <button
        type="button"
        onClick={() => aplicar(NIVEIS[posicao - 1])}
        disabled={posicao <= 0}
        aria-label="Diminuir o tamanho do texto"
        className={`rounded p-1 transition disabled:cursor-not-allowed ${botao}`}
      >
        <Minus className="h-3.5 w-3.5" />
      </button>

      {!compacto && (
        <button
          type="button"
          onClick={() => aplicar(PADRAO)}
          aria-label={`Tamanho do texto em ${percentual}% — voltar ao padrão`}
          title="Voltar ao padrão"
          className={`min-w-[3.25rem] rounded px-1 py-0.5 text-[10px] tabular-nums transition ${botao}`}
        >
          {percentual}%
        </button>
      )}

      <button
        type="button"
        onClick={() => aplicar(NIVEIS[posicao + 1])}
        disabled={posicao >= NIVEIS.length - 1}
        aria-label="Aumentar o tamanho do texto"
        className={`rounded p-1 transition disabled:cursor-not-allowed ${botao}`}
      >
        <Plus className="h-3.5 w-3.5" />
      </button>
    </div>
  );
}
