"use client";

import { useState } from "react";
import { Acao } from "@/components/Acao";
import { reencryptSecrets } from "@/lib/actions";
import type { ReencryptResult } from "@/lib/types";
import type { Resultado } from "@/lib/actions";

export function RotacaoDeChave() {
  const [resultado, setResultado] = useState<ReencryptResult | null>(null);

  return (
    <div>
      <Acao
        variante="primaria"
        rotulo="Re-cifrar segredos"
        confirmar="Re-cifrar os segredos de todas as empresas?"
        acao={() => reencryptSecrets()}
        aoConcluir={(r: Resultado<ReencryptResult>) => {
          if (r.ok) setResultado(r.dados);
        }}
      />

      {resultado && (
        <dl className="mt-4 grid grid-cols-2 gap-3 text-sm sm:grid-cols-4">
          <Item rotulo="Com segredo" valor={resultado.comSegredo} />
          <Item rotulo="Recifrados" valor={resultado.recifrados} />
          <Item rotulo="Falhas" valor={resultado.falhas} destaque={resultado.falhas > 0} />
          <Item
            rotulo="Criptografia"
            valor={resultado.criptografiaAtiva ? "ativa" : "inativa"}
            destaque={!resultado.criptografiaAtiva}
          />
        </dl>
      )}
    </div>
  );
}

function Item({
  rotulo,
  valor,
  destaque,
}: {
  rotulo: string;
  valor: number | string;
  destaque?: boolean;
}) {
  return (
    <div className="rounded-md border border-slate-200 p-3">
      <dt className="text-xs uppercase tracking-wide text-ink-300">{rotulo}</dt>
      <dd className={destaque ? "mt-0.5 font-medium text-red-700" : "mt-0.5 text-ink-900"}>
        {valor}
      </dd>
    </div>
  );
}
