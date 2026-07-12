"use client";

import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
import { useSession } from "next-auth/react";
import { Star, Camera, CheckCircle2, Loader2, MapPin, Download } from "lucide-react";
import {
  getLocacao,
  avaliarLocacao,
  baixarRecibo,
  ApiError,
  type LocacaoClienteDetalhe,
} from "@/lib/api";
import { brl, fmtDateTime } from "@/lib/cn";
import { Button, Card } from "@/components/ui";
import { useToast } from "@/components/Toast";

export default function LocacaoDetailPage() {
  const { id } = useParams<{ id: string }>();
  const { data: session, status } = useSession();
  const router = useRouter();

  const { toast } = useToast();
  const [det, setDet] = useState<LocacaoClienteDetalhe | null>(null);
  const [nota, setNota] = useState(0);
  const [hover, setHover] = useState(0);
  const [comentario, setComentario] = useState("");
  const [enviando, setEnviando] = useState(false);
  const [baixando, setBaixando] = useState(false);
  const [erro, setErro] = useState<string | null>(null);

  const carregar = useCallback(async (token: string) => {
    try {
      setDet(await getLocacao(token, id));
    } catch {
      setErro("Locação não encontrada.");
    }
  }, [id]);

  useEffect(() => {
    if (status === "unauthenticated") {
      router.replace("/login");
      return;
    }
    if (status === "authenticated" && session?.accessToken) {
      carregar(session.accessToken);
    }
  }, [status, session?.accessToken, carregar, router]);

  if (erro && !det) return <p className="py-20 text-center text-slate-400">{erro}</p>;
  if (!det) {
    return (
      <div className="flex justify-center py-20 text-slate-400">
        <Loader2 className="animate-spin" />
      </div>
    );
  }

  const l = det.locacao;
  const fotos = det.fotos.filter((f) => f.downloadUrl);

  async function baixar() {
    if (!session?.accessToken || baixando) return;
    setBaixando(true);
    try {
      const blob = await baixarRecibo(session.accessToken, id);
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = `recibo-${id.slice(0, 8)}.pdf`;
      a.click();
      URL.revokeObjectURL(url);
    } catch (e) {
      toast(e instanceof ApiError ? e.message : "Não foi possível baixar o recibo.", "erro");
    } finally {
      setBaixando(false);
    }
  }

  async function avaliar() {
    if (!session?.accessToken || nota === 0) return;
    setEnviando(true);
    setErro(null);
    try {
      const atualizada = await avaliarLocacao(session.accessToken, id, nota, comentario);
      setDet((d) => (d ? { ...d, locacao: atualizada } : d));
      toast("Avaliação enviada — obrigado!");
    } catch (e) {
      setErro(e instanceof ApiError ? e.message : "Não foi possível enviar a avaliação.");
    } finally {
      setEnviando(false);
    }
  }

  return (
    <div className="mx-auto max-w-2xl">
      <Link href="/conta/locacoes" className="text-sm text-slate-400 hover:text-slate-600">
        ← Histórico
      </Link>

      <Card className="mt-3 p-5">

        <h1 className="text-xl font-bold text-ink-900">{l.modeloNome ?? "Jet ski"}</h1>
        <p className="flex items-center gap-1 text-sm text-slate-500">
          <MapPin size={13} /> {l.lojaNome}
          {l.jetskiSerie && <> · {l.jetskiSerie}</>}
        </p>
        {l.dataCheckIn && (
          <p className="mt-1 text-sm text-slate-500">{fmtDateTime(l.dataCheckIn)}</p>
        )}

        <div className="mt-5 grid grid-cols-3 gap-3 text-center text-sm">
          <Stat label="Duração" value={l.minutosUsados != null ? `${l.minutosUsados} min` : "—"} />
          <Stat label="Faturável" value={l.minutosFaturaveis != null ? `${l.minutosFaturaveis} min` : "—"} />
          <Stat label="Total" value={l.valorTotal != null ? brl(l.valorTotal) : "—"} />
        </div>

        {l.status === "FINALIZADA" && (
          <Button
            variant="outline"
            className="mt-4 w-full"
            size="sm"
            disabled={baixando}
            onClick={baixar}
          >
            {baixando ? <Loader2 size={15} className="animate-spin" /> : <Download size={15} />}
            Baixar recibo (PDF)
          </Button>
        )}
      </Card>

      <h2 className="mb-2 mt-6 flex items-center gap-2 text-lg font-bold text-ink-900">
        <Camera size={18} /> Fotos do check-in / check-out
      </h2>
      {fotos.length === 0 ? (
        <p className="rounded-xl bg-slate-50 px-4 py-3 text-sm text-slate-500">
          Sem fotos disponíveis para esta locação.
        </p>
      ) : (
        <div className="grid grid-cols-4 gap-2">
          {fotos.map((f) => (
            <a key={f.id} href={f.downloadUrl} target="_blank" rel="noreferrer"
              className="block aspect-square overflow-hidden rounded-xl bg-slate-100">
              {/* eslint-disable-next-line @next/next/no-img-element */}
              <img src={f.downloadUrl} alt={f.tipo}
                className="h-full w-full object-cover transition hover:scale-105" />
            </a>
          ))}
        </div>
      )}

      {/* Avaliação */}
      <Card className="mt-6 p-6">
        <h2 className="text-lg font-bold text-ink-900">Avalie sua experiência</h2>
        {l.status !== "FINALIZADA" ? (
          <p className="mt-2 text-sm text-slate-500">
            Você poderá avaliar assim que a locação for finalizada.
          </p>
        ) : l.avaliacaoNota != null ? (
          <div className="mt-3">
            <div className="flex items-center gap-2 text-emerald-700">
              <CheckCircle2 size={18} /> Obrigado! Sua avaliação foi enviada.
            </div>
            <div className="mt-2 flex gap-0.5">
              {[1, 2, 3, 4, 5].map((n) => (
                <Star key={n} size={20}
                  className={n <= (l.avaliacaoNota ?? 0) ? "fill-amber-400 text-amber-400" : "text-slate-300"} />
              ))}
            </div>
            {l.avaliacaoComentario && (
              <p className="mt-2 rounded-xl bg-slate-50 p-3 text-sm text-slate-600">
                “{l.avaliacaoComentario}”
              </p>
            )}
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
              maxLength={1000}
            />
            {erro && (
              <p className="mt-2 rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">{erro}</p>
            )}
            <Button className="mt-3 w-full" disabled={nota === 0 || enviando} onClick={avaliar}>
              {enviando && <Loader2 size={14} className="animate-spin" />}
              Enviar avaliação
            </Button>
          </>
        )}
      </Card>
      <p className="mt-8 text-center text-[11px] text-slate-300">
        Código da locação: {l.id}
      </p>
    </div>
  );
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div className="min-w-0 rounded-xl bg-slate-50 p-3">
      <div className="text-xs text-slate-400">{label}</div>
      <div className="mt-0.5 break-words font-semibold text-ink-900">{value}</div>
    </div>
  );
}
