"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useState } from "react";
import {
  IdCard,
  PlayCircle,
  FileCheck2,
  UploadCloud,
  Landmark,
  LifeBuoy,
  CheckCircle2,
  Clock,
  ChevronRight,
  Award,
} from "lucide-react";
import { useStore } from "@/lib/store";
import { Button, Card, Field, inputCls, Badge } from "@/components/ui";
import { AddressForm } from "@/components/AddressForm";
import { brl } from "@/lib/cn";

type Via = null | "tem" | "nao";

export default function HabilitacaoPage() {
  const { id } = useParams<{ id: string }>();
  const reserva = useStore((s) => s.reservas.find((r) => r.id === id));
  const setEtapa = useStore((s) => s.setEtapa);
  const [via, setVia] = useState<Via>(null);

  if (!reserva) return <p>Reserva não encontrada.</p>;

  return (
    <div className="mx-auto max-w-2xl">
      <Link
        href={`/conta/reservas/${id}`}
        className="text-sm text-slate-400 hover:text-slate-600"
      >
        ← Voltar para a reserva
      </Link>
      <h1 className="mt-3 flex items-center gap-2 text-2xl font-bold text-ink-900">
        <IdCard className="text-brand-600" /> Habilitação náutica
      </h1>

      {reserva.habilitacao === "ok" ? (
        <Card className="mt-6 flex flex-col items-center gap-3 p-8 text-center">
          <Award className="text-emerald-500" size={44} />
          <h2 className="text-lg font-semibold text-ink-900">
            Habilitação válida
          </h2>
          <p className="text-sm text-slate-500">
            Tudo certo para conduzir. A demonstração prática de segurança será
            feita no embarque.
          </p>
          <Button href={`/conta/reservas/${id}`} variant="outline">
            Voltar para a reserva
          </Button>
        </Card>
      ) : (
        <>
          {/* Triagem */}
          <Card className="mt-6 p-6">
            <h2 className="font-semibold text-ink-900">
              Você possui habilitação náutica?
            </h2>
            <p className="mt-1 text-sm text-slate-500">
              Arrais Amador, Motonauta ou CHA (ARA/MSA/CPA/MTA-E).
            </p>
            <div className="mt-4 grid gap-3 sm:grid-cols-2">
              <button
                onClick={() => setVia("tem")}
                className={`rounded-xl border p-4 text-left transition ${
                  via === "tem"
                    ? "border-brand-500 bg-brand-50"
                    : "border-slate-200 hover:border-slate-300"
                }`}
              >
                <FileCheck2 className="text-brand-600" />
                <h3 className="mt-2 font-semibold text-ink-900">Sim, eu tenho</h3>
                <p className="text-sm text-slate-500">
                  Envio o documento para validação.
                </p>
              </button>
              <button
                onClick={() => setVia("nao")}
                className={`rounded-xl border p-4 text-left transition ${
                  via === "nao"
                    ? "border-brand-500 bg-brand-50"
                    : "border-slate-200 hover:border-slate-300"
                }`}
              >
                <LifeBuoy className="text-brand-600" />
                <h3 className="mt-2 font-semibold text-ink-900">
                  Não tenho
                </h3>
                <p className="text-sm text-slate-500">
                  Emitir habilitação especial <b>CHA-MTA-E</b>.
                </p>
              </button>
            </div>
          </Card>

          {via === "tem" && <CaminhoA reservaId={id} onDone={() => setEtapa(id, "habilitacao", "ok")} />}
          {via === "nao" && <CaminhoB reservaId={id} onDone={() => setEtapa(id, "habilitacao", "ok")} />}
        </>
      )}
    </div>
  );
}

