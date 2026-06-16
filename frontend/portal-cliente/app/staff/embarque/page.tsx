"use client";

import { useState } from "react";
import {
  Search,
  UserPlus,
  FolderUp,
  CreditCard,
  Landmark,
  FileSignature,
  FileCheck2,
  Download,
  Printer,
  CheckCircle2,
  ChevronRight,
  Mail,
  LifeBuoy,
  ShieldCheck,
  UploadCloud,
} from "lucide-react";
import { MODELOS, getModelo } from "@/lib/mock";
import { brl } from "@/lib/cn";
import { Button, Card, Field, inputCls, Badge } from "@/components/ui";
import { SignaturePad } from "@/components/SignaturePad";
import { AddressForm } from "@/components/AddressForm";

const STEPS = [
  "Cliente",
  "Documentos",
  "Aluguel",
  "Habilitação",
  "Termos",
  "Emissão",
];

export default function BalcaoPage() {
  const [step, setStep] = useState(0);
  const [temCHA, setTemCHA] = useState<boolean | null>(null);
  const next = () => setStep((s) => Math.min(s + 1, STEPS.length - 1));

  return (
    <div className="mx-auto max-w-2xl">
      <h1 className="flex items-center gap-2 text-2xl font-bold text-ink-900">
        <UserPlus className="text-brand-600" /> Atendimento de balcão
      </h1>
      <p className="mt-1 text-sm text-slate-500">
        Registro, documentos, pagamento, GRU/CHA-MTA-E e termos — gera os
        documentos da Marinha. <b>O check-in/embarque é feito à parte</b>, na hora
        do passeio.
      </p>

      {/* Stepper */}
      <ol className="mt-5 flex flex-wrap items-center gap-x-2 gap-y-1 text-sm">
        {STEPS.map((s, i) => (
          <li key={s} className="flex items-center gap-1.5">
            <span
              className={`grid h-6 w-6 place-items-center rounded-full text-xs font-bold ${
                i < step
                  ? "bg-emerald-500 text-white"
                  : i === step
                  ? "bg-brand-600 text-white"
                  : "bg-slate-200 text-slate-500"
              }`}
            >
              {i < step ? <CheckCircle2 size={13} /> : i + 1}
            </span>
            <span className={i <= step ? "text-ink-900" : "text-slate-400"}>
              {s}
            </span>
            {i < STEPS.length - 1 && (
              <ChevronRight size={13} className="text-slate-300" />
            )}
          </li>
        ))}
      </ol>

      <Card className="mt-6 p-6">
        {step === 0 && <StepCliente onNext={next} />}
        {step === 1 && <StepDocumentos onNext={next} onTemCHA={setTemCHA} />}
        {step === 2 && <StepAluguel onNext={next} />}
        {step === 3 && <StepHabilitacao temCHA={temCHA} onNext={next} />}
        {step === 4 && <StepTermos onNext={next} />}
        {step === 5 && <StepEmissao temCHA={temCHA} />}
      </Card>
    </div>
  );
}

function Head({
  icon,
  title,
  sub,
}: {
  icon: React.ReactNode;
  title: string;
  sub?: string;
}) {
  return (
    <div className="mb-4">
      <div className="flex items-center gap-2">
        <span className="grid h-9 w-9 place-items-center rounded-xl bg-brand-50 text-brand-700">
          {icon}
        </span>
        <h2 className="text-lg font-semibold text-ink-900">{title}</h2>
      </div>
      {sub && <p className="mt-1 text-sm text-slate-500">{sub}</p>}
    </div>
  );
}

