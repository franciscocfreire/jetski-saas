"use client";

import Link from "next/link";
import { CalendarX2, ChevronRight } from "lucide-react";
import { useStore } from "@/lib/store";
import { getModelo, type Reserva } from "@/lib/mock";
import { brl, fmtDateTime } from "@/lib/cn";
import { Badge, Button, Card, SectionTitle } from "@/components/ui";
import { EmailBanner } from "@/components/EmailBanner";

function statusBadge(r: Reserva) {
  switch (r.status) {
    case "PRONTA":
      return <Badge tone="green">Pronta p/ check-in</Badge>;
    case "EM_CURSO":
      return <Badge tone="brand">Em curso</Badge>;
    case "FINALIZADA":
      return <Badge tone="slate">Finalizada</Badge>;
    case "CANCELADA":
      return <Badge tone="red">Cancelada</Badge>;
    default:
      return <Badge tone="amber">Pendências</Badge>;
  }
}

export default function ReservasPage() {
  const reservas = useStore((s) => s.reservas);

  return (
    <div>
      <SectionTitle sub="Acompanhe e conclua as pendências de cada reserva">
        Minhas reservas
      </SectionTitle>

      <EmailBanner />

      {reservas.length === 0 ? (
        <Card className="flex flex-col items-center gap-3 p-12 text-center">
          <CalendarX2 className="text-slate-300" size={40} />
          <p className="text-slate-500">Você ainda não tem reservas.</p>
          <Button href="/">Explorar jet skis</Button>
        </Card>
      ) : (
        <div className="grid gap-4">
          {reservas.map((r) => {
            const m = getModelo(r.modeloId);
            const pend =
              [r.sinal, r.habilitacao, r.termos].filter((e) => e !== "ok")
                .length;
            return (
              <Link key={r.id} href={`/conta/reservas/${r.id}`}>
                <Card className="flex items-center gap-4 p-4 transition hover:shadow-md">
                  {/* eslint-disable-next-line @next/next/no-img-element */}
                  <img
                    src={m?.fotoUrl}
                    alt=""
                    className="h-20 w-28 shrink-0 rounded-xl object-cover"
                  />
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-2">
                      <span className="font-mono text-xs text-slate-400">
                        {r.id}
                      </span>
                      {statusBadge(r)}
                    </div>
                    <h3 className="mt-0.5 truncate font-semibold text-ink-900">
                      {m?.nome}
                    </h3>
                    <p className="text-sm text-slate-500">
                      {fmtDateTime(r.data)} · {r.duracaoHoras}h ·{" "}
                      {brl(r.valorEstimado)}
                    </p>
                    {r.status === "PENDENTE" && (
                      <p className="mt-1 text-xs font-medium text-amber-600">
                        {pend} pendência(s) para liberar o check-in
                      </p>
                    )}
                  </div>
                  <ChevronRight className="text-slate-300" />
                </Card>
              </Link>
            );
          })}
        </div>
      )}
    </div>
  );
}