/* ---------- Caminho A: já possui habilitação ---------- */
function CaminhoA({ onDone }: { reservaId: string; onDone: () => void }) {
  const [enviado, setEnviado] = useState(false);
  return (
    <Card className="mt-4 p-6">
      <h3 className="font-semibold text-ink-900">Envie sua habilitação</h3>
      {enviado ? (
        <div className="mt-4 rounded-xl bg-amber-50 p-4 text-sm text-amber-800">
          <div className="flex items-center gap-2 font-semibold">
            <Clock size={16} /> Documento em validação
          </div>
          <p className="mt-1 text-xs">A loja confere a autenticidade da CHA.</p>
          <button
            onClick={onDone}
            className="mt-3 w-full rounded-lg border border-amber-300 bg-white py-2 text-xs font-medium text-amber-800 hover:bg-amber-100"
          >
            ▶︎ Simular aprovação do staff (demo)
          </button>
        </div>
      ) : (
        <div className="mt-4 grid gap-3">
          <Field label="Categoria">
            <select className={inputCls}>
              <option>Arrais Amador</option>
              <option>Motonauta</option>
              <option>Capitão Amador</option>
              <option>Mestre Amador</option>
            </select>
          </Field>
          <Field label="Número da habilitação">
            <input className={inputCls} placeholder="Ex.: 1234567890" />
          </Field>
          <label className="flex cursor-pointer flex-col items-center gap-2 rounded-xl border-2 border-dashed border-slate-300 bg-slate-50 p-6 text-center hover:border-brand-400">
            <UploadCloud className="text-slate-400" size={26} />
            <span className="text-sm text-slate-600">
              Anexar foto/PDF da CHA (frente e verso)
            </span>
            <input type="file" className="hidden" />
          </label>
          <Button className="w-full" onClick={() => setEnviado(true)}>
            Enviar para validação
          </Button>
        </div>
      )}
    </Card>
  );
}

/* ---------- Caminho B: emissão da CHA-MTA-E (EMA) ---------- */
const ETAPAS_B = [
  "Videoaula da Marinha",
  "Auto-declarações (Anexos)",
  "Documentos",
  "GRU (taxa da Marinha)",
  "Demonstração prática",
];

function CaminhoB({ onDone }: { reservaId: string; onDone: () => void }) {
  const [etapa, setEtapa] = useState(0);
  const [emitindo, setEmitindo] = useState(false);
  const avancar = () => setEtapa((e) => e + 1);

  if (emitindo)
    return (
      <Card className="mt-4 p-6">
        <div className="rounded-xl bg-amber-50 p-4 text-sm text-amber-800">
          <div className="flex items-center gap-2 font-semibold">
            <Clock size={16} /> Emissão em processamento
          </div>
          <p className="mt-1 text-xs">
            Com a GRU paga e os anexos assinados, a CHA-MTA-E será emitida
            (válida por 30 dias). No v1 o staff valida o comprovante da GRU.
          </p>
          <button
            onClick={onDone}
            className="mt-3 w-full rounded-lg border border-amber-300 bg-white py-2 text-xs font-medium text-amber-800 hover:bg-amber-100"
          >
            ▶︎ Simular emissão da CHA-MTA-E (demo)
          </button>
        </div>
      </Card>
    );

  return (
    <Card className="mt-4 p-6">
      <div className="flex items-center justify-between">
        <h3 className="font-semibold text-ink-900">
          Emissão da CHA-MTA-E
        </h3>
        <Badge tone="brand">
          Passo {etapa + 1}/{ETAPAS_B.length}
        </Badge>
      </div>

      {/* trilha */}
      <div className="mt-3 flex flex-wrap gap-1.5 text-xs">
        {ETAPAS_B.map((e, i) => (
          <span
            key={e}
            className={`rounded-full px-2.5 py-1 ${
              i < etapa
                ? "bg-emerald-100 text-emerald-700"
                : i === etapa
                ? "bg-brand-600 text-white"
                : "bg-slate-100 text-slate-400"
            }`}
          >
            {e}
          </span>
        ))}
      </div>

      <div className="mt-5">
        {etapa === 0 && (
          <div>
            <div className="grid h-44 place-items-center rounded-xl bg-slate-900 text-white">
              <PlayCircle size={56} className="opacity-90" />
            </div>
            <p className="mt-3 text-sm text-slate-600">
              Assista à videoaula oficial da Marinha do Brasil (RIPEAM, LESTA,
              segurança e operação da moto aquática).
            </p>
            <Button className="mt-4 w-full" onClick={avancar}>
              Marcar como assistida <ChevronRight size={15} />
            </Button>
          </div>
        )}

        {etapa === 1 && <Anexos onNext={avancar} />}

        {etapa === 2 && (
          <div>
            <p className="text-sm text-slate-600">
              Envie um documento de identidade com foto e CPF.
              <br />
              <span className="text-xs text-slate-400">
                Estrangeiro? Use o passaporte (anexos em inglês).
              </span>
            </p>
            <label className="mt-3 flex cursor-pointer flex-col items-center gap-2 rounded-xl border-2 border-dashed border-slate-300 bg-slate-50 p-6 text-center hover:border-brand-400">
              <UploadCloud className="text-slate-400" size={26} />
              <span className="text-sm text-slate-600">
                Anexar RG/CNH ou passaporte
              </span>
              <input type="file" className="hidden" />
            </label>
            <Button className="mt-4 w-full" onClick={avancar}>
              Continuar <ChevronRight size={15} />
            </Button>
          </div>
        )}

        {etapa === 3 && <Gru onNext={avancar} />}

        {etapa === 4 && (
          <div>
            <div className="flex gap-3 rounded-xl bg-brand-50 p-4 text-sm text-brand-900">
              <LifeBuoy className="shrink-0" />
              <div>
                <b>Demonstração prática — presencial.</b> No dia do passeio, um
                instrutor confere seus dados e realiza a demonstração de
                segurança no embarque. Sem experiência? A demonstração inclui
                deslocamento com você na garupa do instrutor.
              </div>
            </div>
            <Button className="mt-4 w-full" onClick={() => setEmitindo(true)}>
              Concluir e solicitar emissão
            </Button>
          </div>
        )}
      </div>
    </Card>
  );
}

