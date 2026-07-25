"use client";

import { useState, useTransition } from "react";
import { Acao, AcaoComTexto, Botao } from "@/components/Acao";
import { aprovarCompra, atualizarPrecoCredito, rejeitarCompra } from "@/lib/actions";
import { BRL } from "@/lib/platform";

export function AprovarCompra({
  tenantId,
  compraId,
}: {
  tenantId: string;
  compraId: string;
}) {
  return (
    <div className="flex flex-wrap items-start gap-2">
      <Acao
        variante="primaria"
        rotulo="Aprovar"
        confirmar="PIX conferido no extrato?"
        acao={() => aprovarCompra(tenantId, compraId)}
      />
      <AcaoComTexto
        variante="perigo"
        rotulo="Rejeitar"
        placeholder="motivo da rejeição"
        acao={(obs) => rejeitarCompra(tenantId, compraId, obs)}
      />
    </div>
  );
}

export function PrecoCredito({ atual }: { atual: number }) {
  const [valor, setValor] = useState(String(atual));
  const [erro, setErro] = useState<string | null>(null);
  const [ok, setOk] = useState(false);
  const [pendente, iniciar] = useTransition();

  const numero = Number(valor.replace(",", "."));
  const valido = Number.isFinite(numero) && numero > 0 && numero !== atual;

  return (
    <div>
      <div className="flex flex-wrap items-center gap-2">
        <span className="text-sm text-ink-500">Atual: {BRL.format(atual)}</span>
        <input
          value={valor}
          onChange={(e) => setValor(e.target.value)}
          className="w-28 rounded-md border border-slate-300 px-2 py-1.5 text-sm"
        />
        <Botao
          variante="primaria"
          disabled={!valido || pendente}
          onClick={() => {
            setErro(null);
            setOk(false);
            iniciar(async () => {
              const r = await atualizarPrecoCredito(numero);
              if (!r.ok) setErro(r.erro);
              else setOk(true);
            });
          }}
        >
          {pendente ? "…" : "Salvar preço"}
        </Botao>
      </div>
      {erro && <p className="mt-1 text-xs text-red-700">{erro}</p>}
      {ok && <p className="mt-1 text-xs text-emerald-700">Preço atualizado.</p>}
    </div>
  );
}
