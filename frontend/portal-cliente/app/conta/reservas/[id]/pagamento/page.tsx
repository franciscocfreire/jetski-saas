"use client";

import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { useSession } from "next-auth/react";
import {
  Copy,
  Check,
  UploadCloud,
  Clock,
  CheckCircle2,
  ShieldCheck,
  Loader2,
  XCircle,
} from "lucide-react";
import {
  getReserva,
  anexarComprovante,
  ApiError,
  type ReservaCliente,
} from "@/lib/api";
import { brl } from "@/lib/cn";
import { Button, Card } from "@/components/ui";
import { PixQr } from "@/components/PixQr";

const TIPOS_OK = ["image/jpeg", "image/png", "image/webp", "application/pdf"];

export default function PagamentoPage() {
  const { id } = useParams<{ id: string }>();
  const { data: session, status } = useSession();
  const router = useRouter();
  const fileRef = useRef<HTMLInputElement>(null);

  const [reserva, setReserva] = useState<ReservaCliente | null>(null);
  const [tipo, setTipo] = useState<"SINAL" | "TOTAL">("SINAL");
  const [arquivo, setArquivo] = useState<{ nome: string; contentType: string; base64: string } | null>(null);
  const [enviando, setEnviando] = useState(false);
  const [copiado, setCopiado] = useState(false);
  const [erro, setErro] = useState<string | null>(null);

  useEffect(() => {
    if (status === "unauthenticated") {
      router.replace("/login");
      return;
    }
    if (status === "authenticated" && session?.accessToken) {
      getReserva(session.accessToken, id)
        .then((r) => {
          setReserva(r);
          if (r.pagamento.tipo) setTipo(r.pagamento.tipo);
        })
        .catch(() => setErro("Reserva não encontrada."));
    }
  }, [status, session?.accessToken, id, router]);

  if (erro && !reserva) {
    return <p className="py-20 text-center text-slate-400">{erro}</p>;
  }
  if (!reserva) {
    return (
      <div className="flex justify-center py-20 text-slate-400">
        <Loader2 className="animate-spin" />
      </div>
    );
  }

  const pg = reserva.pagamento;
  const valorAtual = tipo === "TOTAL" ? pg.valorTotal : pg.valorSinal;
  const pixAtual = tipo === "TOTAL" ? pg.pixCopiaEColaTotal : pg.pixCopiaEColaSinal;

  function copiar() {
    if (!pixAtual) return;
    navigator.clipboard?.writeText(pixAtual).catch(() => {});
    setCopiado(true);
    setTimeout(() => setCopiado(false), 1500);
  }

  function selecionarArquivo(f: File | undefined) {
    setErro(null);
    if (!f) return;
    if (!TIPOS_OK.includes(f.type)) {
      setErro("Use imagem (JPEG/PNG/WebP) ou PDF.");
      return;
    }
    if (f.size > 5 * 1024 * 1024) {
      setErro("O comprovante deve ter até 5 MB.");
      return;
    }
    const reader = new FileReader();
    reader.onload = () => {
      const dataUrl = reader.result as string;
      setArquivo({ nome: f.name, contentType: f.type, base64: dataUrl.split(",")[1] });
    };
    reader.readAsDataURL(f);
  }

  async function enviar() {
    if (!session?.accessToken || !arquivo) return;
    setEnviando(true);
    setErro(null);
    try {
      const atualizada = await anexarComprovante(session.accessToken, id, {
        tipo,
        valorInformado: valorAtual,
        contentType: arquivo.contentType,
        dataBase64: arquivo.base64,
      });
      setReserva(atualizada);
      setArquivo(null);
    } catch (e) {
      setErro(e instanceof ApiError ? e.message : "Não foi possível enviar o comprovante.");
    } finally {
      setEnviando(false);
    }
  }

  return (
    <div className="mx-auto max-w-lg">
      <Link href={`/conta/reservas/${id}`} className="text-sm text-slate-400 hover:text-slate-600">
        ← Voltar para a reserva
      </Link>
      <h1 className="mt-3 text-2xl font-bold text-ink-900">Pagamento</h1>
      <p className="mt-1 text-sm text-slate-500">
        Escaneie o QR (valor exato já embutido), pague e envie o comprovante. A loja
        confirma e sua reserva fica garantida.
      </p>

      {pg.status === "PRESENCIAL" ? (
        <Card className="mt-6 flex flex-col items-center gap-3 p-8 text-center">
          <CheckCircle2 className="text-slate-400" size={44} />
          <h2 className="text-lg font-semibold text-ink-900">Pagamento na loja</h2>
          <p className="text-sm text-slate-500">
            Esta reserva foi feita no balcão — o pagamento é feito diretamente na
            loja (dinheiro, PIX ou cartão). Não há nada a pagar pelo portal.
          </p>
          <Button href={`/conta/reservas/${id}`} variant="outline">
            Voltar para a reserva
          </Button>
        </Card>
      ) : pg.status === "CONFIRMADO" ? (
        <Card className="mt-6 flex flex-col items-center gap-3 p-8 text-center">
          <CheckCircle2 className="text-emerald-500" size={44} />
          <h2 className="text-lg font-semibold text-ink-900">Pagamento confirmado</h2>
          <p className="text-sm text-slate-500">
            {pg.tipo === "TOTAL" ? "Valor total recebido" : "Sinal recebido"}. Sua
            reserva está garantida.
          </p>
          <Button href={`/conta/reservas/${id}`} variant="outline">
            Voltar para a reserva
          </Button>
        </Card>
      ) : (
        <>
          {pg.status === "RECUSADO" && (
            <div className="mt-4 flex items-start gap-2 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-800">
              <XCircle size={16} className="mt-0.5 shrink-0" />
              <span>
                <b>Comprovante recusado.</b> {pg.motivoRecusa ?? "Confira o valor."}{" "}
                Reenvie abaixo.
              </span>
            </div>
          )}

          <Card className="mt-4 p-6">
            <div className="grid grid-cols-2 gap-2">
              <button
                onClick={() => setTipo("SINAL")}
                className={`rounded-xl border p-3 text-left ${
                  tipo === "SINAL" ? "border-brand-500 bg-brand-50" : "border-slate-200 hover:border-slate-300"
                }`}
              >
                <div className="text-sm font-semibold text-ink-900">Sinal (30%)</div>
                <div className="text-lg font-bold text-brand-700">{brl(pg.valorSinal)}</div>
                <div className="text-xs text-slate-400">Restante no check-in</div>
              </button>
              <button
                onClick={() => setTipo("TOTAL")}
                className={`rounded-xl border p-3 text-left ${
                  tipo === "TOTAL" ? "border-brand-500 bg-brand-50" : "border-slate-200 hover:border-slate-300"
                }`}
              >
                <div className="text-sm font-semibold text-ink-900">Valor total</div>
                <div className="text-lg font-bold text-brand-700">{brl(pg.valorTotal)}</div>
                <div className="text-xs text-slate-400">Nada a pagar depois</div>
              </button>
            </div>

            <div className="mt-4 flex items-center justify-between border-t border-slate-100 pt-4">
              <span className="text-sm text-slate-500">
                A pagar agora · {tipo === "SINAL" ? "sinal" : "total"}
              </span>
              <span className="text-xl font-bold text-brand-700">{brl(valorAtual)}</span>
            </div>

            {pixAtual ? (
              <>
                <div className="mt-5 flex flex-col items-center">
                  <PixQr payload={pixAtual} />
                  <p className="mt-2 text-xs text-slate-400">
                    Aponte a câmera do seu banco — o valor já vai preenchido
                  </p>
                </div>

                <div className="mt-5">
                  <span className="text-xs font-medium text-slate-500">
                    PIX Copia e Cola · {reserva.lojaNome}
                  </span>
                  <div className="mt-1 flex items-center gap-2 rounded-xl border border-slate-200 bg-slate-50 p-3">
                    <code className="flex-1 truncate text-xs text-slate-700">{pixAtual}</code>
                    <Button size="sm" variant="outline" onClick={copiar}>
                      {copiado ? <Check size={15} /> : <Copy size={15} />}
                      {copiado ? "Copiado" : "Copiar"}
                    </Button>
                  </div>
                </div>
              </>
            ) : (
              <p className="mt-5 rounded-xl bg-amber-50 p-3 text-sm text-amber-800">
                A loja ainda não configurou a chave PIX — combine o pagamento pelo
                WhatsApp e envie o comprovante abaixo.
              </p>
            )}
          </Card>

          <Card className="mt-4 p-6">
            <h3 className="font-semibold text-ink-900">Envie o comprovante</h3>
            {pg.status === "EM_ANALISE" ? (
              <div className="mt-4 rounded-xl bg-amber-50 p-4 text-sm text-amber-800">
                <div className="flex items-center gap-2 font-semibold">
                  <Clock size={16} /> Comprovante em análise
                </div>
                <p className="mt-1 text-xs">
                  A loja está conferindo seu pagamento de {brl(pg.valorInformado ?? valorAtual)}.
                  Você pode acompanhar por aqui.
                </p>
              </div>
            ) : (
              <>
                <label className="mt-4 flex cursor-pointer flex-col items-center gap-2 rounded-xl border-2 border-dashed border-slate-300 bg-slate-50 p-8 text-center hover:border-brand-400">
                  <UploadCloud className="text-slate-400" size={28} />
                  <span className="text-sm text-slate-600">
                    {arquivo ? arquivo.nome : "Clique para anexar a imagem/PDF do comprovante"}
                  </span>
                  <input
                    ref={fileRef}
                    type="file"
                    accept={TIPOS_OK.join(",")}
                    className="hidden"
                    onChange={(e) => selecionarArquivo(e.target.files?.[0])}
                  />
                </label>
                {erro && (
                  <p className="mt-3 rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">{erro}</p>
                )}
                <Button
                  className="mt-4 w-full gap-2"
                  onClick={enviar}
                  disabled={!arquivo || enviando}
                >
                  {enviando && <Loader2 size={15} className="animate-spin" />}
                  Enviar comprovante ({brl(valorAtual)})
                </Button>
              </>
            )}
          </Card>

          <p className="mt-4 flex items-center justify-center gap-1 text-center text-xs text-slate-400">
            <ShieldCheck size={13} /> Confirmação manual pela loja — você vê o status
            aqui assim que validarem.
          </p>
        </>
      )}
    </div>
  );
}