function Anexos({ onNext }: { onNext: () => void }) {
  const [saude, setSaude] = useState(false);
  const [regras, setRegras] = useState(false);
  const [temComprovante, setTemComprovante] = useState(true);
  const [residencia, setResidencia] = useState(false);

  const ok = saude && regras && (temComprovante || residencia);

  return (
    <div className="space-y-3">
      {/* 5-C Saúde */}
      <div className="rounded-xl border border-slate-200 p-4">
        <span className="text-xs font-semibold text-slate-400">ANEXO 5-C</span>
        <h4 className="font-medium text-ink-900">Autodeclaração de saúde</h4>
        <label className="mt-2 flex items-start gap-2 text-sm text-slate-600">
          <input
            type="checkbox"
            className="mt-1"
            checked={saude}
            onChange={(e) => setSaude(e.target.checked)}
          />
          Declaro gozar de boas condições de saúde física e mental para conduzir
          moto aquática.
        </label>
      </div>

      {/* 5-B Regras */}
      <div className="rounded-xl border border-slate-200 p-4">
        <span className="text-xs font-semibold text-slate-400">ANEXO 5-B</span>
        <h4 className="font-medium text-ink-900">
          Ciência das regras de condução
        </h4>
        <ul className="mt-2 space-y-1 text-xs text-slate-500">
          <li>• Conduzir apenas na área delimitada e entre o nascer e o pôr do sol</li>
          <li>• Não transportar passageiros nem transferir a moto a terceiros</li>
          <li>• Velocidade máxima de 37 km/h (20 nós) · não abastecer</li>
          <li>• Jamais conduzir após álcool ou substâncias entorpecentes</li>
        </ul>
        <label className="mt-2 flex items-start gap-2 text-sm text-slate-600">
          <input
            type="checkbox"
            className="mt-1"
            checked={regras}
            onChange={(e) => setRegras(e.target.checked)}
          />
          Li e estou ciente das regras e das sanções (LESTA/RLESTA e art. 299 CP).
        </label>
      </div>

      {/* 1-C Residência (condicional) */}
      <div className="rounded-xl border border-slate-200 p-4">
        <span className="text-xs font-semibold text-slate-400">ANEXO 1-C</span>
        <h4 className="font-medium text-ink-900">Comprovante de residência</h4>
        <div className="mt-2 flex gap-2 text-sm">
          <button
            onClick={() => setTemComprovante(true)}
            className={`rounded-lg border px-3 py-1.5 ${
              temComprovante
                ? "border-brand-500 bg-brand-50 text-brand-700"
                : "border-slate-200 text-slate-500"
            }`}
          >
            Tenho comprovante
          </button>
          <button
            onClick={() => setTemComprovante(false)}
            className={`rounded-lg border px-3 py-1.5 ${
              !temComprovante
                ? "border-brand-500 bg-brand-50 text-brand-700"
                : "border-slate-200 text-slate-500"
            }`}
          >
            Não tenho (declarar)
          </button>
        </div>
        {!temComprovante && (
          <div className="mt-3">
            <p className="mb-2 text-xs text-slate-500">
              Informe o CEP para preencher o endereço automaticamente.
            </p>
            <AddressForm />
            <label className="mt-3 flex items-start gap-2 text-sm text-slate-600">
              <input
                type="checkbox"
                className="mt-1"
                checked={residencia}
                onChange={(e) => setResidencia(e.target.checked)}
              />
              Declaro, sob as penas da lei (Lei 7.115/83), residir no endereço
              acima.
            </label>
          </div>
        )}
      </div>

      <Button className="w-full" disabled={!ok} onClick={onNext}>
        {ok ? "Gerar anexos e continuar" : "Marque as declarações para continuar"}
      </Button>
    </div>
  );
}

