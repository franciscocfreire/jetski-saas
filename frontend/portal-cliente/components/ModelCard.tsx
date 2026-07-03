import Link from "next/link";
import { MapPin, Users } from "lucide-react";
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
      <div className="relative h-44 overflow-hidden bg-slate-100">
        {/* eslint-disable-next-line @next/next/no-img-element */}
        <img
          src={foto}
          alt={m.nome}
          className="h-full w-full object-cover transition group-hover:scale-105"
        />
      </div>
      <div className="p-4">
        <div className="flex items-center gap-1 text-xs text-slate-400">
          <MapPin size={12} /> {m.localizacao}
        </div>
        <h3 className="mt-1 font-semibold text-ink-900">{m.nome}</h3>
        <p className="text-xs text-slate-500">{m.empresaNome}</p>
        <div className="mt-3 flex items-center gap-3 text-xs text-slate-500">
          {m.fabricante && <span>{m.fabricante}</span>}
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
