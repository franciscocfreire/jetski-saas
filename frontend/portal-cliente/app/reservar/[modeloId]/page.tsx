"use client";

import { Suspense, useState } from "react";
import { useParams, useRouter, useSearchParams } from "next/navigation";
import {
  CalendarClock,
  User,
  ClipboardList,
  CheckCircle2,
  ChevronRight,
  CreditCard,
  QrCode,
  Copy,
  Check,
  UploadCloud,
  ShieldCheck,
} from "lucide-react";
import { getModelo, getLoja, type Reserva } from "@/lib/mock";
import { useStore } from "@/lib/store";
import { brl, fmtDate } from "@/lib/cn";
import { Button, Card, Field, inputCls } from "@/components/ui";

function Wizard() {
  const { modeloId } = useParams<{ modeloId: string }>();
  const router = useRouter();
  const sp = useSearchParams();
  const m = getModelo(modeloId);
  const loja = m ? getLoja(m.lojaId) : undefined;

  const auth = useStore((s) => s.auth);
  const signup = useStore((s) => s.signup);
  const addReserva = useStore((s) => s.addReserva);

  // O layout do fluxo é decidido uma única vez: quem já tem conta paga depois;
  // quem NÃO tem é forçado a pagar o sinal dentro do fluxo de reserva.
  const [startedLogged] = useState(auth.logged);
  const STEPS = startedLogged
    ? ["Período", "Dados", "Confirmar"]
    : ["Período", "Dados", "Conta", "Pagamento"];

  const [step, setStep] = useState(0);
  const [pessoas, setPessoas] = useState(2);
  const [obs, setObs] = useState("");
  const horas = Number(sp.get("horas") ?? 2);
  const data = sp.get("data") ?? "2026-06-22";

  if (!m || !loja) return <p>Modelo não encontrado.</p>;
  const total = m.precoBaseHora * horas;
  const sinal = Math.round(total * 0.3);

  function novaReserva(
    sinalEstado: Reserva["sinal"],
    tipo: "sinal" | "total" = "sinal"
  ): string {
    const id = "RSV-" + Math.floor(2050 + Math.random() * 900);
    const reserva: Reserva = {
      id,
      modeloId: m!.id,
      data: `${data}T10:00:00`,
      duracaoHoras: horas,
      pessoas,
      valorEstimado: total,
      valorSinal: tipo === "total" ? total : sinal,
      pagamentoTipo: tipo,
      status: "PENDENTE",
      sinal: sinalEstado,
      habilitacao: "pendente",
      termos: "pendente",
    };
    addReserva(reserva);
    return id;
  }

  return (
    <div className="mx-auto max-w-2xl">
      <h1 className="text-2xl font-bold text-ink-900">Reservar {m.nome}</h1>

      {/* Stepper */}
      <ol className="mt-5 flex flex-wrap items-center gap-2 text-sm">
        {STEPS.map((s, i) => (
          <li key={s} className="flex items-center gap-2">
            <span
              className={`grid h-7 w-7 place-items-center rounded-full text-xs font-bold ${
                i <= step
                  ? "bg-brand-600 text-white"
                  : "bg-slate-200 text-slate-500"
              }`}
            >
              {i < step ? <CheckCircle2 size={15} /> : i + 1}
            </span>
            <span
              className={i <= step ? "font-medium text-ink-900" : "text-slate-400"}
            >
              {s}
            </span>
            {i < STEPS.length - 1 && (
              <ChevronRight size={14} className="text-slate-300" />
            )}
          </li>
        ))}
      </ol>

      <Card className="mt-6 p-6">
        {/* 0 — Período */}
        {step === 0 && (
          <div>
            <Head icon={<CalendarClock size={18} />} title="Confirme o período" />
            <div className="mt-4 grid gap-3 rounded-xl bg-slate-50 p-4 text-sm">
              <Info label="Modelo" value={m.nome} />
              <Info label="Loja" value={m.lojaNome} />
              <Info label="Data" value={fmtDate(`${data}T10:00:00`)} />
              <Info label="Duração" value={`${horas} hora(s)`} />
              <Info label="Valor estimado" value={brl(total)} />
            </div>
            <Button className="mt-5 w-full" onClick={() => setStep(1)}>
              Continuar
            </Button>
          </div>
        )}

        {/* 1 — Dados */}
        {step === 1 && (
          <div>
            <Head icon={<ClipboardList size={18} />} title="Dados da reserva" />
            <div className="mt-4 grid gap-3">
              <Field label="Número de pessoas">
                <select
                  className={inputCls}
                  value={pessoas}
                  onChange={(e) => setPessoas(Number(e.target.value))}
                >
                  {Array.from({ length: m.capacidadePessoas }, (_, i) => i + 1).map(
                    (n) => (
                      <option key={n} value={n}>
                        {n} pessoa(s)
                      </option>
                    )
                  )}
                </select>
              </Field>
              <Field
                label="Observações (opcional)"
                hint="Ex.: comemoração, necessidades especiais"
              >
                <textarea
                  className={inputCls + " h-24 py-2"}
                  value={obs}
                  onChange={(e) => setObs(e.target.value)}
                  placeholder="Algo que a loja deva saber?"
                />
              </Field>
            </div>
            <Button className="mt-5 w-full" onClick={() => setStep(2)}>
              Continuar
            </Button>
          </div>
        )}

        {/* 2A — Confirmar (usuário já logado: paga depois) */}
        {step === 2 && startedLogged && (
          <div>
            <Head icon={<CheckCircle2 size={18} />} title="Resumo e confirmação" />
            <ResumoBox m={m} data={data} horas={horas} pessoas={pessoas} sinal={sinal} total={total} />
            <div className="mt-4 rounded-xl border border-amber-200 bg-amber-50 p-3 text-xs text-amber-800">
              Após confirmar, você terá 3 passos na reserva: <b>pagar o sinal</b>,{" "}
              <b>resolver a habilitação</b> e <b>assinar os termos</b>.
            </div>
            <Button
              className="mt-5 w-full"
              size="lg"
              onClick={() => router.push(`/conta/reservas/${novaReserva("pendente")}`)}
            >
              Confirmar reserva
            </Button>
          </div>
        )}

        {/* 2B — Conta (novo cliente) */}
        {step === 2 && !startedLogged && (
          <div>
            <Head icon={<User size={18} />} title="Crie sua conta" />
            <p className="mt-1 text-sm text-slate-500">
              É rápido. Enviaremos um e-mail de confirmação (sua conta fica
              restrita até verificar). No próximo passo você garante a reserva
              pagando o sinal.
            </p>
            <div className="mt-4 grid gap-3">
              <Field label="Nome completo">
                <input className={inputCls} defaultValue="Marina Albuquerque" />
              </Field>
              <Field label="E-mail">
                <input className={inputCls} defaultValue="marina.alb@example.com" />
              </Field>
              <Field label="CPF">
                <input className={inputCls} defaultValue="123.456.789-00" />
              </Field>
              <Field label="Celular">
                <input className={inputCls} defaultValue="(21) 99876-5432" />
              </Field>
            </div>
            <Button
              className="mt-5 w-full"
              onClick={() => {
                signup("Marina Albuquerque");
                setStep(3);
              }}
            >
              Criar conta e ir para o pagamento <ChevronRight size={15} />
            </Button>
          </div>
        )}

        {/* 3 — Pagamento obrigatório (novo cliente) */}
        {step === 3 && !startedLogged && (
          <PagamentoObrigatorio
            m={m}
            loja={loja}
            data={data}
            horas={horas}
            pessoas={pessoas}
            sinal={sinal}
            total={total}
            onConcluir={(tipo) =>
              router.push(
                `/conta/reservas/${novaReserva("em_validacao", tipo)}`
              )
            }
          />
        )}
      </Card>
    </div>
  );
}

