"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { useSession } from "next-auth/react";
import { Star, ChevronRight, Loader2, Waves } from "lucide-react";
import { minhasLocacoes, type LocacaoCliente } from "@/lib/api";
import { brl, fmtDateTime } from "@/lib/cn";
import { Badge, Button, Card, EmptyState, SectionTitle, SkeletonCards } from "@/components/ui";
import { statusLocacao } from "@/lib/status";

export default function LocacoesPage() {
  const { data: session, status } = useSession();
  const router = useRouter();
  const [locacoes, setLocacoes] = useState<LocacaoCliente[] | null>(null);
  const [erro, setErro] = useState(false);

  useEffect(() => {
    if (status === "unauthenticated") {
      router.replace("/login");
      return;
    }
    if (status === "authenticated" && session?.accessToken) {
      minhasLocacoes(session.accessToken)
        .then(setLocacoes)
        .catch(() => setErro(true));
    }
  }, [status, session?.accessToken, router]);

  if (status === "loading" || (!locacoes && !erro)) {
    return (
      <div>
        <SectionTitle sub="Seus passeios concluídos">Histórico</SectionTitle>
        <SkeletonCards n={3} />
      </div>
    );
  }

  return (
    <div>
      <SectionTitle sub="Seus passeios concluídos">Histórico</SectionTitle>

      {erro && (
        <Card className="p-6 text-sm text-red-700">
          Não foi possível carregar seu histórico — tente novamente.
        </Card>
      )}

      {locacoes && locacoes.length === 0 ? (
        <EmptyState
          icon={<Waves size={28} />}
          titulo="Nenhum passeio concluído ainda"
          texto="Seu histórico aparece aqui após o check-out — com fotos, recibo e espaço para avaliar a experiência."
          cta="Explorar jet skis"
          href="/"
        />
      ) : (
        <div className="grid gap-4">
          {locacoes?.map((l) => (
            <Link key={l.id} href={`/conta/locacoes/${l.id}`}>
              <Card className="flex items-center gap-4 p-4 transition hover:shadow-md">
                <div className="min-w-0 flex-1">
                  <h3 className="font-semibold text-ink-900">
                    {l.modeloNome ?? "Jet ski"}
                  </h3>
                  <p className="text-sm text-slate-500">
                    {l.lojaNome}
                    {l.dataCheckIn && <> · {fmtDateTime(l.dataCheckIn)}</>}
                    {l.minutosUsados != null && <> · {l.minutosUsados} min</>}
                    {l.valorTotal != null && <> · {brl(l.valorTotal)}</>}
                  </p>
                  <div className="mt-1">
                    <Badge tone={statusLocacao(l).tone}>
                      {l.avaliacaoNota != null && (
                        <Star size={11} className="fill-emerald-600" />
                      )}
                      {statusLocacao(l).label}
                    </Badge>
                  </div>
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
