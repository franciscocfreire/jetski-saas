"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import {
  CreditCard,
  IdCard,
  FileSignature,
  CheckCircle2,
  Circle,
  Clock,
  ArrowRight,
  PartyPopper,
  MapPin,
} from "lucide-react";
import { useStore } from "@/lib/store";
import { getModelo, type ChecklistEstado } from "@/lib/mock";
import { brl, fmtDateTime } from "@/lib/cn";
import { Badge, Button, Card } from "@/components/ui";
import { EmailBanner } from "@/components/EmailBanner";

export default function ReservaDetailPage() {
  const { id } = useParams<{ id: string }>();
  const reserva = useStore((s) => s.reservas.find((r) => r.id === id));
  const auth = useStore((s) => s.auth);
  const m = reserva ? getModelo(reserva.modeloId) : undefined;

  if (!reserva || !m)
    return (
      <div className="py-20 text-center text-slate-400">
        Reserva não encontrada.
      </div>
    );

  // Conta restrita (logado mas sem e-mail verificado) bloqueia a garantia.
  const emailOk = !auth.logged || auth.emailVerified === true;
  const pronta = reserva.status === "PRONTA" && emailOk;

  return (
    <div className="mx-auto max-w-3xl">
      <Link
        href="/conta/reservas"
        className="text-sm text-slate-400 hover:text-slate-600"
      >
        ← Minhas reservas
      </Link>

      <div className="mt-3" />
      <EmailBanner />

      <Card className="overflow-hidden">
        <div className="flex flex-col gap-4 p-5 sm:flex-row">
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img
            src={m.fotoUrl}
            alt=""
            className="h-32 w-full rounded-xl object-cover sm:w-48"
          />
          <div className="flex-1">
            <div className="flex items-center gap-2">
              <span className="font-mono text-xs text-slate-400">{reserva.id}</span>
              {pronta ? (
                <Badge tone="green">Pronta p/ check-in</Badge>
              ) : (
                <Badge tone="amber">Pendências</Badge>
              )}
            </div>
            <h1 className="mt-1 text-xl font-bold text-ink-900">{m.nome}</h1>
            <p className="flex items-center gap-1 text-sm text-slate-500">
              <MapPin size={13} /> {m.lojaNome}
            </p>
            <p className="mt-2 text-sm text-slate-600">
              {fmtDateTime(reserva.data)} · {reserva.duracaoHoras}h ·{" "}
              {reserva.pessoas} pessoa(s)
            </p>
            <p className="mt-1 text-sm">
              <span className="text-slate-500">Sinal: </span>
              <b>{brl(reserva.valorSinal)}</b>
              <span className="text-slate-400">
                {" "}
                · de {brl(reserva.valorEstimado)}
              </span>
            </p>
          </div>
        </div>

        {pronta ? (
          <div className="flex items-center gap-3 border-t border-emerald-100 bg-emerald-50 px-5 py-4 text-emerald-800">
            <PartyPopper className="shrink-0" />
            <div className="text-sm">
              <b>Tudo pronto!</b> Apresente-se na loja na data agendada. A
              demonstração de segurança será feita pelo instrutor no embarque.
            </div>
          </div>
        ) : (
          reserva.sinal !== "pendente" &&
          (emailOk ? (
            <div className="flex items-center gap-3 border-t border-brand-100 bg-brand-50 px-5 py-4 text-brand-900">
              <PartyPopper className="shrink-0" />
              <div className="text-sm">
                <b>Reserva garantida!</b> Recebemos seu pagamento. Agora é só
                concluir a <b>habilitação</b> e assinar os <b>termos</b> antes do
                passeio.
              </div>
            </div>
          ) : (
            <div className="flex items-center gap-3 border-t border-amber-100 bg-amber-50 px-5 py-4 text-amber-900">
              <PartyPopper className="shrink-0" />
              <div className="text-sm">
                <b>Pagamento recebido!</b> Para <b>garantir</b> a reserva, confirme
                seu e-mail (a pré-reserva expira em 24h sem verificação).
              </div>
            </div>
          ))
        )}
      </Card>

      <h2 className="mb-3 mt-8 text-lg font-bold text-ink-900">
        {pronta ? "Etapas concluídas" : "Conclua para liberar o check-in"}
      </h2>

      <div className="grid gap-3">
        <TaskRow
          estado={reserva.sinal}
          icon={<CreditCard size={18} />}
          title={
            reserva.pagamentoTipo === "total"
              ? "Pagamento (valor total)"
              : "Pagamento (sinal 30%)"
          }
          desc={
            reserva.pagamentoTipo === "total"
              ? "PIX da loja + comprovante · nada a pagar no embarque"
              : "PIX da loja + comprovante · restante no check-in"
          }
          href={`/conta/reservas/${reserva.id}/pagamento`}
        />
        <TaskRow
          estado={reserva.habilitacao}
          icon={<IdCard size={18} />}
          title="Habilitação náutica"
          desc="Envie sua CHA ou emita a CHA-MTA-E"
          href={`/conta/reservas/${reserva.id}/habilitacao`}
        />
        <TaskRow
          estado={reserva.termos}
          icon={<FileSignature size={18} />}
          title="Termos e responsabilidade"
          desc="Assine o termo da loja e os anexos da Marinha"
          href={`/conta/reservas/${reserva.id}/termos`}
        />
      </div>
    </div>
  );
}

function stateMeta(e: ChecklistEstado) {
  switch (e) {
    case "ok":
      return {
        icon: <CheckCircle2 className="text-emerald-500" />,
        badge: <Badge tone="green">Concluído</Badge>,
        cta: "Ver",
      };
    case "em_validacao":
      return {
        icon: <Clock className="text-amber-500" />,
        badge: <Badge tone="amber">Em validação</Badge>,
        cta: "Acompanhar",
      };
    case "expirada":
      return {
        icon: <Circle className="text-rose-400" />,
        badge: <Badge tone="red">Expirada</Badge>,
        cta: "Renovar",
      };
    default:
      return {
        icon: <Circle className="text-slate-300" />,
        badge: <Badge tone="slate">Pendente</Badge>,
        cta: "Resolver",
      };
  }
}

function TaskRow({
  estado,
  icon,
  title,
  desc,
  href,
}: {
  estado: ChecklistEstado;
  icon: React.ReactNode;
  title: string;
  desc: string;
  href: string;
}) {
  const meta = stateMeta(estado);
  return (
    <Card className="flex items-center gap-4 p-4">
      <span className="shrink-0">{meta.icon}</span>
      <span className="grid h-10 w-10 shrink-0 place-items-center rounded-xl bg-slate-100 text-slate-600">
        {icon}
      </span>
      <div className="min-w-0 flex-1">
        <div className="flex items-center gap-2">
          <h3 className="font-semibold text-ink-900">{title}</h3>
          {meta.badge}
        </div>
        <p className="text-sm text-slate-500">{desc}</p>
      </div>
      <Button
        href={href}
        variant={estado === "ok" ? "outline" : "primary"}
        size="sm"
      >
        {meta.cta} <ArrowRight size={15} />
      </Button>
    </Card>
  );
}