function PagamentoObrigatorio({
  m,
  loja,
  data,
  horas,
  pessoas,
  sinal,
  total,
  onConcluir,
}: {
  m: ReturnType<typeof getModelo>;
  loja: ReturnType<typeof getLoja>;
  data: string;
  horas: number;
  pessoas: number;
  sinal: number;
  total: number;
  onConcluir: (tipo: "sinal" | "total") => void;
}) {
  const [copiado, setCopiado] = useState(false);
  const [anexado, setAnexado] = useState(false);
  const [tipo, setTipo] = useState<"sinal" | "total">("sinal");
  if (!m || !loja) return null;

  const valorPagar = tipo === "sinal" ? sinal : total;

  function copiar() {
    navigator.clipboard?.writeText(loja!.pixChave).catch(() => {});
    setCopiado(true);
    setTimeout(() => setCopiado(false), 1500);
  }

  return (
    <div>
      <Head icon={<CreditCard size={18} />} title="Garanta sua reserva" />
      <p className="mt-1 text-sm text-slate-500">
        Escolha como pagar, faça o PIX e envie o comprovante. Sem pagamento a
        reserva não é garantida.
      </p>

      <ResumoBox m={m} data={data} horas={horas} pessoas={pessoas} sinal={sinal} total={total} />

      {/* Escolha: sinal ou total */}
      <div className="mt-4 grid grid-cols-2 gap-2">
        <button
          onClick={() => setTipo("sinal")}
          className={`rounded-xl border p-3 text-left ${
            tipo === "sinal"
              ? "border-brand-500 bg-brand-50"
              : "border-slate-200 hover:border-slate-300"
          }`}
        >
          <div className="text-sm font-semibold text-ink-900">Sinal (30%)</div>
          <div className="text-lg font-bold text-brand-700">{brl(sinal)}</div>
          <div className="text-xs text-slate-400">Restante no check-in</div>
        </button>
        <button
          onClick={() => setTipo("total")}
          className={`rounded-xl border p-3 text-left ${
            tipo === "total"
              ? "border-brand-500 bg-brand-50"
              : "border-slate-200 hover:border-slate-300"
          }`}
        >
          <div className="text-sm font-semibold text-ink-900">Valor total</div>
          <div className="text-lg font-bold text-brand-700">{brl(total)}</div>
          <div className="text-xs text-slate-400">Nada a pagar depois</div>
        </button>
      </div>

      <div className="mt-4 rounded-2xl border border-slate-200 p-4">
        <div className="flex items-center justify-between">
          <span className="text-sm text-slate-500">
            A pagar agora · {tipo === "sinal" ? "sinal" : "total"}
          </span>
          <span className="text-xl font-bold text-brand-700">
            {brl(valorPagar)}
          </span>
        </div>
        <div className="mt-4 flex flex-col items-center">
          <div className="grid h-36 w-36 place-items-center rounded-2xl border-2 border-dashed border-slate-300 bg-slate-50 text-slate-300">
            <QrCode size={96} strokeWidth={1} />
          </div>
          <p className="mt-2 text-xs text-slate-400">Aponte a câmera do seu banco</p>
        </div>
        <div className="mt-4">
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
        </div>
      </div>

      <label className="mt-4 flex cursor-pointer flex-col items-center gap-2 rounded-xl border-2 border-dashed border-slate-300 bg-slate-50 p-6 text-center hover:border-brand-400">
        <UploadCloud className="text-slate-400" size={26} />
        <span className="text-sm text-slate-600">
          {anexado ? "Comprovante anexado ✓" : "Anexar comprovante do PIX"}
        </span>
        <input type="file" className="hidden" onChange={() => setAnexado(true)} />
      </label>

      <Button
        className="mt-4 w-full"
        size="lg"
        disabled={!anexado}
        onClick={() => onConcluir(tipo)}
      >
        {anexado ? "Enviar comprovante e concluir reserva" : "Anexe o comprovante para concluir"}
      </Button>
      <p className="mt-2 flex items-center justify-center gap-1 text-center text-xs text-slate-400">
        <ShieldCheck size={13} /> Depois você resolve habilitação e termos na
        tela da reserva.
      </p>
    </div>
  );
}

