"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import {
  Activity,
  Building2,
  Coins,
  FileText,
  Gauge,
  LayoutGrid,
  ScrollText,
  Settings,
  ShieldCheck,
  Users,
} from "lucide-react";

/**
 * Shell do console. A navegação reflete o destino da spec
 * (PLATAFORMA_CONSOLE_SPEC.md §7): cada card da página monolítica de 775 linhas
 * do backoffice virou uma rota aqui. O que ainda não existe aparece marcado com
 * a fase — melhor mostrar o mapa incompleto do que esconder o plano.
 */
const NAV = [
  { href: "/", label: "Visão geral", icon: Gauge, fase: undefined as string | undefined },
  { href: "/empresas", label: "Empresas", icon: Building2 },
  { href: "/creditos", label: "Créditos", icon: Coins },
  { href: "/faturamento", label: "Faturamento", icon: FileText },
  { href: "/emissoes", label: "Emissões", icon: Activity },
  { href: "/catalogo", label: "Catálogo", icon: LayoutGrid },
  { href: "/configuracoes", label: "Configurações", icon: Settings },
  { href: "/operadores", label: "Operadores", icon: Users, fase: "F2" },
  { href: "/auditoria", label: "Auditoria", icon: ScrollText, fase: "F5" },
  { href: "/saude", label: "Saúde", icon: ShieldCheck, fase: "F5" },
];

export function Shell({
  children,
  email,
}: {
  children: React.ReactNode;
  email?: string | null;
}) {
  const pathname = usePathname();

  return (
    <div className="flex min-h-screen">
      <aside className="hidden w-64 shrink-0 flex-col bg-brand-900 text-brand-100 md:flex">
        <div className="flex items-center gap-2 px-5 py-6">
          <ShieldCheck className="h-6 w-6 text-gold-500" />
          <div>
            <div className="font-display text-lg leading-tight text-white">Meu Jet</div>
            <div className="text-xs tracking-wide text-brand-300">CONSOLE DA PLATAFORMA</div>
          </div>
        </div>

        <nav className="flex-1 space-y-0.5 px-3 pb-4">
          {NAV.map(({ href, label, icon: Icon, fase }) => {
            const ativo = href === "/" ? pathname === "/" : pathname.startsWith(href);
            return (
              <Link
                key={href}
                href={href}
                className={`flex items-center gap-3 rounded-md px-3 py-2 text-sm transition ${
                  ativo
                    ? "bg-brand-700 text-white"
                    : "text-brand-200 hover:bg-brand-800 hover:text-white"
                }`}
              >
                <Icon className="h-4 w-4 shrink-0" />
                <span className="flex-1">{label}</span>
                {fase && (
                  <span className="rounded bg-brand-800 px-1.5 py-0.5 text-[10px] text-brand-300">
                    {fase}
                  </span>
                )}
              </Link>
            );
          })}
        </nav>

        <div className="border-t border-brand-800 px-5 py-4 text-xs text-brand-300">
          <div className="truncate">{email ?? "—"}</div>
          <a href="/api/logout" className="mt-1 inline-block text-brand-200 hover:text-white">
            Sair
          </a>
        </div>
      </aside>

      <div className="flex min-w-0 flex-1 flex-col">
        <header className="flex items-center justify-between border-b border-slate-200 bg-white px-6 py-3 md:hidden">
          <div className="flex items-center gap-2">
            <ShieldCheck className="h-5 w-5 text-brand-600" />
            <span className="font-display text-base">Console da Plataforma</span>
          </div>
          <a href="/api/logout" className="text-sm text-ink-500">
            Sair
          </a>
        </header>
        <main className="min-w-0 flex-1 p-6">{children}</main>
      </div>
    </div>
  );
}
