"use client";

import { useRouter, useSearchParams } from "next/navigation";
import { clsx } from "clsx";
import { useState } from "react";

/** Filtro por status + busca. Estado vive na URL — link de fila compartilhável. */
export function FiltroEmpresas({
  porStatus,
  statusAtual,
  buscaAtual,
  total,
}: {
  porStatus: Record<string, number>;
  statusAtual?: string;
  buscaAtual: string;
  total: number;
}) {
  const router = useRouter();
  const params = useSearchParams();
  const [busca, setBusca] = useState(buscaAtual);

  function navegar(mudanca: Record<string, string | null>) {
    const p = new URLSearchParams(params.toString());
    for (const [k, v] of Object.entries(mudanca)) {
      if (v) p.set(k, v);
      else p.delete(k);
    }
    router.push(`/empresas${p.toString() ? `?${p}` : ""}`);
  }

  // PENDENTE_APROVACAO primeiro: é a fila que exige ação do operador.
  const status = Object.keys(porStatus).sort((a, b) =>
    a === "PENDENTE_APROVACAO" ? -1 : b === "PENDENTE_APROVACAO" ? 1 : a.localeCompare(b),
  );

  return (
    <div className="flex flex-wrap items-center gap-2">
      <Chip ativo={!statusAtual} onClick={() => navegar({ status: null })}>
        Todas ({total})
      </Chip>
      {status.map((s) => (
        <Chip
          key={s}
          ativo={statusAtual === s}
          onClick={() => navegar({ status: statusAtual === s ? null : s })}
        >
          {s.replace(/_/g, " ").toLowerCase()} ({porStatus[s]})
        </Chip>
      ))}

      <form
        className="ml-auto"
        onSubmit={(e) => {
          e.preventDefault();
          navegar({ q: busca.trim() || null });
        }}
      >
        <input
          value={busca}
          onChange={(e) => setBusca(e.target.value)}
          placeholder="buscar por nome ou slug"
          className="w-56 rounded-md border border-slate-300 px-3 py-1.5 text-sm"
        />
      </form>
    </div>
  );
}

function Chip({
  ativo,
  onClick,
  children,
}: {
  ativo: boolean;
  onClick: () => void;
  children: React.ReactNode;
}) {
  return (
    <button
      onClick={onClick}
      className={clsx(
        "rounded-full px-3 py-1 text-xs font-medium transition",
        ativo
          ? "bg-brand-700 text-white"
          : "border border-slate-300 bg-white text-ink-700 hover:bg-slate-50",
      )}
    >
      {children}
    </button>
  );
}
