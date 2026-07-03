"use client";

import { useParams, useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import {
  MapPin,
  Users,
  Star,
  ShieldCheck,
  CheckCircle2,
  AlertCircle,
  Loader2,
  XCircle,
} from "lucide-react";
import {
  getModeloPublico,
  getDisponibilidade,
  getBrandingLoja,
  fotoPrincipal,
  type MarketplaceModelo,
  type Disponibilidade,
  type BrandingLoja,
} from "@/lib/api";
import { jetImage } from "@/lib/img";
import { brl } from "@/lib/cn";
import { Button, Card, Field, inputCls } from "@/components/ui";

const SINAL_PCT = 0.3;

export default function ModeloPage() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();

  const [m, setM] = useState<MarketplaceModelo | null>(null);
  const [branding, setBranding] = useState<BrandingLoja | null>(null);
  const [carregando, setCarregando] = useState(true);
  const [data, setData] = useState(() => {
    const d = new Date(Date.now() + 3 * 86400000);
    return d.toISOString().slice(0, 10);
  });
  const [hora, setHora] = useState("10:00");
  const [horas, setHoras] = useState(2);
  const [disp, setDisp] = useState<Disponibilidade | null>(null);
  const [checando, setChecando] = useState(false);
  const [erroDisp, setErroDisp] = useState<string | null>(null);

  useEffect(() => {
    getModeloPublico(id)
      .then((modelo) => {
        setM(modelo);
        getBrandingLoja(modelo.lojaSlug).then(setBranding);
      })
      .catch(() => setM(null))
      .finally(() => setCarregando(false));
  }, [id]);

  useEffect(() => {
    setDisp(null);
  }, [data, hora, horas]);

  if (carregando) {
    return (
      <div className="flex justify-center py-24 text-slate-400">
        <Loader2 className="animate-spin" />
      </div>
    );
  }
  if (!m) return <p className="py-20 text-center text-slate-400">Modelo não encontrado.</p>;

  const foto = fotoPrincipal(m) ?? jetImage("200", "230", m.id.charCodeAt(0));
  const total = m.precoBaseHora * horas;
  const inicioISO = `${data}T${hora}:00`;
  const fim = new Date(new Date(inicioISO).getTime() + horas * 3600000);
  const fimISO = `${fim.getFullYear()}-${String(fim.getMonth() + 1).padStart(2, "0")}-${String(fim.getDate()).padStart(2, "0")}T${String(fim.getHours()).padStart(2, "0")}:${String(fim.getMinutes()).padStart(2, "0")}:00`;

  async function verificar() {
    if (!m) return;
    setChecando(true);
    setErroDisp(null);
    try {
      setDisp(await getDisponibilidade(m.lojaSlug, m.id, inicioISO, fimISO));
    } catch {
      setErroDisp("Não foi possível consultar a disponibilidade.");
    } finally {
      setChecando(false);
    }
  }

  return (
    <div className="grid gap-8 lg:grid-cols-[1.4fr_1fr]">
      <div>
        <div className="overflow-hidden rounded-3xl border border-slate-200 bg-slate-100">
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img src={foto} alt={m.nome} className="h-80 w-full object-cover" />
        </div>

        <div className="mt-5">
          <div className="flex items-center gap-1 text-sm text-slate-400">
            <MapPin size={14} /> {m.localizacao} · {m.empresaNome}
          </div>
          <h1 className="mt-1 flex items-center gap-3 text-2xl font-bold text-ink-900">
            {branding?.logoDataUrl && (
              /* eslint-disable-next-line @next/next/no-img-element */
              <img src={branding.logoDataUrl} alt={m.empresaNome}
                className="h-8 w-8 rounded-lg object-contain" />
            )}
            {m.nome}
          </h1>
          {m.notaMedia != null && (m.totalAvaliacoes ?? 0) > 0 && (
            <p className="mt-1 flex items-center gap-1 text-sm font-medium text-amber-600">
              <Star size={14} className="fill-amber-400 text-amber-400" />
              {m.notaMedia.toFixed(1)} · {m.totalAvaliacoes} avaliação(ões)
            </p>
          )}

          <div className="mt-5 grid grid-cols-2 gap-3 sm:grid-cols-3">
            {m.fabricante && <Spec icon={<ShieldCheck size={16} />} label="Fabricante" value={m.fabricante} />}
            <Spec icon={<Users size={16} />} label="Capacidade" value={`${m.capacidadePessoas} pessoas`} />
            <Spec icon={<MapPin size={16} />} label="Loja" value={m.empresaNome} />
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
                Sinal de 30% via PIX garante a reserva (QR com valor exato).
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

            <Button variant="outline" className="w-full" onClick={verificar} disabled={checando}>
              {checando && <Loader2 size={14} className="animate-spin" />}
              Verificar disponibilidade
            </Button>

            {erroDisp && (
              <div className="rounded-xl bg-red-50 p-3 text-sm text-red-700">{erroDisp}</div>
            )}
            {disp && (disp.aceitaComSinal ? (
              <div className="rounded-xl bg-emerald-50 p-3 text-sm text-emerald-800">
                <div className="flex items-center gap-2 font-semibold">
                  <CheckCircle2 size={16} /> Disponível — garanta com sinal
                </div>
                <p className="mt-1 text-xs text-emerald-700">
                  {disp.vagasGarantidas} de {disp.totalJetskis} unidade(s) com garantia neste horário.
                </p>
              </div>
            ) : disp.aceitaSemSinal ? (
              <div className="rounded-xl bg-amber-50 p-3 text-sm text-amber-800">
                <div className="flex items-center gap-2 font-semibold">
                  <AlertCircle size={16} /> Lista de espera — sem garantia
                </div>
                <p className="mt-1 text-xs">
                  As unidades garantidas esgotaram; a reserva entra por ordem de chegada.
                </p>
              </div>
            ) : (
              <div className="rounded-xl bg-red-50 p-3 text-sm text-red-700">
                <div className="flex items-center gap-2 font-semibold">
                  <XCircle size={16} /> Esgotado neste horário
                </div>
              </div>
            ))}
          </div>

          <div className="mt-4 space-y-1.5 border-t border-slate-100 pt-4 text-sm">
            <Row label={`${brl(m.precoBaseHora)} × ${horas}h`} value={brl(total)} />
            <Row label="Sinal (30%)" value={brl(total * SINAL_PCT)} accent />
          </div>

          <Button
            className="mt-4 w-full"
            size="lg"
            style={branding?.corPrimaria ? { backgroundColor: branding.corPrimaria } : undefined}
            onClick={() =>
              router.push(`/reservar/${m.id}?data=${data}&hora=${hora}&horas=${horas}`)
            }
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
}: {
  label: string;
  value: string;
  accent?: boolean;
}) {
  return (
    <div className="flex justify-between">
      <span className="text-slate-600">{label}</span>
      <span className={accent ? "font-semibold text-brand-700" : "font-medium text-ink-900"}>
        {value}
      </span>
    </div>
  );
}
