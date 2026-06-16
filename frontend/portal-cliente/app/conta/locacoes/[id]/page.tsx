"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useState } from "react";
import { Star, Camera, Download, CheckCircle2 } from "lucide-react";
import { useStore } from "@/lib/store";
import { getModelo } from "@/lib/mock";
import { brl, fmtDateTime } from "@/lib/cn";
import { Button, Card } from "@/components/ui";

export default function LocacaoDetailPage() {
  const { id } = useParams<{ id: string }>();
  const locacao = useStore((s) => s.locacoes.find((l) => l.id === id));
  const avaliar = useStore((s) => s.avaliar);
  const m = locacao ? getModelo(locacao.modeloId) : undefined;

  const [nota, setNota] = useState(0);
  const [hover, setHover] = useState(0);
  const [comentario, setComentario] = useState("");

  if (!locacao || !m) return <p>Locação não encontrada.</p>;

  return (
    <div className="mx-auto max-w-2xl">
      <Link
        href="/conta/locacoes"
        className="text-sm text-slate-400 hover:text-slate-600"
      >
        ← Histórico
      </Link>

      <Card className="mt-3 p-5">
        <div className="flex gap-4">
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img
            src={m.fotoUrl}
            alt=""
            className="h-24 w-36 rounded-xl object-cover"
          />
          <div>
            <span className="font-mono text-xs text-slate-400">{locacao.id}</span>
            <h1 className="text-xl font-bold text-ink-900">{m.nome}</h1>
            <p className="text-sm text-slate-500">{fmtDateTime(locacao.data)}</p>
          </div>
        </div>

        <div className="mt-5 grid grid-cols-3 gap-3 text-center text-sm">
          <Stat label="Duração" value={`${locacao.duracaoMin} min`} />
          <Stat label="Faturável" value={`${locacao.duracaoMin - 5} min`} />
          <Stat label="Total" value={brl(locacao.valorTotal)} />
        </div>

        <Button variant="outline" className="mt-4 w-full" size="sm">
          <Download size={15} /> Baixar recibo (PDF)
        </Button>
      </Card>

      <h2 className="mb-2 mt-6 flex items-center gap-2 text-lg font-bold text-ink-900">
        <Camera size={18} /> Fotos do check-in / check-out
      </h2>
      <div className="grid grid-cols-4 gap-2">
        {[0, 1, 2, 3].map((i) => (
          <div
            key={i}
            className="grid aspect-square place-items-center rounded-xl bg-slate-100 text-slate-300"
          >
            <Camera size={22} />
          </div>
        ))}
      </div>

      {/* Avaliação */}
      <Card className="mt-6 p-6">
        <h2 className="text-lg font-bold text-ink-900">Avalie sua experiência</h2>
        {locacao.avaliada ? (
          <div className="mt-3 flex items-center gap-2 text-emerald-700">
            <CheckCircle2 size={18} /> Obrigado! Sua avaliação foi enviada.
          </div>
        ) : (
          <>
            <div className="mt-3 flex gap-1">
              {[1, 2, 3, 4, 5].map((n) => (
                <button
                  key={n}
                  onMouseEnter={() => setHover(n)}
                  onMouseLeave={() => setHover(0)}
                  onClick={() => setNota(n)}
                >
                  <Star
                    size={32}
                    className={
                      n <= (hover || nota)
                        ? "fill-amber-400 text-amber-400"
                        : "text-slate-300"
                    }
                  />
                </button>
              ))}
            </div>
            <textarea
              className="mt-3 h-24 w-full rounded-xl border border-slate-300 p-3 text-sm outline-none focus:border-brand-500"
              placeholder="Conte como foi o passeio…"
              value={comentario}
              onChange={(e) => setComentario(e.target.value)}
            />
            <Button
              className="mt-3 w-full"
              disabled={nota === 0}
              onClick={() => avaliar(locacao.id)}
            >
              Enviar avaliação
            </Button>
          </>
        )}
      </Card>
    </div>
  );
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-xl bg-slate-50 p-3">
      <div className="text-xs text-slate-400">{label}</div>
      <div className="mt-0.5 font-semibold text-ink-900">{value}</div>
    </div>
  );
}