/* 0 — Registro do cliente (pré-conta) */
function StepCliente({ onNext }: { onNext: () => void }) {
  const [buscou, setBuscou] = useState(false);
  return (
    <div>
      <Head
        icon={<Search size={18} />}
        title="Registro do cliente"
        sub="Busque pelo CPF. Se não existir, cria-se uma pré-conta (origem: balcão)."
      />
      <div className="flex gap-2">
        <input className={inputCls} placeholder="CPF do cliente" defaultValue="987.654.321-00" />
        <Button variant="outline" onClick={() => setBuscou(true)}>
          <Search size={16} /> Buscar
        </Button>
      </div>

      {buscou && (
        <div className="mt-4">
          <div className="rounded-xl border border-amber-200 bg-amber-50 p-3 text-sm text-amber-800">
            Nenhum cliente encontrado — criando <b>pré-conta</b>.
          </div>
          <div className="mt-4 grid gap-3 sm:grid-cols-2">
            <Field label="Nome completo">
              <input className={inputCls} defaultValue="Roberto Lima" />
            </Field>
            <Field label="CPF">
              <input className={inputCls} defaultValue="987.654.321-00" />
            </Field>
            <Field label="E-mail (opcional)">
              <input className={inputCls} defaultValue="roberto.lima@email.com" />
            </Field>
            <Field label="Celular (opcional)">
              <input className={inputCls} defaultValue="(21) 98888-1234" />
            </Field>
          </div>
          <p className="mt-2 text-xs text-slate-400">
            Dedupe por CPF aplicado. Se houver conta existente, exige verificação (OTP).
          </p>
          <Button className="mt-4 w-full" onClick={onNext}>
            Criar pré-conta e continuar <ChevronRight size={15} />
          </Button>
        </div>
      )}
    </div>
  );
}

/* 1 — Coleta de documentos + triagem de habilitação */
function StepDocumentos({
  onNext,
  onTemCHA,
}: {
  onNext: () => void;
  onTemCHA: (v: boolean) => void;
}) {
  const [tem, setTem] = useState<boolean | null>(null);
  const [resModo, setResModo] = useState<"tem" | "declarar" | null>(null);
  return (
    <div>
      <Head
        icon={<FolderUp size={18} />}
        title="Coleta de documentos"
        sub="Digitalize os documentos do cliente."
      />
      <div className="grid gap-2 sm:grid-cols-3">
        {["RG / CNH ou passaporte", "CPF", "Foto do cliente"].map((d) => (
          <label
            key={d}
            className="flex cursor-pointer items-center gap-2 rounded-xl border-2 border-dashed border-slate-300 bg-slate-50 px-3 py-3 text-sm text-slate-600 hover:border-brand-400"
          >
            <UploadCloud size={18} className="text-slate-400" /> {d}
            <input type="file" className="hidden" />
          </label>
        ))}
      </div>

      {/* Comprovante de residência (Anexo 1-C com CEP autofill) */}
      <div className="mt-5 rounded-xl border border-slate-200 p-4">
        <h3 className="font-medium text-ink-900">Comprovante de residência</h3>
        <div className="mt-2 flex flex-wrap gap-2">
          <button
            onClick={() => setResModo("tem")}
            className={`rounded-lg border px-3 py-1.5 text-sm ${
              resModo === "tem"
                ? "border-brand-500 bg-brand-50 text-brand-700"
                : "border-slate-200 text-slate-600"
            }`}
          >
            Tem comprovante — anexar
          </button>
          <button
            onClick={() => setResModo("declarar")}
            className={`rounded-lg border px-3 py-1.5 text-sm ${
              resModo === "declarar"
                ? "border-brand-500 bg-brand-50 text-brand-700"
                : "border-slate-200 text-slate-600"
            }`}
          >
            Não tem — declarar (Anexo 1-C)
          </button>
        </div>

        {resModo === "tem" && (
          <label className="mt-3 flex cursor-pointer items-center gap-2 rounded-lg border-2 border-dashed border-slate-300 bg-slate-50 px-3 py-2 text-sm text-slate-600">
            <UploadCloud size={16} className="text-slate-400" /> Anexar comprovante
            <input type="file" className="hidden" />
          </label>
        )}
        {resModo === "declarar" && (
          <div className="mt-3">
            <p className="mb-3 text-xs text-slate-500">
              Declaração de Residência (Lei 7.115/83). Informe o CEP para
              preencher automaticamente.
            </p>
            <AddressForm />
          </div>
        )}
      </div>

      <div className="mt-5 rounded-xl border border-slate-200 p-4">
        <h3 className="font-medium text-ink-900">
          O cliente possui habilitação náutica?
        </h3>
        <p className="text-xs text-slate-500">Arrais Amador, Motonauta ou CHA.</p>
        <div className="mt-2 flex gap-2">
          <button
            onClick={() => {
              setTem(true);
              onTemCHA(true);
            }}
            className={`rounded-lg border px-3 py-1.5 text-sm ${
              tem === true
                ? "border-brand-500 bg-brand-50 text-brand-700"
                : "border-slate-200 text-slate-600"
            }`}
          >
            Sim — digitalizar CHA
          </button>
          <button
            onClick={() => {
              setTem(false);
              onTemCHA(false);
            }}
            className={`rounded-lg border px-3 py-1.5 text-sm ${
              tem === false
                ? "border-brand-500 bg-brand-50 text-brand-700"
                : "border-slate-200 text-slate-600"
            }`}
          >
            Não — emitir CHA-MTA-E
          </button>
        </div>
        {tem === true && (
          <label className="mt-3 flex cursor-pointer items-center gap-2 rounded-lg border-2 border-dashed border-slate-300 bg-slate-50 px-3 py-2 text-sm text-slate-600">
            <UploadCloud size={16} className="text-slate-400" /> Anexar CHA (frente/verso)
            <input type="file" className="hidden" />
          </label>
        )}
        {tem === false && (
          <p className="mt-3 text-xs text-amber-700">
            Sem habilitação → será emitida a <b>CHA-MTA-E</b> (com GRU) no passo
            Habilitação.
          </p>
        )}
      </div>

      <Button
        className="mt-4 w-full"
        disabled={tem === null || resModo === null}
        onClick={onNext}
      >
        Continuar <ChevronRight size={15} />
      </Button>
    </div>
  );
}

