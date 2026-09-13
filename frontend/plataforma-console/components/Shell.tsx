"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useState } from "react";
import { TamanhoFonte } from "./TamanhoFonte";
import {
  Activity,
  Building2,
  Coins,
  FileText,
  Gauge,
  HeartPulse,
  LayoutGrid,
  Menu,
  ScrollText,
  Settings,
  ShieldCheck,
  Users,
  X,
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
  // Exclusivos de PLATFORM_ADMIN no platform.rego — escondidos para os demais,
  // senão o menu ofereceria o que a API vai negar com 403.
  { href: "/configuracoes", label: "Configurações", icon: Settings, somenteAdmin: true },
  { href: "/operadores", label: "Operadores", icon: Users, somenteAdmin: true },
  { href: "/auditoria", label: "Auditoria", icon: ScrollText },
  { href: "/saude", label: "Saúde", icon: HeartPulse },
];

type Item = (typeof NAV)[number];

/** Lista de links — a mesma no menu fixo (desktop) e na gaveta (celular). */
function Links({
  itens,
  pathname,
  onNavegar,
}: {
  itens: Item[];
  pathname: string;
  onNavegar?: () => void;
}) {
  return (
    <nav className="flex-1 space-y-0.5 px-3 pb-4">
      {itens.map(({ href, label, icon: Icon, fase }) => {
        const ativo = href === "/" ? pathname === "/" : pathname.startsWith(href);
        return (
          <Link
            key={href}
            href={href}
            onClick={onNavegar}
            aria-current={ativo ? "page" : undefined}
            className={`flex items-center gap-3 rounded-md px-3 py-2.5 text-sm transition ${
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
  );
}

/** Identidade do operador, ajuste de leitura e saída — no pé dos dois menus. */
function Rodape({ email, papeis }: { email?: string | null; papeis: string[] }) {
  return (
    <div className="border-t border-brand-800 px-5 py-4 text-xs text-brand-300">
      <div className="mb-3">
        <TamanhoFonte />
      </div>
      <div className="truncate">{email ?? "—"}</div>
      {papeis.length > 0 && (
        <div className="mt-0.5 truncate text-[10px] text-brand-400">
          {papeis.map((p) => p.replace("PLATFORM_", "").toLowerCase()).join(" · ")}
        </div>
      )}
      <a href="/api/logout" className="mt-1 inline-block text-brand-200 hover:text-white">
        Sair
      </a>
    </div>
  );
}

export function Shell({
  children,
  email,
  admin = true,
  papeis = [],
}: {
  children: React.ReactNode;
  email?: string | null;
  /** PLATFORM_ADMIN — libera os itens exclusivos do menu. */
  admin?: boolean;
  papeis?: string[];
}) {
  const pathname = usePathname();
  const itens = NAV.filter((n) => admin || !("somenteAdmin" in n && n.somenteAdmin));

  // No celular o menu fixo não cabe: abaixo de `md` a navegação virava NADA —
  // o header só tinha título e "Sair", e as rotas só existiam digitando a URL.
  const [gaveta, setGaveta] = useState(false);

  // Fecha ao trocar de rota (o clique no link navega e a gaveta some) e ao Esc.
  useEffect(() => setGaveta(false), [pathname]);
  useEffect(() => {
    if (!gaveta) return;
    const aoTeclar = (e: KeyboardEvent) => {
      if (e.key === "Escape") setGaveta(false);
    };
    document.addEventListener("keydown", aoTeclar);
    return () => document.removeEventListener("keydown", aoTeclar);
  }, [gaveta]);

  return (
    <div className="flex min-h-screen">
      {/* Menu fixo: só a partir de md */}
      <aside className="hidden w-64 max-w-[40vw] shrink-0 flex-col bg-brand-900 text-brand-100 md:flex">
        <div className="flex items-center gap-2 px-5 py-6">
          <ShieldCheck className="h-6 w-6 text-gold-500" />
          <div>
            <div className="font-display text-lg leading-tight text-white">Meu Jet</div>
            <div className="text-xs tracking-wide text-brand-300">CONSOLE DA PLATAFORMA</div>
          </div>
        </div>
        <Links itens={itens} pathname={pathname} />
        <Rodape email={email} papeis={papeis} />
      </aside>

      {/* Gaveta do celular */}
      {gaveta && (
        <div className="fixed inset-0 z-50 md:hidden" role="dialog" aria-modal="true" aria-label="Menu">
          <button
            type="button"
            aria-label="Fechar menu"
            onClick={() => setGaveta(false)}
            className="absolute inset-0 h-full w-full cursor-default bg-black/50"
          />
          <div className="absolute inset-y-0 left-0 flex w-72 max-w-[85%] flex-col overflow-y-auto bg-brand-900 text-brand-100 shadow-xl">
            <div className="flex items-center justify-between px-5 py-5">
              <div className="flex items-center gap-2">
                <ShieldCheck className="h-6 w-6 text-gold-500" />
                <div>
                  <div className="font-display text-lg leading-tight text-white">Meu Jet</div>
                  <div className="text-[10px] tracking-wide text-brand-300">
                    CONSOLE DA PLATAFORMA
                  </div>
                </div>
              </div>
              <button
                type="button"
                aria-label="Fechar menu"
                onClick={() => setGaveta(false)}
                className="rounded-md p-1.5 text-brand-200 hover:bg-brand-800 hover:text-white"
              >
                <X className="h-5 w-5" />
              </button>
            </div>
            <Links itens={itens} pathname={pathname} onNavegar={() => setGaveta(false)} />
            <Rodape email={email} papeis={papeis} />
          </div>
        </div>
      )}

      <div className="flex min-w-0 flex-1 flex-col">
        <header className="flex items-center justify-between border-b border-slate-200 bg-white px-4 py-3 md:hidden">
          <div className="flex min-w-0 items-center gap-2">
            <button
              type="button"
              aria-label="Abrir menu"
              aria-expanded={gaveta}
              onClick={() => setGaveta(true)}
              className="-ml-1 rounded-md p-2 text-ink-700 hover:bg-slate-100"
            >
              <Menu className="h-5 w-5" />
            </button>
            <ShieldCheck className="h-5 w-5 shrink-0 text-brand-600" />
            <span className="truncate font-display text-base">Console da Plataforma</span>
          </div>
          <div className="flex shrink-0 items-center gap-2">
            {/* a um toque no celular: quem precisa de fonte maior não deveria
                ter que abrir o menu antes de conseguir ler a tela */}
            <TamanhoFonte variante="claro" compacto />
            <a href="/api/logout" className="text-sm text-ink-500">
              Sair
            </a>
          </div>
        </header>
        <main className="min-w-0 flex-1 p-4 md:p-6">{children}</main>
      </div>
    </div>
  );
}
