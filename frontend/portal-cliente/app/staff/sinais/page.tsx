"use client";

import { useState } from "react";
import {
  Inbox,
  FileText,
  ImageIcon,
  CheckCircle2,
  XCircle,
  AlertTriangle,
  MailWarning,
  MailCheck,
  X,
  ExternalLink,
} from "lucide-react";
import { useStaff, type Sinal } from "@/lib/staff-store";
import { getModelo } from "@/lib/mock";
import { brl, fmtDateTime } from "@/lib/cn";
import { Card, Button, Badge, SectionTitle, inputCls } from "@/components/ui";

const MOTIVOS = [
  "Comprovante ilegível",
  "Valor insuficiente",
  "Pagamento não identificado",
  "Outro",
];

export default function SinaisPage() {
  const sinais = useStaff((s) => s.sinais);
  const [mostrarResolvidos, setMostrarResolvidos] = useState(false);
  const [aberto, setAberto] = useState<Sinal | null>(null);

  const fila = sinais.filter((s) =>
    mostrarResolvidos ? true : s.status === "em_analise"
  );
  const pendentes = sinais.filter((s) => s.status === "em_analise").length;

  return (
    <div>
      <div className="flex items-end justify-between">
        <SectionTitle sub="Comprovantes de PIX (sinal ou total) enviados pelos clientes no portal · balcão não entra aqui">
          Pagamentos a validar
        </SectionTitle>
        <label className="mb-5 flex items-center gap-2 text-sm text-slate-500">
          <input
            type="checkbox"
            checked={mostrarResolvidos}
            onChange={(e) => setMostrarResolvidos(e.target.checked)}
          />
          Mostrar resolvidos
        </label>
      </div>

      <Card className="overflow-hidden">
        <div className="flex items-center gap-2 border-b border-slate-100 bg-slate-50 px-4 py-2 text-sm font-medium text-slate-600">
          <Inbox size={15} /> {pendentes} aguardando análise
        </div>

        {fila.length === 0 ? (
          <div className="flex flex-col items-center gap-2 py-16 text-center text-slate-400">
            <CheckCircle2 size={36} className="text-emerald-400" />
            Tudo validado!
          </div>
        ) : (
          <div className="divide-y divide-slate-100">
            {/* cabeçalho (desktop) */}
            <div className="hidden grid-cols-[1.4fr_1fr_0.8fr_0.8fr_0.7fr_auto] gap-3 px-4 py-2 text-xs font-semibold uppercase tracking-wide text-slate-400 md:grid">
              <span>Cliente / Reserva</span>
              <span>Modelo / Data</span>
              <span>Esperado</span>
              <span>Informado</span>
              <span>Compr.</span>
              <span>Ação</span>
            </div>
            {fila.map((s) => {
              const m = getModelo(s.modeloId);
              const diverge = s.valorInformado !== s.valorEsperado;
              return (
                <div
                  key={s.id}
                  className="grid grid-cols-2 items-center gap-3 px-4 py-3 md:grid-cols-[1.4fr_1fr_0.8fr_0.8fr_0.7fr_auto]"
                >
                  <div className="col-span-2 md:col-span-1">
                    <div className="flex items-center gap-2">
                      <span className="font-medium text-ink-900">
                        {s.clienteNome}
                      </span>
                      {s.emailVerificado ? (
                        <MailCheck size={14} className="text-emerald-500" />
                      ) : (
                        <MailWarning size={14} className="text-amber-500" />
                      )}
                    </div>
                    <div className="flex items-center gap-2">
                      <span className="font-mono text-xs text-slate-400">{s.id}</span>
                      <Badge tone={s.tipo === "total" ? "brand" : "slate"}>
                        {s.tipo === "total" ? "Total" : "Sinal"}
                      </Badge>
                    </div>
                  </div>
                  <div className="text-sm text-slate-600">
                    {m?.nome}
                    <div className="text-xs text-slate-400">
                      {fmtDateTime(s.dataISO)}
                    </div>
                  </div>
                  <div className="text-sm font-medium text-ink-900">
                    {brl(s.valorEsperado)}
                  </div>
                  <div
                    className={`text-sm font-medium ${
                      diverge ? "text-rose-600" : "text-ink-900"
                    }`}
                  >
                    {brl(s.valorInformado)}
                    {diverge && <AlertTriangle size={12} className="ml-1 inline" />}
                  </div>
                  <div className="text-slate-400">
                    {s.comprovante === "pdf" ? (
                      <FileText size={18} />
                    ) : (
                      <ImageIcon size={18} />
                    )}
                  </div>
                  <div className="col-span-2 md:col-span-1">
                    {s.status === "em_analise" ? (
                      <Button size="sm" onClick={() => setAberto(s)}>
                        Revisar
                      </Button>
                    ) : s.status === "confirmado" ? (
                      <Badge tone="green">Confirmado</Badge>
                    ) : (
                      <Badge tone="red">Recusado</Badge>
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </Card>

      {aberto && <RevisaoModal sinal={aberto} onClose={() => setAberto(null)} />}
    </div>
  );
}

function RevisaoModal({ sinal, onClose }: { sinal: Sinal; onClose: () => void }) {
  const confirmar = useStaff((s) => s.confirmar);
  const recusar = useStaff((s) => s.recusar);
  const m = getModelo(sinal.modeloId);

  const [valor, setValor] = useState(sinal.valorInformado);
  const [modoRecusa, setModoRecusa] = useState(false);
  const [motivo, setMotivo] = useState(MOTIVOS[0]);

  const diverge = valor !== sinal.valorEsperado;

  return (
    <div className="fixed inset-0 z-50 flex items-end justify-center bg-ink-900/50 p-0 sm:items-center sm:p-4">
      <div className="max-h-[92vh] w-full max-w-3xl overflow-auto rounded-t-2xl bg-white sm:rounded-2xl">
        <div className="flex items-center justify-between border-b border-slate-100 px-5 py-3">
          <h2 className="font-semibold text-ink-900">
            Revisar pagamento ·{" "}
            <span className="font-mono text-sm">{sinal.id}</span>
            <span className="ml-2">
              <Badge tone={sinal.tipo === "total" ? "brand" : "slate"}>
                {sinal.tipo === "total" ? "Total" : "Sinal"}
              </Badge>
            </span>
          </h2>
          <button onClick={onClose} className="rounded-lg p-1 hover:bg-slate-100">
            <X size={18} />
          </button>
        </div>

        <div className="grid gap-5 p-5 md:grid-cols-[1fr_1fr]">
          {/* Comprovante */}
          <div>
            <span className="text-xs font-medium text-slate-500">Comprovante</span>
            <div className="mt-1 grid h-64 place-items-center rounded-xl border border-slate-200 bg-slate-50 text-slate-300">
              {sinal.comprovante === "pdf" ? (
                <div className="flex flex-col items-center gap-2">
                  <FileText size={56} strokeWidth={1} />
                  <span className="text-sm text-slate-400">comprovante.pdf</span>
                </div>
              ) : (
                <div className="flex flex-col items-center gap-2">
                  <ImageIcon size={56} strokeWidth={1} />
                  <span className="text-sm text-slate-400">comprovante.jpg</span>
                </div>
              )}
            </div>
            <button className="mt-2 inline-flex items-center gap-1 text-xs text-brand-600 hover:underline">
              <ExternalLink size={12} /> Abrir em nova aba
            </button>
          </div>

          {/* Dados + ação */}
          <div>
            <div className="space-y-1.5 rounded-xl bg-slate-50 p-4 text-sm">
              <Row label="Cliente" value={sinal.clienteNome} />
              <Row
                label="E-mail"
                value={sinal.emailVerificado ? "verificado ✓" : "não verificado ⚠"}
                tone={sinal.emailVerificado ? "ok" : "warn"}
              />
              <Row label="Modelo" value={m?.nome ?? ""} />
              <Row label="Data" value={fmtDateTime(sinal.dataISO)} />
              <Row
                label={sinal.tipo === "total" ? "Total esperado" : "Sinal esperado"}
                value={brl(sinal.valorEsperado)}
              />
              <Row label="Valor informado" value={brl(sinal.valorInformado)} />
            </div>

            {!sinal.emailVerificado && (
              <p className="mt-2 flex items-start gap-1.5 text-xs text-amber-700">
                <MailWarning size={14} className="mt-px shrink-0" /> E-mail não
                verificado: a reserva só fica garantida após o cliente confirmar o
                e-mail.
              </p>
            )}

            {!modoRecusa ? (
              <>
                <label className="mt-4 block text-sm font-medium text-slate-700">
                  Valor recebido
                </label>
                <input
                  type="number"
                  className={inputCls + " mt-1"}
                  value={valor}
                  onChange={(e) => setValor(Number(e.target.value))}
                />
                {diverge && (
                  <p className="mt-1 flex items-center gap-1 text-xs text-rose-600">
                    <AlertTriangle size={13} /> Diverge do esperado (
                    {brl(valor - sinal.valorEsperado)})
                  </p>
                )}
                <p className="mt-2 flex items-center gap-1 text-xs text-amber-700">
                  <AlertTriangle size={13} /> Capacidade: 2/3 garantidas neste
                  horário
                </p>

                <div className="mt-4 flex gap-2">
                  <Button
                    variant="outline"
                    className="flex-1 border-rose-200 text-rose-700 hover:bg-rose-50"
                    onClick={() => setModoRecusa(true)}
                  >
                    <XCircle size={16} /> Recusar
                  </Button>
                  <Button
                    className="flex-1"
                    onClick={() => {
                      confirmar(sinal.id, valor);
                      onClose();
                    }}
                  >
                    <CheckCircle2 size={16} /> Confirmar sinal
                  </Button>
                </div>
              </>
            ) : (
              <div className="mt-4">
                <label className="block text-sm font-medium text-slate-700">
                  Motivo da recusa
                </label>
                <select
                  className={inputCls + " mt-1"}
                  value={motivo}
                  onChange={(e) => setMotivo(e.target.value)}
                >
                  {MOTIVOS.map((mo) => (
                    <option key={mo}>{mo}</option>
                  ))}
                </select>
                <textarea
                  className={inputCls + " mt-2 h-20 py-2"}
                  placeholder="Detalhe (opcional) — vai para o cliente reenviar"
                />
                <div className="mt-3 flex gap-2">
                  <Button
                    variant="ghost"
                    className="flex-1"
                    onClick={() => setModoRecusa(false)}
                  >
                    Voltar
                  </Button>
                  <Button
                    className="flex-1 bg-rose-600 hover:bg-rose-700"
                    onClick={() => {
                      recusar(sinal.id, motivo);
                      onClose();
                    }}
                  >
                    Confirmar recusa
                  </Button>
                </div>
              </div>
            )}
            <p className="mt-3 text-center text-xs text-slate-400">
              Ação auditada (operador, data/hora, IP). Cliente é notificado por
              e-mail.
            </p>
          </div>
        </div>
      </div>
    </div>
  );
}

function Row({
  label,
  value,
  tone,
}: {
  label: string;
  value: string;
  tone?: "ok" | "warn";
}) {
  return (
    <div className="flex justify-between">
      <span className="text-slate-500">{label}</span>
      <span
        className={
          tone === "ok"
            ? "font-medium text-emerald-600"
            : tone === "warn"
            ? "font-medium text-amber-600"
            : "font-medium text-ink-900"
        }
      >
        {value}
      </span>
    </div>
  );
}