function ResumoBox({
  m,
  data,
  horas,
  pessoas,
  sinal,
  total,
}: {
  m: ReturnType<typeof getModelo>;
  data: string;
  horas: number;
  pessoas: number;
  sinal: number;
  total: number;
}) {
  if (!m) return null;
  return (
    <div className="mt-4 space-y-2 rounded-xl bg-slate-50 p-4 text-sm">
      <Info label="Modelo" value={m.nome} />
      <Info label="Data" value={fmtDate(`${data}T10:00:00`)} />
      <Info label="Duração" value={`${horas}h · ${pessoas} pessoa(s)`} />
      <div className="my-1 border-t border-slate-200" />
      <Info label="Valor total" value={brl(total)} />
      <Info label="Sinal (30%)" value={brl(sinal)} accent />
      <Info label="Caução (reembolsável)" value={brl(m.caucao)} muted />
    </div>
  );
}

function Head({ icon, title }: { icon: React.ReactNode; title: string }) {
  return (
    <div className="flex items-center gap-2">
      <span className="grid h-9 w-9 place-items-center rounded-xl bg-brand-50 text-brand-700">
        {icon}
      </span>
      <h2 className="text-lg font-semibold text-ink-900">{title}</h2>
    </div>
  );
}

function Info({
  label,
  value,
  accent,
  muted,
}: {
  label: string;
  value: string;
  accent?: boolean;
  muted?: boolean;
}) {
  return (
    <div className="flex justify-between">
      <span className="text-slate-500">{label}</span>
      <span
        className={
          accent
            ? "font-semibold text-brand-700"
            : muted
            ? "text-slate-400"
            : "font-medium text-ink-900"
        }
      >
        {value}
      </span>
    </div>
  );
}

export default function Page() {
  return (
    <Suspense
      fallback={<div className="py-20 text-center text-slate-400">Carregando…</div>}
    >
      <Wizard />
    </Suspense>
  );
}
