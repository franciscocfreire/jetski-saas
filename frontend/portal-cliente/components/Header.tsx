"use client";

import Link from "next/link";
import { Anchor, CalendarCheck, User, Globe } from "lucide-react";
import { useStore } from "@/lib/store";
import { Button } from "./ui";

export function Header() {
  const auth = useStore((s) => s.auth);
  const signup = useStore((s) => s.signup);
  const logout = useStore((s) => s.logout);

  return (
    <header className="no-print sticky top-0 z-30 border-b border-slate-200 bg-white/85 backdrop-blur">
      <div className="mx-auto flex h-16 max-w-6xl items-center gap-4 px-4">
        <Link href="/" className="flex items-center gap-2 font-bold text-ink-900">
          <span className="grid h-9 w-9 place-items-center rounded-xl bg-brand-600 text-white">
            <Anchor size={18} />
          </span>
          <span className="hidden sm:block">
            Jet<span className="text-brand-600">Riders</span>
          </span>
        </Link>

        <nav className="ml-2 hidden items-center gap-1 text-sm md:flex">
          <Link href="/" className="rounded-lg px-3 py-2 text-slate-600 hover:bg-slate-100">
            Explorar
          </Link>
          {auth.logged && (
            <>
              <Link
                href="/conta/reservas"
                className="rounded-lg px-3 py-2 text-slate-600 hover:bg-slate-100"
              >
                Minhas reservas
              </Link>
              <Link
                href="/conta/locacoes"
                className="rounded-lg px-3 py-2 text-slate-600 hover:bg-slate-100"
              >
                Histórico
              </Link>
            </>
          )}
        </nav>

        <div className="ml-auto flex items-center gap-2">
          <button
            className="hidden items-center gap-1 rounded-lg px-2.5 py-2 text-sm text-slate-500 hover:bg-slate-100 sm:flex"
            title="Idioma (protótipo)"
          >
            <Globe size={16} /> PT
          </button>
          {auth.logged ? (
            <div className="flex items-center gap-2">
              <Link
                href="/conta/reservas"
                className="hidden items-center gap-2 rounded-xl bg-slate-100 px-3 py-2 text-sm font-medium text-slate-700 sm:flex"
              >
                <CalendarCheck size={16} /> {auth.nome?.split(" ")[0]}
              </Link>
              <Button variant="ghost" size="sm" onClick={logout}>
                Sair
              </Button>
            </div>
          ) : (
            <>
              <Button variant="ghost" size="sm" href="/login">
                Entrar
              </Button>
              <Button
                size="sm"
                onClick={() => signup("Marina Albuquerque")}
                className="hidden sm:inline-flex"
              >
                <User size={16} /> Criar conta
              </Button>
            </>
          )}
        </div>
      </div>
    </header>
  );
}