function Gru({ onNext }: { onNext: () => void }) {
  const [gerada, setGerada] = useState(false);
  const valor = 23.13; // taxa simbólica da GRU (exemplo)
  return (
    <div>
      {!gerada ? (
        <div className="text-center">
          <Landmark className="mx-auto text-slate-300" size={40} />
          <p className="mt-2 text-sm text-slate-600">
            A emissão da CHA-MTA-E exige o recolhimento de uma taxa à Marinha via
            <b> GRU</b>.
          </p>
          <Button className="mt-4 w-full" onClick={() => setGerada(true)}>
            Gerar GRU
          </Button>
          <p className="mt-2 text-xs text-slate-400">
            (No v1, a GRU é assistida — futuro: geração automática + PIX/boleto)
          </p>
        </div>
      ) : (
        <div>
          <div className="rounded-xl border border-slate-200 bg-slate-50 p-4">
            <div className="flex justify-between text-sm">
              <span className="text-slate-500">Valor da GRU</span>
              <b className="text-ink-900">{brl(valor)}</b>
            </div>
            <div className="mt-2 flex justify-between text-sm">
              <span className="text-slate-500">Nº da GRU</span>
              <b className="font-mono text-ink-900">2026-000482-19</b>
            </div>
            <div className="mt-3">
              <span className="text-xs text-slate-400">Linha digitável</span>
              <code className="mt-1 block truncate rounded-lg border border-slate-200 bg-white p-2 text-xs text-slate-700">
                85800000000-2 33880482019-3 26060000110-8 04190000000-1
              </code>
            </div>
          </div>
          <label className="mt-3 flex cursor-pointer flex-col items-center gap-2 rounded-xl border-2 border-dashed border-slate-300 bg-slate-50 p-6 text-center hover:border-brand-400">
            <UploadCloud className="text-slate-400" size={26} />
            <span className="text-sm text-slate-600">
              Anexar comprovante de pagamento da GRU
            </span>
            <input type="file" className="hidden" />
          </label>
          <Button className="mt-4 w-full" onClick={onNext}>
            Continuar <ChevronRight size={15} />
          </Button>
        </div>
      )}
    </div>
  );
}
