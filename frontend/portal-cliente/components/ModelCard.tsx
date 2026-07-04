import Link from "next/link";
import { MapPin, Users, Star } from "lucide-react";
import { brl } from "@/lib/cn";
import { fotoPrincipal, type MarketplaceModelo } from "@/lib/api";
import { jetImage } from "@/lib/img";

export function ModelCard({ m }: { m: MarketplaceModelo }) {
  const foto = fotoPrincipal(m) ?? jetImage("200", "230", m.id.charCodeAt(0));
  return (
    <Link
      href={`/modelo/${m.id}`}
      className="group overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm transition hover:shadow-md"
    >
      <div className="relative h-52 overflow-hidden bg-slate-100">
        {/* eslint-disable-next-line @next/next/no-img-element */}
        <img
          src={foto}
          alt={m.nome}
          className="h-full w-full object-cover transition duration-300 group-hover:scale-105"
        />
        {/* gradiente inferior: nome/loja legíveis sobre a foto */}
        <div className="absolute inset-x-0 bottom-0 h-24 bg-gradient-to-t from-ink-900/80 to-transparent" />
        {m.notaMedia != null && (m.totalAvaliacoes ?? 0) > 0 && (
          <span className="absolute right-3 top-3 flex items-center gap-1 rounded-full bg-white/95 px-2 py-0.5 text-xs font-semibold text-slate-700 shadow-sm">
            <Star size={12} className="fill-amber-400 text-amber-400" />
            {m.notaMedia.toFixed(1)} ({m.totalAvaliacoes})
          </span>
        )}
        <span className="absolute left-3 top-3 flex items-center gap-1 rounded-full bg-ink-900/60 px-2 py-0.5 text-xs font-medium text-white backdrop-blur-sm">
          <Users size={12} /> {m.capacidadePessoas}
        </span>
        <div className="absolute inset-x-0 bottom-0 p-4">
          <h3 className="text-lg font-bold leading-tight text-white">{m.nome}</h3>
          <p className="flex items-center gap-1 text-xs text-white/80">
            {m.empresaNome} · <MapPin size={11} /> {m.localizacao}
          </p>
        </div>
      </div>
      <div className="flex items-end justify-between p-4">
        <div>
          <span className="text-[11px] uppercase tracking-wide text-slate-400">
            a partir de
          </span>
          <div>
            <span className="text-xl font-bold tabular-nums text-ink-900">
              {brl(m.precoBaseHora)}
            </span>
            <span className="text-xs text-slate-400"> /hora</span>
          </div>
        </div>
        <span className="rounded-xl bg-brand-600 px-4 py-2 text-sm font-medium text-white transition group-hover:bg-brand-700">
          Reservar
        </span>
      </div>
    </Link>
  );
}
