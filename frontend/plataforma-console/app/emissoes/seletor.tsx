"use client";

import { useRouter } from "next/navigation";

/** Últimas 12 competências (YYYY-MM), no fuso da operação. */
function ultimasCompetencias(n = 12): string[] {
  const hoje = new Date();
  return Array.from({ length: n }, (_, i) => {
    const d = new Date(hoje.getFullYear(), hoje.getMonth() - i, 1);
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}`;
  });
}

export function SeletorCompetencia({ atual }: { atual: string }) {
  const router = useRouter();
  return (
    <select
      value={atual}
      onChange={(e) => router.push(`/emissoes?competencia=${e.target.value}`)}
      className="rounded-md border border-slate-300 bg-white px-3 py-1.5 text-sm"
    >
      {ultimasCompetencias().map((c) => (
        <option key={c} value={c}>
          {c}
        </option>
      ))}
    </select>
  );
}
