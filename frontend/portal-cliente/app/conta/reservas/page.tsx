"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { useSession } from "next-auth/react";
import { CalendarX2, ChevronRight, Loader2 } from "lucide-react";
import { minhasReservas, type ReservaCliente } from "@/lib/api";
import { brl, fmtDateTime } from "@/lib/cn";
import { Badge, Button, Card, EmptyState, SectionTitle, SkeletonCards } from "@/components/ui";
import { statusReserva } from "@/lib/status";

function statusBadge(r: ReservaCliente) {
  const v = statusReserva(r);
  return <Badge tone={v.tone}>{v.label}</Badge>;
}

export default function ReservasPage() {
  const { data: session, status } = useSession();
  const router = useRouter();
  const [reservas, setReservas] = useState<ReservaCliente[] | null>(null);
  const [erro, setErro] = useState(false);

  useEffect(() => {
    if (status === "unauthenticated") {
      router.replace("/login");
      return;
    }
    if (status === "authenticated" && session?.accessToken) {
      minhasReservas(session.accessToken)
        .then(setReservas)
        .catch(() => setErro(true));
    }
  }, [status, session?.accessToken, router]);

  if (status === "loading" || (!reservas && !erro)) {
    return (
      <div>
        <SectionTitle sub="Acompanhe e conclua as pendências de cada reserva">
          Minhas reservas
        </SectionTitle>
        <SkeletonCards n={3} />
      </div>
    );
  }

  return (
    <div>
      <SectionTitle sub="Acompanhe e conclua as pendências de cada reserva">
        Minhas reservas
      </SectionTitle>

      {erro && (
        <Card className="p-6 text-sm text-red-700">
          Não foi possível carregar suas reservas — tente novamente.
        </Card>
      )}

      {reservas && reservas.length === 0 ? (
        <EmptyState
          icon={<CalendarX2 size={28} />}
          titulo="Seu próximo passeio começa aqui"
          texto="Você ainda não tem reservas. Escolha um jet ski, reserve em minutos e pague o sinal por PIX."
          cta="Explorar jet skis"
          href="/"
        />
      ) : (
        <div className="grid gap-4">
          {reservas?.map((r) => (
            <Link key={r.id} href={`/conta/reservas/${r.id}`}>
              <Card className="flex items-center gap-4 p-4 transition hover:shadow-md">
                <div className="min-w-0 flex-1">
                  <div className="flex items-center gap-2">{statusBadge(r)}</div>
                  <h3 className="mt-0.5 truncate font-semibold text-ink-900">
                    {r.modeloNome}
                  </h3>
                  <p className="text-sm text-slate-500">
                    {r.lojaNome} · {fmtDateTime(r.dataInicio)} · {brl(r.pagamento.valorTotal)}
                  </p>
                  {r.pagamento.status === "AGUARDANDO" && r.status === "PENDENTE" && (
                    <p className="mt-1 text-xs font-medium text-amber-600">
                      Envie o pagamento para garantir — a pré-reserva expira em 24h
                    </p>
                  )}
                </div>
                <ChevronRight className="text-slate-300" />
              </Card>
            </Link>
          ))}
        </div>
      )}
    </div>
  );
}
