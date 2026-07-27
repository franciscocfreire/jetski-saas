"use client";

import { useState, useTransition } from "react";
import { LogIn } from "lucide-react";
import { Botao } from "@/components/Acao";
import { abrirSuporte } from "@/lib/actions";
import type { AberturaSuporte } from "@/lib/types";

/**
 * Entrar na empresa — o que substitui o god mode do switcher.
 *
 * O acesso não é implícito: exige motivo, nasce com prazo e por padrão é somente
 * leitura. O console devolve um CÓDIGO de uso único e vida curta; o backoffice o troca
 * pelo cookie de sessão. O token nunca passa pela URL.
 */
export function EntrarNaEmpresa({
  tenantId,
  razaoSocial,
  backofficeUrl,
}: {
  tenantId: string;
  razaoSocial: string;
  backofficeUrl: string;
}) {
  const [aberto, setAberto] = useState(false);
  const [motivo, setMotivo] = useState("");
  const [somenteLeitura, setSomenteLeitura] = useState(true);
  const [erro, setErro] = useState<string | null>(null);
  const [pendente, iniciar] = useTransition();

  if (!aberto) {
    return (
      <Botao variante="primaria" onClick={() => setAberto(true)}>
        <LogIn className="h-4 w-4" /> Entrar na empresa
      </Botao>
    );
  }

  const valido = motivo.trim().length >= 5;

  return (
    <div className="rounded-md border border-brand-200 bg-brand-50/50 p-3">
      <p className="text-sm text-ink-700">
        Você vai operar <strong>{razaoSocial}</strong>. O acesso fica registrado — a
        empresa pode consultar quem entrou e por quê.
      </p>

      <input
        autoFocus
        value={motivo}
        onChange={(e) => setMotivo(e.target.value)}
        placeholder="motivo do acesso (ex.: cliente relatou cobrança duplicada)"
        className="mt-3 w-full rounded-md border border-slate-300 px-3 py-1.5 text-sm"
      />

      <label className="mt-3 flex items-start gap-2 text-sm">
        <input
          type="checkbox"
          checked={somenteLeitura}
          onChange={(e) => setSomenteLeitura(e.target.checked)}
          className="mt-1"
        />
        <span>
          <span className="font-medium">Somente leitura</span>
          <span className="block text-xs text-ink-500">
            Recomendado. Desmarque só se precisar alterar dados da empresa.
          </span>
        </span>
      </label>

      <div className="mt-3 flex flex-wrap items-center gap-2">
        <Botao
          variante={somenteLeitura ? "primaria" : "perigo"}
          disabled={!valido || pendente}
          onClick={() => {
            setErro(null);
            iniciar(async () => {
              const r = await abrirSuporte(tenantId, motivo.trim(), somenteLeitura);
              if (!r.ok) {
                setErro(r.erro);
                return;
              }
              // Handoff em ABA NOVA: o console continua aberto atrás, então dá para
              // abrir outra empresa ou revogar a sessão sem perder o lugar. Funciona
              // porque a página de resgate espera a sessão do NextAuth (cookie, vale
              // em qualquer aba) em vez do token de sessionStorage, que é por aba.
              const { codigo } = r.dados as AberturaSuporte;
              const url = `${backofficeUrl}/suporte?codigo=${encodeURIComponent(codigo)}`;
              // SEM "noopener" nas features: com ela o window.open devolve null por
              // especificação (não há referência para entregar), o fallback de
              // pop-up bloqueado disparava sempre e a aba atual também navegava —
              // abriam duas, e a segunda encontrava o código já queimado.
              // O desligamento do opener vira explícito, logo abaixo.
              const aba = window.open(url, "_blank");
              if (!aba) {
                // bloqueador de pop-up: cai na mesma aba em vez de não fazer nada
                window.location.href = url;
                return;
              }
              aba.opener = null;
              setAberto(false);
              setMotivo("");
            });
          }}
        >
          {pendente
            ? "Abrindo…"
            : somenteLeitura
              ? "Entrar (somente leitura)"
              : "Entrar com escrita"}
        </Botao>
        <Botao onClick={() => setAberto(false)} disabled={pendente}>
          Cancelar
        </Botao>
      </div>
      {erro && <p className="mt-1 text-xs text-red-700">{erro}</p>}
    </div>
  );
}
