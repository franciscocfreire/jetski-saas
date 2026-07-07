"use client";

import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
import { useSession } from "next-auth/react";
import {
  IdCard,
  UploadCloud,
  CheckCircle2,
  Award,
  Loader2,
  LifeBuoy,
} from "lucide-react";
import {
  getHabilitacao,
  enviarCha,
  getHabilitacoes,
  usarTemporariaReserva,
  emitirNovaReserva,
  ApiError,
  type HabilitacaoCliente,
  type HabilitacaoTemporaria,
} from "@/lib/api";
import { Button, Card, Field, inputCls } from "@/components/ui";
import { EmaWizard } from "@/components/EmaWizard";

type Via = null | "tem" | "nao";

const CATEGORIAS = ["MOTONAUTA", "ARRAIS_AMADOR", "MSA", "CPA", "MTA-E", "OUTRA"];

/**
 * Habilitação REAL — caminho A (já tenho CHA): número/categoria/validade +
 * foto do documento. Caminho B (CHA-MTA-E via EMA/GRU) chega na P3 — por ora
 * orienta o atendimento da loja.
 */
export default function HabilitacaoPage() {
  const { id } = useParams<{ id: string }>();
  const { data: session, status } = useSession();
  const router = useRouter();

  const [hab, setHab] = useState<HabilitacaoCliente | null>(null);
  const [via, setVia] = useState<Via>(null);
  const [categoria, setCategoria] = useState("MOTONAUTA");
  const [numero, setNumero] = useState("");
  const [validade, setValidade] = useState("");
  const [foto, setFoto] = useState<{ nome: string; dataUrl: string } | null>(null);
  const [enviando, setEnviando] = useState(false);
  const [erro, setErro] = useState<string | null>(null);
  const [trocando, setTrocando] = useState(false);
  // temporária confirmada+vigente de OUTRA reserva — opção de reuso tardio
  const [temporariaElegivel, setTemporariaElegivel] = useState<HabilitacaoTemporaria | null>(null);

  useEffect(() => {
    if (status === "authenticated" && session?.accessToken) {
      getHabilitacoes(session.accessToken)
        .then((hs) => setTemporariaElegivel(
          hs.find((h) => h.vigente && h.confirmada && h.reservaId !== id) ?? null))
        .catch(() => {});
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [status, session?.accessToken, id]);

  const carregar = useCallback(async (token: string) => {
    try {
      const h = await getHabilitacao(token, id);
      setHab(h);
      // decisão já tomada na reserva (triagem do wizard) — não pergunta de novo
      setVia((atual) => atual ?? (h.via === "CHA" ? "tem" : h.via === "EMA" ? "nao" : null));
    } catch {
      setErro("Reserva não encontrada.");
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

  if (erro && !hab) return <p className="py-20 text-center text-slate-400">{erro}</p>;
  if (!hab) {
    return (
      <div className="flex justify-center py-20 text-slate-400">
        <Loader2 className="animate-spin" />
      </div>
    );
  }

  function selecionarFoto(f: File | undefined) {
    setErro(null);
    if (!f) return;
    if (!["image/jpeg", "image/png", "image/webp"].includes(f.type)) {
      setErro("A foto deve ser JPEG, PNG ou WebP.");
      return;
    }
    if (f.size > 5 * 1024 * 1024) {
      setErro("A foto deve ter até 5 MB.");
      return;
    }
    const reader = new FileReader();
    reader.onload = () => setFoto({ nome: f.name, dataUrl: reader.result as string });
    reader.readAsDataURL(f);
  }

  async function enviar() {
    if (!session?.accessToken || !numero.trim()) return;
    setEnviando(true);
    setErro(null);
    try {
      const atualizada = await enviarCha(session.accessToken, id, {
        categoria,
        numero: numero.trim(),
        validade: validade || undefined,
        fotoBase64: foto?.dataUrl,
      });
      setHab(atualizada);
    } catch (e) {
      setErro(e instanceof ApiError ? e.message : "Não foi possível enviar a habilitação.");
    } finally {
      setEnviando(false);
    }
  }

  return (
    <div className="mx-auto max-w-2xl">
      <Link href={`/conta/reservas/${id}`} className="text-sm text-slate-400 hover:text-slate-600">
        ← Voltar para a reserva
      </Link>
      <h1 className="mt-3 flex items-center gap-2 text-2xl font-bold text-ink-900">
        <IdCard className="text-brand-600" /> Habilitação náutica
      </h1>

      {hab.resolvida && hab.chaCategoria === "MTA-E TEMPORÁRIA" ? (
        <Card className="mt-6 flex flex-col items-center gap-3 p-8 text-center">
          <Award className="text-emerald-500" size={44} />
          <h2 className="text-lg font-semibold text-ink-900">Habilitação temporária vigente</h2>
          <p className="text-sm text-slate-500">
            Usando sua habilitação temporária
            {hab.chaValidade &&
              ` válida até ${new Date(hab.chaValidade + "T12:00:00").toLocaleDateString("pt-BR")}`}{" "}
            (GRU nº {hab.chaNumero}) — sem nova taxa da Marinha nesta reserva. Use o
            número da GRU para consultar o estado junto à Marinha.
          </p>
          <div className="flex flex-wrap justify-center gap-2">
            <Button href={`/conta/reservas/${id}`} variant="outline">
              Voltar para a reserva
            </Button>
            <Button
              variant="ghost"
              disabled={trocando}
              onClick={async () => {
                if (!session?.accessToken) return;
                setTrocando(true);
                try {
                  await emitirNovaReserva(session.accessToken, id);
                  setVia("nao");
                  await carregar(session.accessToken);
                } catch (e) {
                  setErro(e instanceof ApiError ? e.message : "Não foi possível trocar.");
                } finally {
                  setTrocando(false);
                }
              }}
            >
              {trocando ? "Trocando…" : "Emitir nova CHA-MTA-E"}
            </Button>
          </div>
        </Card>
      ) : hab.resolvida ? (
        <Card className="mt-6 flex flex-col items-center gap-3 p-8 text-center">
          <Award className="text-emerald-500" size={44} />
          <h2 className="text-lg font-semibold text-ink-900">Habilitação registrada</h2>
          <p className="text-sm text-slate-500">
            {hab.chaCategoria ? `${hab.chaCategoria} · ` : ""}nº {hab.chaNumero}
            {hab.temFotoCha && " · foto do documento anexada"}. A loja confere no
            check-in; a demonstração prática de segurança acontece no embarque.
          </p>
          <Button href={`/conta/reservas/${id}`} variant="outline">
            Voltar para a reserva
          </Button>
        </Card>
      ) : (
        <>
          {/* Triagem */}
          {via === null && (
            <Card className="mt-6 p-6">
              <h2 className="font-semibold text-ink-900">Você possui habilitação náutica?</h2>
              <p className="mt-1 text-sm text-slate-500">
                Arrais Amador, Motonauta ou CHA (ARA/MSA/CPA/MTA-E).
              </p>
              <div className="mt-4 grid gap-3 sm:grid-cols-2">
                <button
                  onClick={() => setVia("tem")}
                  className="rounded-xl border border-slate-200 p-4 text-left hover:border-brand-400"
                >
                  <CheckCircle2 className="text-emerald-500" size={20} />
                  <div className="mt-2 font-semibold text-ink-900">Sim, eu tenho</div>
                  <div className="text-sm text-slate-500">
                    Informe os dados e anexe a foto da sua CHA.
                  </div>
                </button>
                <button
                  onClick={() => setVia("nao")}
                  className="rounded-xl border border-slate-200 p-4 text-left hover:border-brand-400"
                >
                  <LifeBuoy className="text-brand-600" size={20} />
                  <div className="mt-2 font-semibold text-ink-900">Ainda não tenho</div>
                  <div className="text-sm text-slate-500">
                    Emita a habilitação especial CHA-MTA-E para o passeio.
                  </div>
                </button>
              </div>
            </Card>
          )}

          {/* Caminho A — tenho CHA */}
          {via === "tem" && (
            <Card className="mt-6 p-6">
              <h2 className="font-semibold text-ink-900">Dados da sua habilitação</h2>
              <div className="mt-4 grid gap-3 sm:grid-cols-2">
                <Field label="Categoria">
                  <select className={inputCls} value={categoria} onChange={(e) => setCategoria(e.target.value)}>
                    {CATEGORIAS.map((c) => (
                      <option key={c} value={c}>{c.replace("_", " ")}</option>
                    ))}
                  </select>
                </Field>
                <Field label="Número da CHA">
                  <input
                    className={inputCls}
                    value={numero}
                    onChange={(e) => setNumero(e.target.value)}
                    placeholder="Ex.: 123456789"
                  />
                </Field>
              </div>
              <div className="mt-3">
                <Field label="Validade" hint="Como impressa no documento">
                  <input
                    type="date"
                    className={inputCls}
                    value={validade}
                    onChange={(e) => setValidade(e.target.value)}
                  />
                </Field>
              </div>

              <label className="mt-4 flex cursor-pointer flex-col items-center gap-2 rounded-xl border-2 border-dashed border-slate-300 bg-slate-50 p-6 text-center hover:border-brand-400">
                <UploadCloud className="text-slate-400" size={26} />
                <span className="text-sm text-slate-600">
                  {foto ? foto.nome : "Foto da CHA (frente) — JPEG/PNG até 5 MB"}
                </span>
                <input
                  type="file"
                  accept="image/jpeg,image/png,image/webp"
                  className="hidden"
                  onChange={(e) => selecionarFoto(e.target.files?.[0])}
                />
              </label>

              {erro && (
                <p className="mt-3 rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">{erro}</p>
              )}

              <div className="mt-4 flex gap-2">
                <Button variant="outline" onClick={() => setVia(null)}>Voltar</Button>
                <Button
                  className="flex-1 gap-2"
                  onClick={enviar}
                  disabled={enviando || !numero.trim()}
                >
                  {enviando && <Loader2 size={15} className="animate-spin" />}
                  Enviar habilitação
                </Button>
              </div>
            </Card>
          )}

          {/* Caminho B — CHA-MTA-E (P3): wizard real */}
          {via === "nao" && (
            <div className="mt-6">
              <div className="mb-3 flex items-center justify-between">
                <h2 className="flex items-center gap-2 font-semibold text-ink-900">
                  <LifeBuoy className="text-brand-600" size={20} /> Emissão da CHA-MTA-E
                </h2>
                <Button variant="ghost" size="sm" onClick={() => setVia(null)}>
                  Voltar
                </Button>
              </div>
              <p className="mb-4 text-sm text-slate-500">
                Complete os 4 passos — a demonstração prática de segurança acontece no
                embarque, com o instrutor da loja.
              </p>
              {temporariaElegivel && (
                <div className="mb-4 flex flex-wrap items-center justify-between gap-2 rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-800">
                  <span>
                    Você tem uma <b>temporária confirmada válida até{" "}
                    {new Date(temporariaElegivel.validaAte + "T12:00:00").toLocaleDateString("pt-BR")}</b>{" "}
                    (GRU nº {temporariaElegivel.gruNumero}) — pode usá-la aqui, sem nova taxa.
                  </span>
                  <Button
                    size="sm"
                    disabled={trocando}
                    onClick={async () => {
                      if (!session?.accessToken) return;
                      setTrocando(true);
                      try {
                        await usarTemporariaReserva(session.accessToken, id);
                        await carregar(session.accessToken);
                      } catch (e) {
                        setErro(e instanceof ApiError ? e.message : "Não foi possível usar a temporária.");
                      } finally {
                        setTrocando(false);
                      }
                    }}
                  >
                    {trocando ? "Aplicando…" : "Usar minha temporária"}
                  </Button>
                </div>
              )}
              <EmaWizard reservaId={id} />
            </div>
          )}
        </>
      )}
    </div>
  );
}
