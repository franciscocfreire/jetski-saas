"use client";

import { useState, useTransition } from "react";
import { Badge } from "@/components/ui";
import { definir2FAConsole } from "@/lib/actions";
import type { Seguranca2FAConsole } from "@/lib/types";

/**
 * Liga/desliga o 2FA a cada login no console.
 *
 * Não é um "salvar" de formulário: a mudança vale no próximo login, sem reiniciar o
 * Keycloak. Por isso o estado local só avança depois que o backend confirma.
 */
export function Exigencia2FA({ inicial }: { inicial: Seguranca2FAConsole }) {
  const [estado, setEstado] = useState(inicial);
  const [erro, setErro] = useState<string | null>(null);
  const [pendente, iniciar] = useTransition();

  const alternar = (exigeSempre: boolean) => {
    setErro(null);
    iniciar(async () => {
      const r = await definir2FAConsole(exigeSempre);
      if (!r.ok) {
        setErro(r.erro);
        return;
      }
      setEstado({ ...estado, exigeSempre, configurado: true });
    });
  };

  return (
    <div>
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div className="min-w-0">
          <div className="flex items-center gap-2">
            <span className="text-sm font-medium text-ink-900">
              Pedir 2FA a cada login no console
            </span>
            <Badge tom={estado.exigeSempre ? "ativo" : "atencao"}>
              {estado.exigeSempre ? "sempre" : "lembra o navegador"}
            </Badge>
          </div>
          <p className="mt-1 max-w-2xl text-sm text-ink-500">
            {estado.exigeSempre
              ? "Todo login no console é desafiado, mesmo num navegador já marcado como confiável no backoffice. É o padrão."
              : "Um navegador marcado como confiável entra sem novo desafio por 30 dias — o mesmo comportamento do backoffice."}
          </p>
        </div>

        <button
          type="button"
          role="switch"
          aria-checked={estado.exigeSempre}
          disabled={pendente}
          onClick={() => alternar(!estado.exigeSempre)}
          className={`relative h-6 w-11 shrink-0 rounded-full transition disabled:opacity-50 ${
            estado.exigeSempre ? "bg-brand-600" : "bg-slate-300"
          }`}
        >
          <span
            className={`absolute top-0.5 h-5 w-5 rounded-full bg-white transition ${
              estado.exigeSempre ? "left-[22px]" : "left-0.5"
            }`}
          />
        </button>
      </div>

      <ul className="mt-4 space-y-1 text-xs text-ink-500">
        <li>
          • Vale para quem entra pelo <strong>Google</strong>. O login por senha no console
          sempre pede o código — o fator é obrigatório no próprio fluxo, e não passa por
          esta chave.
        </li>
        <li>
          • Desligar não some com o 2FA: o navegador só é dispensado depois de ter passado
          por um desafio e sido marcado como confiável.
        </li>
        <li>• A mudança vale no próximo login e fica registrada na auditoria.</li>
      </ul>

      {!estado.configurado && (
        <p className="mt-3 text-xs text-amber-700">
          O realm ainda não tem a condição de dispositivo confiável nos fluxos de 2FA. Rode{" "}
          <code>infra/prod/configure-keycloak-console-2fa.sh</code> uma vez para criá-la —
          depois disso este botão passa a funcionar.
        </p>
      )}
      {erro && <p className="mt-3 text-xs text-red-700">{erro}</p>}
    </div>
  );
}
