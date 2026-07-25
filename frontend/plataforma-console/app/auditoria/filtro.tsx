"use client";

import { useRouter } from "next/navigation";

/**
 * As opções vêm do próprio banco (`DISTINCT acao`) em vez de uma lista chumbada:
 * ação nova no backend aparece aqui sem tocar no console.
 */
export function FiltroAcao({ acoes, atual }: { acoes: string[]; atual?: string }) {
  const router = useRouter();
  return (
    <div className="flex items-center gap-2">
      <label htmlFor="acao" className="text-sm text-ink-500">
        Ação
      </label>
      <select
        id="acao"
        value={atual ?? ""}
        onChange={(e) =>
          router.push(e.target.value ? `/auditoria?acao=${encodeURIComponent(e.target.value)}` : "/auditoria")
        }
        className="rounded-md border border-slate-300 bg-white px-3 py-1.5 text-sm"
      >
        <option value="">Todas</option>
        {acoes.map((a) => (
          <option key={a} value={a}>
            {a}
          </option>
        ))}
      </select>
    </div>
  );
}
