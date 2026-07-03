"use client";

import { withBase } from "@/lib/base";
import { Suspense, useEffect, useState } from "react";
import Link from "next/link";
import { useParams, useRouter, useSearchParams } from "next/navigation";
import { useSession, signIn } from "next-auth/react";
import {
  CalendarRange,
  ClipboardList,
  CheckCircle2,
  Loader2,
  ShieldCheck,
  LogIn,
} from "lucide-react";
import {
  getModeloPublico,
  criarReserva,
  getSelf,
  ApiError,
  type MarketplaceModelo,
} from "@/lib/api";
import { brl } from "@/lib/cn";
import { Button, Card, Field, inputCls } from "@/components/ui";

const SINAL_PCT = 0.3;

/**
 * Wizard de reserva REAL: Período → Dados → Confirmar (exige login) →
 * cria a reserva no backend e segue para o pagamento (PIX com valor exato).
 */
export default function ReservarPage() {
  return (
    <Suspense fallback={<div className="py-24 text-center text-slate-400">Carregando…</div>}>
      <Wizard />
    </Suspense>
  );
}

function Wizard() {
  const { modeloId } = useParams<{ modeloId: string }>();
  const params = useSearchParams();
  const router = useRouter();
  const { data: session, status } = useSession();

  const [m, setM] = useState<MarketplaceModelo | null>(null);
  const [step, setStep] = useState(0);

  const [data, setData] = useState(params.get("data") ?? "");
  const [hora, setHora] = useState(params.get("hora") ?? "10:00");
  const [horas, setHoras] = useState(Number(params.get("horas") ?? 2));
  const [observacoes, setObservacoes] = useState("");
  const [cpf, setCpf] = useState("");
  const [cpfCadastro, setCpfCadastro] = useState<string | null>(null);
  const [telefone, setTelefone] = useState("");
  const [tipo, setTipo] = useState<"SINAL" | "TOTAL">("SINAL");
  const [criando, setCriando] = useState(false);
  const [indoLogin, setIndoLogin] = useState(false);
  const [erro, setErro] = useState<string | null>(null);

  useEffect(() => {
    getModeloPublico(modeloId).then(setM).catch(() => setM(null));
  }, [modeloId]);

  // CPF do cadastro (identidade global): a reserva sempre usa ele
  useEffect(() => {
    if (status === "authenticated" && session?.accessToken) {
      getSelf(session.accessToken)
        .then((s) => {
          const doc = s.identidade?.cpf;
          if (doc) {
            setCpfCadastro(doc);
            setCpf(doc);
          }
        })
        .catch(() => {});
    }
  }, [status, session?.accessToken]);

  if (!m) {
    return (
      <div className="flex justify-center py-24 text-slate-400">
        <Loader2 className="animate-spin" />
      </div>
    );
  }

  const total = m.precoBaseHora * horas;
  const steps = ["Período", "Dados", "Confirmar e pagar"];
  const logged = status === "authenticated";

  const inicioISO = data ? `${data}T${hora}:00` : null;
  const fimISO = inicioISO
    ? (() => {
        const f = new Date(new Date(inicioISO).getTime() + horas * 3600000);
        const p = (n: number) => String(n).padStart(2, "0");
        return `${f.getFullYear()}-${p(f.getMonth() + 1)}-${p(f.getDate())}T${p(f.getHours())}:${p(f.getMinutes())}:00`;
      })()
    : null;

  async function confirmar() {
    if (!session?.accessToken || !inicioISO || !fimISO || !m) return;
    setCriando(true);
    setErro(null);
    try {
      const reserva = await criarReserva(session.accessToken, {
        lojaSlug: m.lojaSlug,
        modeloId: m.id,
        dataInicio: inicioISO,
        dataFimPrevista: fimISO,
        observacoes: observacoes || undefined,
        pagamentoTipo: tipo,
        cpf: cpf || undefined,
        telefone: telefone || undefined,
      });
      router.push(`/conta/reservas/${reserva.id}/pagamento?tipo=${tipo}`);
    } catch (e) {
      setErro(e instanceof ApiError ? e.message : "Não foi possível criar a reserva.");
      setCriando(false);
    }
  }

  return (
    <div className="mx-auto max-w-xl">
      <h1 className="text-2xl font-bold text-ink-900">Reservar {m.nome}</h1>
      <p className="text-sm text-slate-500">
        {m.empresaNome} · {m.localizacao}
      </p>

      {/* Stepper */}
      <div className="mt-5 flex flex-wrap items-center gap-2">
        {steps.map((s, i) => (
          <div key={s} className="flex items-center gap-2">
            <span
              className={`grid h-7 w-7 place-items-center rounded-full text-xs font-bold ${
                i < step
                  ? "bg-emerald-500 text-white"
                  : i === step
                    ? "bg-brand-600 text-white"
                    : "bg-slate-200 text-slate-500"
              }`}
            >
              {i < step ? "✓" : i + 1}
            </span>
            <span className={`text-sm ${i === step ? "font-semibold text-ink-900" : "text-slate-400"}`}>
              {s}
            </span>
            {i < steps.length - 1 && <span className="h-px w-6 bg-slate-200" />}
          </div>
        ))}
      </div>

      {/* Passo 1 — Período */}
      {step === 0 && (
        <Card className="mt-6 p-6">
          <h2 className="flex items-center gap-2 font-semibold text-ink-900">
            <CalendarRange size={18} /> Quando será o passeio?
          </h2>
          <div className="mt-4 grid grid-cols-2 gap-3">
            <Field label="Data">
              <input type="date" className={inputCls} value={data} onChange={(e) => setData(e.target.value)} />
            </Field>
            <Field label="Hora">
              <input type="time" className={inputCls} value={hora} onChange={(e) => setHora(e.target.value)} />
            </Field>
          </div>
          <div className="mt-3">
            <Field label="Duração">
              <select className={inputCls} value={horas} onChange={(e) => setHoras(Number(e.target.value))}>
                <option value={1}>1 hora</option>
                <option value={2}>2 horas</option>
                <option value={3}>3 horas</option>
                <option value={4}>4 horas</option>
              </select>
            </Field>
          </div>
          <Button className="mt-5 w-full" disabled={!data} onClick={() => setStep(1)}>
            Continuar
          </Button>
        </Card>
      )}

      {/* Passo 2 — Dados */}
      {step === 1 && (
        <Card className="mt-6 p-6">
          <h2 className="flex items-center gap-2 font-semibold text-ink-900">
            <ClipboardList size={18} /> Dados da reserva
          </h2>
          <div className="mt-4 grid gap-3">
            <Field
              label={cpfCadastro ? "CPF (do seu cadastro)" : "CPF (opcional — agiliza o check-in)"}
              hint={cpfCadastro ? "A reserva sempre usa o CPF do cadastro — para corrigir, fale com a loja" : undefined}
            >
              <input
                className={inputCls}
                value={cpf}
                onChange={(e) => setCpf(e.target.value)}
                placeholder="000.000.000-00"
                disabled={!!cpfCadastro}
              />
            </Field>
            <Field label="Telefone/WhatsApp (opcional)">
              <input
                className={inputCls}
                value={telefone}
                onChange={(e) => setTelefone(e.target.value)}
                placeholder="(48) 99999-9999"
              />
            </Field>
            <Field label="Observações (opcional)">
              <textarea
                className={`${inputCls} min-h-20`}
                value={observacoes}
                onChange={(e) => setObservacoes(e.target.value)}
                placeholder="Ex.: 2 pessoas, primeira vez"
              />
            </Field>
          </div>
          <div className="mt-5 flex gap-2">
            <Button variant="outline" onClick={() => setStep(0)}>Voltar</Button>
            <Button className="flex-1" onClick={() => setStep(2)}>Continuar</Button>
          </div>
        </Card>
      )}

      {/* Passo 3 — Confirmar (exige login) */}
      {step === 2 && (
        <Card className="mt-6 p-6">
          <h2 className="font-semibold text-ink-900">Resumo</h2>
          <div className="mt-3 space-y-1.5 text-sm">
            <Row label="Modelo" value={m.nome} />
            <Row label="Loja" value={m.empresaNome} />
            <Row label="Quando" value={`${data.split("-").reverse().join("/")} ${hora} · ${horas}h`} />
            <Row label="Valor estimado" value={brl(total)} />
          </div>

          <div className="mt-4 grid grid-cols-2 gap-2">
            <button
              onClick={() => setTipo("SINAL")}
              className={`rounded-xl border p-3 text-left ${
                tipo === "SINAL" ? "border-brand-500 bg-brand-50" : "border-slate-200 hover:border-slate-300"
              }`}
            >
              <div className="text-sm font-semibold text-ink-900">Sinal (30%)</div>
              <div className="text-lg font-bold text-brand-700">{brl(total * SINAL_PCT)}</div>
              <div className="text-xs text-slate-400">Restante no check-in</div>
            </button>
            <button
              onClick={() => setTipo("TOTAL")}
              className={`rounded-xl border p-3 text-left ${
                tipo === "TOTAL" ? "border-brand-500 bg-brand-50" : "border-slate-200 hover:border-slate-300"
              }`}
            >
              <div className="text-sm font-semibold text-ink-900">Valor total</div>
              <div className="text-lg font-bold text-brand-700">{brl(total)}</div>
              <div className="text-xs text-slate-400">Nada a pagar depois</div>
            </button>
          </div>

          {erro && (
            <p className="mt-4 rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">{erro}</p>
          )}

          <div className="mt-5 flex gap-2">
            <Button variant="outline" onClick={() => setStep(1)}>Voltar</Button>
            {logged ? (
              <Button className="flex-1 gap-2" onClick={confirmar} disabled={criando}>
                {criando ? <Loader2 size={15} className="animate-spin" /> : <CheckCircle2 size={15} />}
                Confirmar e ir para o PIX
              </Button>
            ) : (
              <Button
                className="flex-1 gap-2"
                disabled={indoLogin}
                onClick={() => {
                  if (indoLogin) return;
                  setIndoLogin(true);
                  signIn("keycloak", {
                    callbackUrl: withBase(`/reservar/${m.id}?data=${data}&hora=${hora}&horas=${horas}`),
                  });
                }}
              >
                <LogIn size={15} /> Entrar para confirmar
              </Button>
            )}
          </div>
          {!logged && (
            <p className="mt-3 text-center text-xs text-slate-400">
              Não tem conta?{" "}
              <Link href="/cadastro" className="font-medium text-brand-600">Cadastre-se</Link>{" "}
              — leva 1 minuto e você volta para cá.
            </p>
          )}
          <p className="mt-3 flex items-center justify-center gap-1 text-center text-xs text-slate-400">
            <ShieldCheck size={12} /> A reserva expira em 24h se o pagamento não for enviado.
          </p>
        </Card>
      )}
    </div>
  );
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex justify-between">
      <span className="text-slate-500">{label}</span>
      <span className="font-medium text-ink-900">{value}</span>
    </div>
  );
}
