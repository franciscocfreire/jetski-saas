"use client";

import Link from "next/link";
import { Star, ChevronRight } from "lucide-react";
import { useStore } from "@/lib/store";
import { getModelo } from "@/lib/mock";
import { brl, fmtDateTime } from "@/lib/cn";
import { Badge, Card, SectionTitle } from "@/components/ui";

export default function LocacoesPage() {
  const locacoes = useStore((s) => s.locacoes);

  return (
    <div>
      <SectionTitle sub="Seus passeios concluídos">Histórico</SectionTitle>
      <div className="grid gap-4">
        {locacoes.map((l) => {
          const m = getModelo(l.modeloId);
          return (
            <Link key={l.id} href={`/conta/locacoes/${l.id}`}>
              <Card className="flex items-center gap-4 p-4 transition hover:shadow-md">
                {/* eslint-disable-next-line @next/next/no-img-element */}
                <img
                  src={m?.fotoUrl}
                  alt=""
                  className="h-20 w-28 shrink-0 rounded-xl object-cover"
                />
                <div className="min-w-0 flex-1">
                  <span className="font-mono text-xs text-slate-400">{l.id}</span>
                  <h3 className="font-semibold text-ink-900">{m?.nome}</h3>
                  <p className="text-sm text-slate-500">
                    {fmtDateTime(l.data)} · {l.duracaoMin} min ·{" "}
                    {brl(l.valorTotal)}
                  </p>
                  <div className="mt-1">
                    {l.avaliada ? (
                      <Badge tone="green">
                        <Star size={11} className="fill-emerald-600" /> Avaliada
                      </Badge>
                    ) : (
                      <Badge tone="amber">Avalie sua experiência</Badge>
                    )}
                  </div>
                </div>
                <ChevronRight className="text-slate-300" />
              </Card>
            </Link>
          );
        })}
      </div>
    </div>
  );
}