/* 2 — Aluguel + pagamento (valor total, sem sinal no balcão) */
function StepAluguel({ onNext }: { onNext: () => void }) {
  const [modeloId, setModeloId] = useState(MODELOS[0].id);
  const [horas, setHoras] = useState(2);
  const [forma, setForma] = useState("Dinheiro");
  const [pago, setPago] = useState(false);
  const m = getModelo(modeloId)!;
  const total = m.precoBaseHora * horas;

  return (
    <div>
      <Head
        icon={<CreditCard size={18} />}
        title="Aluguel e pagamento"
        sub="No balcão a reserva é paga integralmente (valor total, sem sinal)."
      />
      <div className="grid gap-3 sm:grid-cols-2">
        <Field label="Modelo">
          <select className={inputCls} value={modeloId} onChange={(e) => setModeloId(e.target.value)}>
            {MODELOS.map((mm) => (
              <option key={mm.id} value={mm.id}>
                {mm.nome} — {brl(mm.precoBaseHora)}/h
              </option>
            ))}
          </select>
        </Field>
        <Field label="Duração">
          <select className={inputCls} value={horas} onChange={(e) => setHoras(Number(e.target.value))}>
            {[1, 2, 3, 4].map((h) => (
              <option key={h} value={h}>
                {h} hora(s)
              </option>
            ))}
          </select>
        </Field>
      </div>
      <div className="mt-3 flex justify-between rounded-xl bg-slate-50 p-3 text-sm">
        <span className="text-slate-500">Valor total do aluguel</span>
        <b className="text-ink-900">{brl(total)}</b>
      </div>

      {pago ? (
        <div className="mt-4 rounded-xl bg-emerald-50 p-4 text-sm text-emerald-800">
          <div className="flex items-center gap-2 font-semibold">
            <CheckCircle2 size={16} /> Aluguel pago · {forma} · {brl(total)}
          </div>
          <Button className="mt-3 w-full" onClick={onNext}>
            Continuar <ChevronRight size={15} />
          </Button>
        </div>
      ) : (
        <>
          <div className="mt-4 grid grid-cols-3 gap-2">
            {["Dinheiro", "PIX", "Maquininha"].map((f) => (
              <button
                key={f}
                onClick={() => setForma(f)}
                className={`rounded-xl border p-3 text-sm font-medium ${
                  forma === f
                    ? "border-brand-500 bg-brand-50 text-brand-700"
                    : "border-slate-200 text-slate-600 hover:border-slate-300"
                }`}
              >
                {f}
              </button>
            ))}
          </div>
          <Button className="mt-4 w-full" onClick={() => setPago(true)}>
            Registrar pagamento do aluguel (valor total)
          </Button>
        </>
      )}
    </div>
  );
}

