"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useSession } from "next-auth/react";
import { Compass, CalendarCheck, History, User } from "lucide-react";

const TABS = [
  { href: "/", rotulo: "Explorar", icone: Compass, match: (p: string) => p === "/" || p.startsWith("/modelo") },
  { href: "/conta/reservas", rotulo: "Reservas", icone: CalendarCheck, match: (p: string) => p.startsWith("/conta/reservas") || p.startsWith("/reservar") },
  { href: "/conta/locacoes", rotulo: "Histórico", icone: History, match: (p: string) => p.startsWith("/conta/locacoes") },
  { href: "/conta/perfil", rotulo: "Perfil", icone: User, match: (p: string) => p.startsWith("/conta/perfil") || p.startsWith("/conta/notificacoes") },
];

/**
 * Navegação inferior mobile (D1): logado, o portal se comporta como app.
 * Some no desktop (md+, onde o Header já tem o nav) e deslogado.
 */
export function BottomNav() {
  const { status } = useSession();
  const pathname = usePathname() ?? "/";

  if (status !== "authenticated") return null;

  return (
    <nav
      className="fixed inset-x-0 bottom-0 z-40 border-t border-slate-200 bg-white/95 backdrop-blur md:hidden"
      style={{ paddingBottom: "env(safe-area-inset-bottom)" }}
      aria-label="Navegação principal"
    >
      <div className="mx-auto grid max-w-md grid-cols-4">
        {TABS.map((t) => {
          const ativo = t.match(pathname);
          const Icone = t.icone;
          return (
            <Link
              key={t.href}
              href={t.href}
              className={`flex min-h-[56px] flex-col items-center justify-center gap-0.5 text-[11px] font-medium ${
                ativo ? "text-brand-600" : "text-slate-400"
              }`}
            >
              <Icone size={21} strokeWidth={ativo ? 2.4 : 2} />
              {t.rotulo}
            </Link>
          );
        })}
      </div>
    </nav>
  );
}
