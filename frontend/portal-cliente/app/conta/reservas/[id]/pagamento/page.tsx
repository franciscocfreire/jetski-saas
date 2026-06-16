"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useState } from "react";
import {
  Copy,
  Check,
  QrCode,
  UploadCloud,
  Clock,
  CheckCircle2,
  ShieldCheck,
} from "lucide-react";
import { useStore } from "@/lib/store";
import { getModelo, getLoja } from "@/lib/mock";
import { brl } from "@/lib/cn";
import { Button, Card } from "@/components/ui";

export default function PagamentoPage() {
  const { id } = useParams<{ id: string }>();
  const reserva = useStore((s) => s.reservas.find((r) => r.id === id));
  const setEtapa = useStore((s) => s.setEtapa);
  const setPagamentoTipo = useStore((s) => s.setPagamentoTipo);
  const [copiado, setCopiado] = useState(false);
  const [enviado, setEnviado] = useState(false);

  const m = reserva ? getModelo(reserva.modeloId) : undefined;
  const loja = m ? getLoja(m.lojaId) : undefined;
  if (!reserva || !m || !loja) return <p>Reserva não encontrada.</p>;

  const estado = reserva.sinal;
  const tipo = reserva.pagamentoTipo ?? "sinal";
  const sinal30 = Math.round(reserva.valorEstimado * 0.3);

  function copiar() {
    navigator.clipboard?.writeText(loja!.pixChave).catch(() => {});
    setCopiado(true);
    setTimeout(() => setCopiado(false), 1500);
  }

  return (
    <div className="mx-auto max-w-lg">
      <Link
        href={`/conta/reservas/${id}`}
        className="text-sm text-slate-400 hover:text-slate-600"
      >
        ← Voltar para a reserva
      </Link>
      <h1 className="mt-3 text-2xl font-bold text-ink-900">Pagamento</h1>
      <p className="mt-1 text-sm text-slate-500">
        Escolha como pagar, faça o PIX e envie o comprovante. A loja confirma em
        até alguns minutos.
      </p>

      {estado === "ok" ? (
        <Card className="mt-6 flex flex-col items-center gap-3 p-8 text-center">
          <CheckCircle2 className="text-emerald-500" size={44} />
          <h2 className="text-lg font-semibold text-ink-900">
            Pagamento confirmado
          </h2>
          <p className="text-sm text-slate-500">
            Recebemos {brl(reserva.valorSinal)}
            {tipo === "sinal" ? " (sinal)" : " (valor total)"}. Sua reserva está
            garantida.
          </p>
          <Button href={`/conta/reservas/${id}`} variant="outline">
            Voltar para a reserva
          </Button>
        </Card>
      ) : (
        <>
          <Card className="mt-6 p-6">
            <div className="grid grid-cols-2 gap-2">
              <button
                onClick={() => setPagamentoTipo(reserva.id, "sinal")}
                className={`rounded-xl border p-3 text-left ${
                  tipo === "sinal"
                    ? "border-brand-500 bg-brand-50"
                    : "border-slate-200 hover:border-slate-300"
                }`}
              >
                <div className="text-sm font-semibold text-ink-900">
                  Sinal (30%)
                </div>
                <div className="text-lg font-bold text-brand-700">
                  {brl(sinal30)}
                </div>
                <div className="text-xs text-slate-400">Restante no check-in</div>
              </button>
              <button
                onClick={() => setPagamentoTipo(reserva.id, "total")}
                className={`rounded-xl border p-3 text-left ${
                  tipo === "total"
                    ? "border-brand-500 bg-brand-50"
                    : "border-slate-200 hover:border-slate-300"
                }`}
              >
                <div className="text-sm font-semibold text-ink-900">
                  Valor total
                </div>
                <div className="text-lg font-bold text-brand-700">
                  {brl(reserva.valorEstimado)}
                </div>
                <div className="text-xs text-slate-400">Nada a pagar depois</div>
              </button>
            </div>

            <div className="mt-4 flex items-center justify-between border-t border-slate-100 pt-4">
              <span className="text-sm text-slate-500">
                A pagar agora · {tipo === "sinal" ? "sinal" : "total"}
              </span>
              <span className="text-xl font-bold text-brand-700">
                {brl(reserva.valorSinal)}
              </span>
            </div>

            <div className="mt-5 flex flex-col items-center">
              <div className="grid h-44 w-44 place-items-center rounded-2xl border-2 border-dashed border-slate-300 bg-slate-50 text-slate-300">
                <QrCode size={120} strokeWidth={1} />
              </div>
              <p className="mt-2 text-xs text-slate-400">
                Aponte a câmera do seu banco
              </p>
            </div>

            <div className="mt-5">
              <span className="text-xs font-medium text-slate-500">
                PIX Copia e Cola · {loja.nome}
              </span>
              <div className="mt-1 flex items-center gap-2 rounded-xl border border-slate-200 bg-slate-50 p-3">
                <code className="flex-1 truncate text-sm text-slate-700">
                  {loja.pixChave}
                </code>
                <Button size="sm" variant="outline" onClick={copiar}>
                  {copiado ? <Check size={15} /> : <Copy size={15} />}
                  {copiado ? "Copiado" : "Copiar"}
                </Button>
              </div>
              <p className="mt-2 text-xs text-slate-400">
                CNPJ {loja.cnpj} · {loja.nome}
              </p>
            </div>
          </Card>

          <Card className="mt-4 p-6">
            <h3 className="font-semibold text-ink-900">Envie o comprovante</h3>
            {estado === "em_validacao" || enviado ? (
              <div className="mt-4 rounded-xl bg-amber-50 p-4 text-sm text-amber-800">
                <div className="flex items-center gap-2 font-semibold">
                  <Clock size={16} /> Comprovante em análise
                </div>
                <p className="mt-1 text-xs">
                  A loja está conferindo seu pagamento. Você será avisado por
                  e-mail.
                </p>
                {/* Atalho de demonstração */}
                <button
                  onClick={() => setEtapa(reserva.id, "sinal", "ok")}
                  className="mt-3 w-full rounded-lg border border-amber-300 bg-white py-2 text-xs font-medium text-amber-800 hover:bg-amber-100"
                >
                  ▶︎ Simular confirmação do staff (demo)
                </button>
              </div>
            ) : (
              <>
                <label className="mt-4 flex cursor-pointer flex-col items-center gap-2 rounded-xl border-2 border-dashed border-slate-300 bg-slate-50 p-8 text-center hover:border-brand-400">
                  <UploadCloud className="text-slate-400" size={28} />
                  <span className="text-sm text-slate-600">
                    Clique para anexar a imagem/PDF do comprovante
                  </span>
                  <input
                    type="file"
                    className="hidden"
                    onChange={() => setEnviado(true)}
                  />
                </label>
                <Button
                  className="mt-4 w-full"
                  onClick={() => {
                    setEnviado(true);
                    setEtapa(reserva.id, "sinal", "em_validacao");
                  }}
                >
                  Enviar comprovante
                </Button>
              </>
            )}
          </Card>

          <p className="mt-4 flex items-center justify-center gap-1 text-center text-xs text-slate-400">
            <ShieldCheck size={13} /> No v1 a confirmação é manual pelo staff
            (sem gateway de pagamento).
          </p>
        </>
      )}
    </div>
  );
}