/* 3 — Habilitação: CHA já coletada OU emissão CHA-MTA-E (GRU) */
function StepHabilitacao({
  temCHA,
  onNext,
}: {
  temCHA: boolean | null;
  onNext: () => void;
}) {
  if (temCHA) {
    return (
      <div>
        <Head icon={<LifeBuoy size={18} />} title="Habilitação" />
        <div className="rounded-xl bg-emerald-50 p-4 text-sm text-emerald-800">
          <div className="flex items-center gap-2 font-semibold">
            <CheckCircle2 size={16} /> Cliente já habilitado
          </div>
          <p className="mt-1 text-xs">
            CHA coletada nos documentos. <b>GRU não é necessária.</b>
          </p>
        </div>
        <Button className="mt-4 w-full" onClick={onNext}>
          Continuar <ChevronRight size={15} />
        </Button>
      </div>
    );
  }
  return <EmissaoCHA onNext={onNext} />;
}

function EmissaoCHA({ onNext }: { onNext: () => void }) {
  const [gruGerada, setGruGerada] = useState(false);
  const [gruPaga, setGruPaga] = useState(false);
  return (
    <div>
      <Head
        icon={<Landmark size={18} />}
        title="Emissão da CHA-MTA-E"
        sub="Habilitação especial para quem não tem Arrais/Motonauta."
      />
      <div className="space-y-2 text-sm">
        {["Videoaula da Marinha assistida no balcão", "Anexos 5-C / 5-B / 1-C preenchidos"].map(
          (t) => (
            <label key={t} className="flex items-center gap-2 rounded-lg bg-slate-50 px-3 py-2">
              <input type="checkbox" defaultChecked /> {t}
            </label>
          )
        )}
      </div>

      {/* GRU */}
      <div className="mt-4 rounded-xl border border-slate-200 p-4">
        <div className="flex items-center justify-between">
          <span className="font-medium text-ink-900">GRU (taxa da Marinha)</span>
          {gruPaga ? (
            <Badge tone="green">Paga</Badge>
          ) : gruGerada ? (
            <Badge tone="amber">Gerada</Badge>
          ) : (
            <Badge tone="slate">Pendente</Badge>
          )}
        </div>
        {!gruGerada ? (
          <Button variant="outline" className="mt-3 w-full" onClick={() => setGruGerada(true)}>
            Gerar GRU
          </Button>
        ) : (
          <div className="mt-3">
            <div className="flex justify-between text-sm">
              <span className="text-slate-500">Valor / Nº GRU</span>
              <b className="font-mono text-ink-900">R$ 23,13 · 2026-000482-19</b>
            </div>
            {!gruPaga ? (
              <Button className="mt-3 w-full" onClick={() => setGruPaga(true)}>
                Registrar pagamento da GRU
              </Button>
            ) : (
              <p className="mt-2 text-xs text-emerald-700">
                ✓ GRU paga — a guia será entregue ao cliente na emissão final.
              </p>
            )}
          </div>
        )}
      </div>

      <div className="mt-3 flex items-start gap-2 rounded-xl bg-brand-50 p-3 text-sm text-brand-900">
        <LifeBuoy size={16} className="mt-0.5 shrink-0" />
        <span>
          A <b>demonstração prática</b> (Atestado 5-B) é feita pelo instrutor no
          <b> check-in/embarque</b> (à parte).
        </span>
      </div>

      <Button className="mt-4 w-full" disabled={!gruPaga} onClick={onNext}>
        Continuar <ChevronRight size={15} />
      </Button>
    </div>
  );
}

/* 4 — Termos + signature pad */
function StepTermos({ onNext }: { onNext: () => void }) {
  const [assinado, setAssinado] = useState(false);
  return (
    <div>
      <Head
        icon={<FileSignature size={18} />}
        title="Assinatura dos termos"
        sub="Assinatura presencial no balcão (signature pad)."
      />
      <div className="max-h-32 overflow-auto rounded-xl bg-slate-50 p-3 text-xs text-slate-600">
        Eu, Roberto Lima, declaro que recebi orientações de segurança e assumo
        total responsabilidade pelo equipamento; ciente dos custos por
        emborcamento (R$ 400–2.000), das normas da Autoridade Marítima e de que
        possuo condições físicas/psicológicas e não estou sob efeito de álcool ou
        drogas.
      </div>
      <div className="mt-4">
        <SignaturePad onChange={setAssinado} />
      </div>
      <Button className="mt-4 w-full" disabled={!assinado} onClick={onNext}>
        {assinado ? "Confirmar assinatura e continuar" : "Aguardando assinatura…"}
      </Button>
      <p className="mt-2 text-center text-xs text-slate-400">
        Evidências: operador, data/hora, IP/dispositivo, hash SHA-256, origem=BALCAO.
      </p>
    </div>
  );
}

