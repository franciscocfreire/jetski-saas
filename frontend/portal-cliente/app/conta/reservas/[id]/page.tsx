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
              {pg.status === "PRESENCIAL" ? (
                <>
                  <span className="text-slate-500">Pagamento na loja · </span>
                  <span className="text-slate-400">estimado {brl(pg.valorTotal)}</span>
                </>
              ) : (
                <>
                  <span className="text-slate-500">
                    {pg.tipo === "TOTAL" ? "Total: " : "Sinal: "}
                  </span>
                  <b>{brl(pg.tipo === "TOTAL" ? pg.valorTotal : pg.valorSinal)}</b>
                  <span className="text-slate-400"> · estimado {brl(pg.valorTotal)}</span>
                </>
              )}
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

      <h2 className="mb-4 mt-8 text-lg font-bold text-ink-900">
        Sua jornada até o passeio
      </h2>

      <Timeline
        passos={[
          {
            titulo: "Reserva feita",
            desc: `${reserva.modeloNome} · ${fmtDateTime(reserva.dataInicio)}`,
            estado: "ok",
          },
          pg.status === "PRESENCIAL"
            ? {
                titulo: "Pagamento na loja",
                desc: "Pague no balcão no dia do passeio — dinheiro, PIX ou cartão",
                estado: "pendente" as const,
                icone: <CreditCard size={16} />,
                // sem href: não há nada a "resolver" pelo portal
              }
            : {
                titulo: pg.tipo === "TOTAL" ? "Pagamento (valor total)" : "Pagamento (sinal 30%)",
                desc:
                  pg.status === "RECUSADO"
                    ? `Recusado: ${pg.motivoRecusa ?? "confira o valor"} — reenvie o comprovante`
                    : pg.status === "EM_ANALISE"
                      ? "Comprovante em análise pela loja"
                      : pg.status === "CONFIRMADO"
                        ? "Pagamento confirmado pela loja"
                        : "PIX com QR de valor exato",
                estado: estadoPagamento,
                icone: <CreditCard size={16} />,
                href: `/conta/reservas/${reserva.id}/pagamento`,
                alerta: pg.status === "RECUSADO",
              },
          {
            titulo: "Termos e responsabilidade",
            desc: checklist.termosOk
              ? "Termo assinado"
              : "Assine o termo da loja pelo celular",
            estado: checklist.termosOk ? "ok" : "pendente",
            icone: <FileSignature size={16} />,
            href: `/conta/reservas/${reserva.id}/termos`,
          },
          {
            titulo: "Habilitação náutica",
            desc: checklist.habilitacaoTemporaria
              ? `Habilitação temporária vigente até ${new Date(
                  checklist.habilitacaoTemporaria.validaAte + "T12:00:00"
                ).toLocaleDateString("pt-BR")} (GRU nº ${checklist.habilitacaoTemporaria.gruNumero})`
              : checklist.habilitacaoOk
                ? checklist.habilitacaoVia === "CHA"
                  ? "CHA enviada e registrada"
                  : "CHA-MTA-E encaminhada"
                : checklist.habilitacaoVia === "CHA"
                  ? "Envie os dados e a foto da sua CHA"
                  : checklist.habilitacaoVia === "EMA"
                    ? "Complete os passos da CHA-MTA-E — a taxa da Marinha fica com a loja"
                    : "Envie sua CHA ou emita a CHA-MTA-E",
            estado: checklist.habilitacaoOk ? "ok" : "pendente",
            icone: <IdCard size={16} />,
            href: `/conta/reservas/${reserva.id}/habilitacao`,
          },
          {
            titulo: "Pronto para o check-in",
            desc: pronta
              ? "Tudo certo — apresente-se na loja na data agendada"
              : "Libera quando as etapas acima estiverem concluídas",
            estado: pronta ? "ok" : "futuro",
            final: true,
          },
        ]}
      />
      <p className="mt-8 text-center text-[11px] text-slate-300">
        Código da reserva: {reserva.id}
      </p>
    </div>
  );
}

type Estado = "ok" | "em_validacao" | "pendente" | "futuro";

interface Passo {
  titulo: string;
  desc: string;
  estado: Estado;
  icone?: React.ReactNode;
  href?: string;
  alerta?: boolean;
  final?: boolean;
}

/** Linha do tempo vertical da reserva (D2): onde estou e o que falta. */
function Timeline({ passos }: { passos: Passo[] }) {
  // o passo "atual" é o primeiro não-concluído acionável
  const atualIdx = passos.findIndex((p) => p.estado !== "ok" && !p.final);
  return (
    <ol className="relative">
      {passos.map((p, i) => {
        const atual = i === atualIdx;
        const ultimo = i === passos.length - 1;
        const cardado = atual || p.alerta || (p.final && p.estado === "ok");
        return (
          <li key={p.titulo} className="relative flex gap-4 pb-6 last:pb-0">
            {!ultimo && (
              <span
                className={`absolute left-[15px] top-9 h-[calc(100%-28px)] w-0.5 ${
                  p.estado === "ok" ? "bg-emerald-300" : "bg-slate-200"
                }`}
                aria-hidden
              />
            )}
            <span className="relative z-10 mt-1 shrink-0">
              {p.estado === "ok" ? (
                <CheckCircle2 size={32} className="text-emerald-500" strokeWidth={2} />
              ) : p.estado === "em_validacao" ? (
                <span className="grid h-8 w-8 place-items-center rounded-full border-2 border-amber-400 bg-amber-50">
                  <Clock size={15} className="text-amber-500" />
                </span>
              ) : atual ? (
                <span className="grid h-8 w-8 place-items-center rounded-full border-2 border-brand-500 bg-brand-50 ring-4 ring-brand-100">
                  <Circle size={10} className="fill-brand-500 text-brand-500" />
                </span>
              ) : (
                <span className="grid h-8 w-8 place-items-center rounded-full border-2 border-slate-200 bg-white">
                  <Circle size={10} className="text-slate-300" />
                </span>
              )}
            </span>
            <div
              className={
                cardado
                  ? `min-w-0 flex-1 rounded-2xl border p-4 ${
                      p.alerta
                        ? "border-red-200 bg-red-50"
                        : p.final
                          ? "border-emerald-200 bg-emerald-50"
                          : "border-brand-200 bg-white shadow-sm"
                    }`
                  : "min-w-0 flex-1 px-0 py-1"
              }
            >
              <div className="flex items-center gap-2">
                {p.icone && <span className="text-slate-400">{p.icone}</span>}
                <h3
                  className={`font-semibold ${
                    p.estado === "futuro" ? "text-slate-400" : "text-ink-900"
                  }`}
                >
                  {p.titulo}
                </h3>
              </div>
              <p
                className={`mt-0.5 text-sm ${
                  p.alerta
                    ? "text-red-700"
                    : p.estado === "futuro"
                      ? "text-slate-400"
                      : "text-slate-500"
                }`}
              >
                {p.desc}
              </p>
              {p.href && p.estado !== "ok" && (
                <Button
                  href={p.href}
                  size="sm"
                  variant={atual || p.alerta ? "primary" : "outline"}
                  className="mt-3"
                >
                  {p.estado === "em_validacao" ? "Acompanhar" : "Resolver"}{" "}
                  <ArrowRight size={15} />
                </Button>
              )}
              {p.href && p.estado === "ok" && (
                <Link
                  href={p.href}
                  className="mt-1 inline-block text-xs text-slate-400 underline-offset-2 hover:underline"
                >
                  ver detalhes
                </Link>
              )}
            </div>
          </li>
        );
      })}
    </ol>
  );
}
