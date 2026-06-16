"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useState } from "react";
import { FileSignature, CheckCircle2, ShieldCheck, Lock } from "lucide-react";
import { useStore } from "@/lib/store";
import { getModelo, getLoja, CLIENTE } from "@/lib/mock";
import { Button, Card } from "@/components/ui";

const CLAUSULAS = [
  "A moto aquática me é entregue em perfeitas condições de funcionamento e conservação.",
  "Sou responsável pela guarda, conservação e correta operação do equipamento durante o uso.",
  "Danos por negligência, imprudência, imperícia ou desrespeito às orientações são de minha responsabilidade.",
  "Em caso de colisão, abalroamento, encalhe ou choque, arco integralmente com os custos de reparo.",
  "O tombamento (virada) pode causar entrada de água no motor, gerando custos de manutenção.",
  "Autorizo a cobrança dos custos de inspeção/drenagem/reparo entre R$ 400,00 e R$ 2.000,00 em caso de virada por erro operacional.",
  "Possuo condições físicas e psicológicas adequadas e não estou sob efeito de álcool ou drogas.",
  "Comprometo-me a respeitar as orientações do instrutor e as normas da Autoridade Marítima Brasileira.",
];

export default function TermosPage() {
  const { id } = useParams<{ id: string }>();
  const reserva = useStore((s) => s.reservas.find((r) => r.id === id));
  const setEtapa = useStore((s) => s.setEtapa);
  const [aceito, setAceito] = useState(false);

  const m = reserva ? getModelo(reserva.modeloId) : undefined;
  const loja = m ? getLoja(m.lojaId) : undefined;
  if (!reserva || !m || !loja) return <p>Reserva não encontrada.</p>;

  const assinado = reserva.termos === "ok";

  return (
    <div className="mx-auto max-w-2xl">
      <Link
        href={`/conta/reservas/${id}`}
        className="text-sm text-slate-400 hover:text-slate-600"
      >
        ← Voltar para a reserva
      </Link>
      <h1 className="mt-3 flex items-center gap-2 text-2xl font-bold text-ink-900">
        <FileSignature className="text-brand-600" /> Termo de responsabilidade
      </h1>

      {assinado ? (
        <Card className="mt-6 flex flex-col items-center gap-3 p-8 text-center">
          <CheckCircle2 className="text-emerald-500" size={44} />
          <h2 className="text-lg font-semibold text-ink-900">Termo assinado</h2>
          <p className="text-sm text-slate-500">
            Aceite eletrônico registrado com data, IP e hash do documento.
          </p>
          <Button href={`/conta/reservas/${id}`} variant="outline">
            Voltar para a reserva
          </Button>
        </Card>
      ) : (
        <>
          <Card className="mt-6 p-6">
            <div className="text-center">
              <h2 className="font-bold text-ink-900">
                TERMO DE RESPONSABILIDADE PELO USO DE MOTO AQUÁTICA
              </h2>
              <p className="text-sm font-semibold text-slate-600">
                {loja.nome.toUpperCase()}
              </p>
              <p className="text-xs text-slate-400">CNPJ: {loja.cnpj}</p>
            </div>

            <p className="mt-4 text-sm text-slate-600">
              Eu, <b>{CLIENTE.nome}</b>, CPF <b>{CLIENTE.cpf}</b>, declaro que
              recebi orientações de segurança e instruções de utilização da moto
              aquática disponibilizada pela {loja.nome}, assumindo total
              responsabilidade pelo equipamento durante o período de utilização e
              que estou ciente de que:
            </p>

            <ol className="mt-3 max-h-72 space-y-2 overflow-auto rounded-xl bg-slate-50 p-4 text-sm text-slate-700">
              {CLAUSULAS.map((c, i) => (
                <li key={i} className="flex gap-2">
                  <span className="font-semibold text-brand-600">{i + 1}.</span>
                  {c}
                </li>
              ))}
            </ol>

            <p className="mt-3 text-xs text-slate-400">
              Acompanham este termo os anexos da NORMAM-212/DPC aplicáveis
              (5-B, 5-C e 1-C quando emitida a CHA-MTA-E).
            </p>
          </Card>

          <Card className="mt-4 p-6">
            <label className="flex items-start gap-3 text-sm text-slate-700">
              <input
                type="checkbox"
                className="mt-1"
                checked={aceito}
                onChange={(e) => setAceito(e.target.checked)}
              />
              Li e concordo com o termo de responsabilidade e declaro que as
              informações são verdadeiras.
            </label>

            <Button
              className="mt-4 w-full"
              size="lg"
              disabled={!aceito}
              onClick={() => setEtapa(reserva.id, "termos", "ok")}
            >
              <Lock size={16} /> Assinar eletronicamente
            </Button>
            <p className="mt-2 flex items-center justify-center gap-1 text-center text-xs text-slate-400">
              <ShieldCheck size={13} /> Registramos data/hora, IP, dispositivo e
              hash SHA-256 como evidência do aceite.
            </p>
          </Card>
        </>
      )}
    </div>
  );
}