/* 5 — Emissão do PDF consolidado + envio Marinha + e-mail + saída da GRU */
function StepEmissao({ temCHA }: { temCHA: boolean | null }) {
  const [emitido, setEmitido] = useState(false);

  if (!emitido)
    return (
      <div>
        <Head
          icon={<FileCheck2 size={18} />}
          title="Emitir documentos"
          sub="Gera o PDF consolidado e dispara os envios."
        />
        <ul className="space-y-2 rounded-xl bg-slate-50 p-4 text-sm text-slate-600">
          <li className="flex gap-2">
            <CheckCircle2 size={16} className="mt-0.5 shrink-0 text-emerald-500" />
            Documentos coletados (RG/CPF/residência)
          </li>
          <li className="flex gap-2">
            <CheckCircle2 size={16} className="mt-0.5 shrink-0 text-emerald-500" />
            {temCHA ? "CHA do cliente" : "Anexos NORMAM (5-C/5-B/1-C) + GRU paga"}
          </li>
          <li className="flex gap-2">
            <CheckCircle2 size={16} className="mt-0.5 shrink-0 text-emerald-500" />
            Termo de responsabilidade assinado
          </li>
        </ul>
        <Button className="mt-4 w-full" size="lg" onClick={() => setEmitido(true)}>
          Emitir PDF e enviar
        </Button>
      </div>
    );

  return (
    <div>
      <div className="flex flex-col items-center gap-2 text-center">
        <CheckCircle2 className="text-emerald-500" size={44} />
        <h2 className="text-lg font-semibold text-ink-900">
          Documentos emitidos!
        </h2>
        <p className="max-w-sm text-sm text-slate-500">
          PDF consolidado (documentos + termos + assinatura) gerado e enviado.
        </p>
      </div>

      <div className="mt-5 space-y-3">
        <Artefato
          icon={<FileCheck2 size={18} />}
          titulo="PDF consolidado"
          desc="Documentos + termos + assinatura"
          acao={
            <a
              href="/staff/documento"
              target="_blank"
              rel="noopener noreferrer"
              className="inline-flex h-9 items-center gap-2 rounded-xl border border-slate-300 bg-white px-3 text-sm font-medium text-slate-800 hover:bg-slate-50"
            >
              <Download size={14} /> Abrir
            </a>
          }
        />
        <Status texto="Enviado à Marinha (automático)" />
        <Status texto="E-mail enviado ao cliente (PDF + link de ativação da conta)" />
        {!temCHA && (
          <Artefato
            icon={<Landmark size={18} />}
            titulo="GRU — guia de recolhimento"
            desc="Entregar/imprimir para o cliente"
            acao={
              <Button size="sm" variant="outline">
                <Printer size={14} /> Imprimir
              </Button>
            }
          />
        )}
      </div>

      <div className="mt-4 flex items-center justify-center gap-1 text-center text-xs text-slate-400">
        <ShieldCheck size={13} /> Check-in/embarque é feito à parte, na hora do passeio.
      </div>
      <Button href="/staff" variant="outline" className="mt-4 w-full">
        Concluir atendimento
      </Button>
    </div>
  );
}

function Artefato({
  icon,
  titulo,
  desc,
  acao,
}: {
  icon: React.ReactNode;
  titulo: string;
  desc: string;
  acao: React.ReactNode;
}) {
  return (
    <div className="flex items-center gap-3 rounded-xl border border-slate-200 p-3">
      <span className="grid h-10 w-10 shrink-0 place-items-center rounded-xl bg-slate-100 text-slate-600">
        {icon}
      </span>
      <div className="min-w-0 flex-1">
        <div className="font-medium text-ink-900">{titulo}</div>
        <div className="text-xs text-slate-500">{desc}</div>
      </div>
      {acao}
    </div>
  );
}

function Status({ texto }: { texto: string }) {
  return (
    <div className="flex items-center gap-2 rounded-xl bg-emerald-50 px-3 py-2 text-sm text-emerald-800">
      <CheckCircle2 size={16} className="shrink-0" /> {texto}
    </div>
  );
}
