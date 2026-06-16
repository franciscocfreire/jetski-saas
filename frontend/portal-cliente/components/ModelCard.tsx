import Link from "next/link";
import { Star, MapPin, Users, Gauge } from "lucide-react";
import { brl } from "@/lib/cn";
import { Badge } from "./ui";
import type { Modelo } from "@/lib/mock";

export function ModelCard({ m }: { m: Modelo }) {
  return (
    <Link
      href={`/modelo/${m.id}`}
      className="group overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm transition hover:shadow-md"
    >
      <div className="relative h-44 overflow-hidden bg-slate-100">
        {/* eslint-disable-next-line @next/next/no-img-element */}
        <img
          src={m.fotoUrl}
          alt={m.nome}
          className="h-full w-full object-cover transition group-hover:scale-105"
        />
        {m.destaque && (
          <span className="absolute left-3 top-3">
            <Badge tone="brand">Destaque</Badge>
          </span>
        )}
        <span className="absolute right-3 top-3 flex items-center gap-1 rounded-full bg-white/90 px-2 py-0.5 text-xs font-semibold text-slate-700">
          <Star size={12} className="fill-amber-400 text-amber-400" />
          {m.rating}
        </span>
      </div>
      <div className="p-4">
        <div className="flex items-center gap-1 text-xs text-slate-400">
          <MapPin size={12} /> {m.cidade}
        </div>
        <h3 className="mt-1 font-semibold text-ink-900">{m.nome}</h3>
        <p className="text-xs text-slate-500">{m.lojaNome}</p>
        <div className="mt-3 flex items-center gap-3 text-xs text-slate-500">
          <span className="flex items-center gap-1">
            <Gauge size={13} /> {m.potenciaHp} HP
          </span>
          <span className="flex items-center gap-1">
            <Users size={13} /> {m.capacidadePessoas} pessoas
          </span>
        </div>
        <div className="mt-3 flex items-end justify-between border-t border-slate-100 pt-3">
          <div>
            <span className="text-lg font-bold text-ink-900">
              {brl(m.precoBaseHora)}
            </span>
            <span className="text-xs text-slate-400"> /hora</span>
          </div>
          <span className="text-sm font-medium text-brand-600 group-hover:underline">
            Ver
          </span>
        </div>
      </div>
    </Link>
  );
}
