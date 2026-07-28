"use client";

import { useState, useTransition } from "react";
import { RefreshCw } from "lucide-react";
import { Botao } from "@/components/Acao";
import { recalcularDashboard } from "@/lib/actions";

/**
 * Recálculo manual do read model do dashboard.
 *
 * O snapshot é diário (job das 04:15): troca de plano ou movimento feito
 * durante o dia só aparece no painel no dia seguinte. Este botão recalcula a
 * janela de 7 dias na hora — a página recarrega com os números atualizados.
 */
export function RecalcularDashboard() {
  const [erro, setErro] = useState<string | null>(null);
  const [feito, setFeito] = useState<string | null>(null);
  const [pendente, iniciar] = useTransition();

  return (
    <div className="flex flex-wrap items-center gap-2">
      <Botao
        disabled={pendente}
        onClick={() => {
          setErro(null);
          setFeito(null);
          iniciar(async () => {
            const r = await recalcularDashboard();
            if (!r.ok) setErro(r.erro);
            else
              setFeito(
                `${r.dados.empresas} empresa(s) recalculada(s) em ${r.dados.dias} dia(s).`,
              );
          });
        }}
      >
        <RefreshCw className={`h-4 w-4 ${pendente ? "animate-spin" : ""}`} />
        {pendente ? "Recalculando…" : "Recalcular agora"}
      </Botao>
      {feito && <span className="text-xs text-emerald-700">{feito}</span>}
      {erro && <span className="text-xs text-red-700">{erro}</span>}
    </div>
  );
}
