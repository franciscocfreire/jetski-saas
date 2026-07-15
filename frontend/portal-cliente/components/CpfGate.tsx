"use client";

import { useEffect, useState } from "react";
import { usePathname, useRouter } from "next/navigation";
import { useSession } from "next-auth/react";
import { Loader2 } from "lucide-react";
import { getSelf } from "@/lib/api";

/**
 * Gate de CPF (dedupe de contas): usuário autenticado sem CPF no perfil — e
 * que não se declarou estrangeiro — é levado a /conta/cpf antes de usar a
 * área da conta. Detecta colisão de CPF no primeiro minuto, antes de a pessoa
 * criar reservas numa conta duplicada (ex.: primeiro login com Google).
 *
 * Client-side por página (padrão do portal — sem middleware); /conta/cpf é
 * isenta para não criar loop.
 */
export function CpfGate({ children }: { children: React.ReactNode }) {
  const { data: session, status } = useSession();
  const pathname = usePathname();
  const router = useRouter();
  const [liberado, setLiberado] = useState(false);

  const isento = pathname === "/conta/cpf";

  useEffect(() => {
    if (isento || status !== "authenticated" || !session?.accessToken) {
      return;
    }
    let ativo = true;
    getSelf(session.accessToken)
      .then((self) => {
        if (!ativo) return;
        const semCpf = !self.identidade?.cpf && self.identidade?.estrangeiro !== true;
        if (semCpf) {
          router.replace(`/conta/cpf?next=${encodeURIComponent(pathname)}`);
        } else {
          setLiberado(true);
        }
      })
      .catch(() => {
        // backend indisponível — não tranca o portal por causa do gate
        if (ativo) setLiberado(true);
      });
    return () => {
      ativo = false;
    };
  }, [isento, status, session?.accessToken, pathname, router]);

  // Não autenticado (as páginas redirecionam para /login) ou página isenta
  if (isento || status !== "authenticated") {
    return <>{children}</>;
  }

  if (!liberado) {
    return (
      <div className="flex justify-center py-20 text-slate-400">
        <Loader2 className="animate-spin" />
      </div>
    );
  }

  return <>{children}</>;
}
