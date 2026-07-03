"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { useSession } from "next-auth/react";
import { Star, ChevronRight, Loader2, Waves } from "lucide-react";
import { minhasLocacoes, type LocacaoCliente } from "@/lib/api";
import { brl, fmtDateTime } from "@/lib/cn";
import { Badge, Button, Card, SectionTitle } from "@/components/ui";

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
      <div className="flex justify-center py-20 text-slate-400">
        <Loader2 className="animate-spin" />
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
        <Card className="flex flex-col items-center gap-3 p-12 text-center">
          <Waves className="text-slate-300" size={40} />
          <p className="text-slate-500">
            Nenhum passeio concluído ainda — seu histórico aparece aqui após o
            check-out.
          </p>
          <Button href="/">Explorar jet skis</Button>
        </Card>
      ) : (
        <div className="grid gap-4">
          {locacoes?.map((l) => (
            <Link key={l.id} href={`/conta/locacoes/${l.id}`}>
              <Card className="flex items-center gap-4 p-4 transition hover:shadow-md">
                <div className="min-w-0 flex-1">
                  <span className="font-mono text-xs text-slate-400">
                    {l.id.slice(0, 8)}
                  </span>
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
                    {l.status !== "FINALIZADA" ? (
                      <Badge tone="brand">Em curso</Badge>
                    ) : l.avaliacaoNota != null ? (
                      <Badge tone="green">
                        <Star size={11} className="fill-emerald-600" /> Avaliada ({l.avaliacaoNota}★)
                      </Badge>
                    ) : (
                      <Badge tone="amber">Avalie sua experiência</Badge>
                    )}
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
