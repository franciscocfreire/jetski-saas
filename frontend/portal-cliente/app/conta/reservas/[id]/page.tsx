"use client";

import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { useSession } from "next-auth/react";
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
  Loader2,
} from "lucide-react";
import {
  getReserva,
  getChecklist,
  type ReservaCliente,
  type ChecklistReserva,
} from "@/lib/api";
import { brl, fmtDateTime } from "@/lib/cn";
import { Badge, Button, Card } from "@/components/ui";

export default function ReservaDetailPage() {
  const { id } = useParams<{ id: string }>();
  const { data: session, status } = useSession();
  const router = useRouter();

  const [reserva, setReserva] = useState<ReservaCliente | null>(null);
  const [checklist, setChecklist] = useState<ChecklistReserva | null>(null);
  const [erro, setErro] = useState(false);

  useEffect(() => {
    if (status === "unauthenticated") {
      router.replace("/login");
      return;
    }
    if (status === "authenticated" && session?.accessToken) {
      Promise.all([
        getReserva(session.accessToken, id),
        getChecklist(session.accessToken, id),
      ])
        .then(([r, c]) => {
          setReserva(r);
          setChecklist(c);
        })
        .catch(() => setErro(true));
    }
  }, [status, session?.accessToken, id, router]);

  if (erro) {
    return <div className="py-20 text-center text-slate-400">Reserva não encontrada.</div>;
  }
  if (!reserva || !checklist) {
    return (
      <div className="flex justify-center py-20 text-slate-400">
        <Loader2 className="animate-spin" />
      </div>
    );
  }

  const pg = reserva.pagamento;
  const pronta = checklist.prontaParaCheckin;
  const horas = Math.round(
    (new Date(reserva.dataFimPrevista).getTime() - new Date(reserva.dataInicio).getTime()) / 3600000
  );

  const estadoPagamento =
    pg.status === "CONFIRMADO" ? "ok" : pg.status === "EM_ANALISE" ? "em_validacao" : "pendente";

  return (
    <div className="mx-auto max-w-3xl">
      <Link href="/conta/reservas" className="text-sm text-slate-400 hover:text-slate-600">
        ← Minhas reservas
      </Link>

      {!checklist.emailVerified && (
        <div className="mt-3 rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900">
          <b>Verifique seu e-mail.</b> Sem a verificação a reserva não fica garantida
          (link enviado no cadastro).
        </div>
      )}

      <Card className="mt-3 overflow-hidden">
        <div className="flex flex-col gap-4 p-5 sm:flex-row">
          <div className="flex-1">
            <div className="flex items-center gap-2">
              <span className="font-mono text-xs text-slate-400">{reserva.id.slice(0, 8)}</span>
              {pronta ? (
                <Badge tone="green">Pronta p/ check-in</Badge>
              ) : checklist.garantida ? (
                <Badge tone="green">Garantida</Badge>
              ) : reserva.status === "EXPIRADA" ? (
                <Badge tone="red">Expirada</Badge>
              ) : (
                <Badge tone="amber">Pendências</Badge>
              )}
            </div>
            <h1 className="mt-1 text-xl font-bold text-ink-900">{reserva.modeloNome}</h1>
            <p className="flex items-center gap-1 text-sm text-slate-500">
              <MapPin size={13} /> {reserva.lojaNome}
            </p>
            <p className="mt-2 text-sm text-slate-600">
              {fmtDateTime(reserva.dataInicio)} · {horas}h
            </p>
            <p className="mt-1 text-sm">
              <span className="text-slate-500">
                {pg.tipo === "TOTAL" ? "Total: " : "Sinal: "}
              </span>
              <b>{brl(pg.tipo === "TOTAL" ? pg.valorTotal : pg.valorSinal)}</b>
              <span className="text-slate-400"> · estimado {brl(pg.valorTotal)}</span>
            </p>
          </div>
        </div>

        {pronta ? (
          <div className="flex items-center gap-3 border-t border-emerald-100 bg-emerald-50 px-5 py-4 text-emerald-800">
            <PartyPopper className="shrink-0" />
            <div className="text-sm">
              <b>Tudo pronto!</b> Apresente-se na loja na data agendada. A demonstração
              de segurança será feita pelo instrutor no embarque.
            </div>
          </div>
        ) : checklist.garantida ? (
          <div className="flex items-center gap-3 border-t border-brand-100 bg-brand-50 px-5 py-4 text-brand-900">
            <PartyPopper className="shrink-0" />
            <div className="text-sm">
              <b>Reserva garantida!</b> Agora é só concluir a <b>habilitação</b> e os{" "}
              <b>termos</b> antes do passeio.
            </div>
          </div>
        ) : null}
      </Card>

      <h2 className="mb-3 mt-8 text-lg font-bold text-ink-900">
        {pronta ? "Etapas concluídas" : "Conclua para liberar o check-in"}
      </h2>

      <div className="grid gap-3">
        <TaskRow
          estado={estadoPagamento}
          icon={<CreditCard size={18} />}
          title={pg.tipo === "TOTAL" ? "Pagamento (valor total)" : "Pagamento (sinal 30%)"}
          desc={
            pg.status === "RECUSADO"
              ? `Recusado: ${pg.motivoRecusa ?? "confira o valor"} — reenvie o comprovante`
              : pg.tipo === "TOTAL"
                ? "PIX com QR de valor exato · nada a pagar no embarque"
                : "PIX com QR de valor exato · restante no check-in"
          }
          href={`/conta/reservas/${reserva.id}/pagamento`}
        />
        <TaskRow
          estado={checklist.habilitacaoOk ? "ok" : "pendente"}
          icon={<IdCard size={18} />}
          title="Habilitação náutica"
          desc="Em breve pelo portal — por enquanto, resolva com a loja no atendimento"
          href={`/conta/reservas/${reserva.id}/habilitacao`}
        />
        <TaskRow
          estado={checklist.termosOk ? "ok" : "pendente"}
          icon={<FileSignature size={18} />}
          title="Termos e responsabilidade"
          desc="Em breve pelo portal — assinatura acontece no atendimento da loja"
          href={`/conta/reservas/${reserva.id}/termos`}
        />
      </div>
    </div>
  );
}

type Estado = "ok" | "em_validacao" | "pendente";

function stateMeta(e: Estado) {
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
  estado: Estado;
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
      <Button href={href} variant={estado === "ok" ? "outline" : "primary"} size="sm">
        {meta.cta} <ArrowRight size={15} />
      </Button>
    </Card>
  );
}
