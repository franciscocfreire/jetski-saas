"use client";

import { useParams, useRouter } from "next/navigation";
import { useState } from "react";
import {
  Star,
  MapPin,
  Users,
  Gauge,
  Fuel,
  ShieldCheck,
  CheckCircle2,
  AlertCircle,
} from "lucide-react";
import { getModelo, getLoja } from "@/lib/mock";
import { brl } from "@/lib/cn";
import { Button, Card, Badge, Field, inputCls } from "@/components/ui";

export default function ModeloPage() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();
  const m = getModelo(id);
  const loja = m ? getLoja(m.lojaId) : undefined;

  const [data, setData] = useState("2026-06-22");
  const [hora, setHora] = useState("10:00");
  const [horas, setHoras] = useState(2);
  const [checado, setChecado] = useState(false);

  if (!m) return <p>Modelo não encontrado.</p>;

  const total = m.precoBaseHora * horas;

  return (
    <div className="grid gap-8 lg:grid-cols-[1.4fr_1fr]">
      <div>
        <div className="overflow-hidden rounded-3xl border border-slate-200 bg-slate-100">
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img src={m.fotoUrl} alt={m.nome} className="h-80 w-full object-cover" />
        </div>

        <div className="mt-5">
          <div className="flex items-center gap-1 text-sm text-slate-400">
            <MapPin size={14} /> {m.cidade} · {m.lojaNome}
          </div>
          <h1 className="mt-1 text-2xl font-bold text-ink-900">{m.nome}</h1>
          <div className="mt-2 flex items-center gap-3 text-sm text-slate-500">
            <span className="flex items-center gap-1 font-medium text-amber-600">
              <Star size={14} className="fill-amber-400 text-amber-400" />
              {m.rating} ({m.avaliacoes})
            </span>
          </div>

          <div className="mt-5 grid grid-cols-2 gap-3 sm:grid-cols-4">
            <Spec icon={<Gauge size={16} />} label="Potência" value={`${m.potenciaHp} HP`} />
            <Spec icon={<Users size={16} />} label="Capacidade" value={`${m.capacidadePessoas} pessoas`} />
            <Spec icon={<Fuel size={16} />} label="Combustível" value={m.combustivel} />
            <Spec icon={<ShieldCheck size={16} />} label="Caução" value={brl(m.caucao)} />
          </div>

          <Card className="mt-6 p-5">
            <h3 className="font-semibold text-ink-900">O que você precisa saber</h3>
            <ul className="mt-3 space-y-2 text-sm text-slate-600">
              <li className="flex gap-2">
                <CheckCircle2 size={16} className="mt-0.5 shrink-0 text-emerald-500" />
                Necessário condutor habilitado (Arrais/Motonauta) ou emissão da
                habilitação especial <b>CHA-MTA-E</b> antes do passeio.
              </li>
              <li className="flex gap-2">
                <CheckCircle2 size={16} className="mt-0.5 shrink-0 text-emerald-500" />
                Sinal de 30% via PIX garante a reserva.
              </li>
              <li className="flex gap-2">
                <CheckCircle2 size={16} className="mt-0.5 shrink-0 text-emerald-500" />
                Demonstração prática de segurança presencial no embarque.
              </li>
            </ul>
          </Card>
        </div>
      </div>

      {/* Caixa de reserva */}
      <div>
        <Card className="sticky top-24 p-5">
          <div className="flex items-baseline gap-1">
            <span className="text-2xl font-bold text-ink-900">
              {brl(m.precoBaseHora)}
            </span>
            <span className="text-sm text-slate-400">/hora</span>
          </div>

          <div className="mt-4 space-y-3">
            <div className="grid grid-cols-2 gap-3">
              <Field label="Data">
                <input
                  type="date"
                  className={inputCls}
                  value={data}
                  onChange={(e) => setData(e.target.value)}
                />
              </Field>
              <Field label="Hora">
                <input
                  type="time"
                  className={inputCls}
                  value={hora}
                  onChange={(e) => setHora(e.target.value)}
                />
              </Field>
            </div>
            <Field label="Duração">
              <select
                className={inputCls}
                value={horas}
                onChange={(e) => setHoras(Number(e.target.value))}
              >
                <option value={1}>1 hora</option>
                <option value={2}>2 horas</option>
                <option value={3}>3 horas</option>
                <option value={4}>4 horas</option>
              </select>
            </Field>

            <Button
              variant="outline"
              className="w-full"
              onClick={() => setChecado(true)}
            >
              Verificar disponibilidade
            </Button>

            {checado && (
              <div className="rounded-xl bg-emerald-50 p-3 text-sm text-emerald-800">
                <div className="flex items-center gap-2 font-semibold">
                  <CheckCircle2 size={16} /> Disponível — garanta com sinal
                </div>
                <p className="mt-1 text-xs text-emerald-700">
                  2 de 3 unidades livres neste horário.
                </p>
              </div>
            )}
          </div>

          <div className="mt-4 space-y-1.5 border-t border-slate-100 pt-4 text-sm">
            <Row label={`${brl(m.precoBaseHora)} × ${horas}h`} value={brl(total)} />
            <Row label="Sinal (30%)" value={brl(total * 0.3)} accent />
            <Row label="Caução (reembolsável)" value={brl(m.caucao)} muted />
          </div>

          <Button
            className="mt-4 w-full"
            size="lg"
            style={loja?.branding ? { backgroundColor: loja.branding.corPrimaria } : undefined}
            onClick={() => router.push(`/reservar/${m.id}?data=${data}&hora=${hora}&horas=${horas}`)}
          >
            Reservar agora
          </Button>
          <p className="mt-2 flex items-center justify-center gap-1 text-center text-xs text-slate-400">
            <AlertCircle size={12} /> Você só paga o sinal após confirmar.
          </p>
        </Card>
      </div>
    </div>
  );
}

function Spec({
  icon,
  label,
  value,
}: {
  icon: React.ReactNode;
  label: string;
  value: string;
}) {
  return (
    <div className="rounded-xl border border-slate-200 bg-white p-3">
      <div className="flex items-center gap-1 text-xs text-slate-400">
        {icon} {label}
      </div>
      <div className="mt-1 text-sm font-semibold text-ink-900">{value}</div>
    </div>
  );
}

function Row({
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
      <span className={muted ? "text-slate-400" : "text-slate-600"}>{label}</span>
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
