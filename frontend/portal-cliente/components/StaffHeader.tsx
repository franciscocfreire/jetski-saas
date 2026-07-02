"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { Anchor, Inbox, UserPlus, ExternalLink } from "lucide-react";
import { Logo } from "./Logo";
import { cn } from "@/lib/cn";
import { getLoja } from "@/lib/mock";

/** Loja do operador no protótipo (viria da sessão no produto real). */
const LOJA_ATUAL = getLoja("loja-jetsave");

const NAV = [
  { href: "/staff", label: "Painel", icon: Anchor, exact: true },
  { href: "/staff/sinais", label: "Sinais a validar", icon: Inbox },
  { href: "/staff/embarque", label: "Atendimento de balcão", icon: UserPlus },
];

export function StaffHeader() {
  const path = usePathname();
  return (
    <header className="no-print sticky top-0 z-30 border-b border-slate-700 bg-ink-900 text-slate-100">
      <div className="mx-auto flex h-16 max-w-6xl items-center gap-4 px-4">
        <Link href="/staff" className="flex items-center gap-2">
          <Logo theme="dark" size={20} />
          <span className="ml-1 rounded-md bg-slate-700 px-2 py-0.5 text-[10px] font-semibold tracking-wide text-slate-200">
            BACKOFFICE
          </span>
        </Link>

        <nav className="ml-3 hidden items-center gap-1 text-sm md:flex">
          {NAV.map((n) => {
            const active = n.exact ? path === n.href : path?.startsWith(n.href);
            return (
              <Link
                key={n.href}
                href={n.href}
                className={cn(
                  "flex items-center gap-1.5 rounded-lg px-3 py-2",
                  active
                    ? "bg-slate-700 text-white"
                    : "text-slate-300 hover:bg-slate-800"
                )}
              >
                <n.icon size={15} /> {n.label}
              </Link>
            );
          })}
        </nav>

        <div className="ml-auto flex items-center gap-3">
          <Link
            href="/"
            className="hidden items-center gap-1 text-xs text-slate-400 hover:text-slate-200 sm:flex"
          >
            <ExternalLink size={13} /> Portal do cliente
          </Link>
          <div className="flex items-center gap-2 rounded-xl bg-slate-800 px-3 py-1.5 text-sm">
            <span
              className="grid h-6 w-6 place-items-center rounded-full text-xs font-bold text-white"
              style={{ backgroundColor: LOJA_ATUAL?.branding?.corPrimaria ?? "#33689a" }}
            >
              O
            </span>
            <span className="hidden sm:block">Operador · {LOJA_ATUAL?.nome ?? "Jet Save"}</span>
          </div>
        </div>
      </div>
    </header>
  );
}
