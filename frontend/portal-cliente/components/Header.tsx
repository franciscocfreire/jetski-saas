"use client";

import { withBase } from "@/lib/base";
import Link from "next/link";
import { CalendarCheck, User, Globe } from "lucide-react";
import { useSession, signOut } from "next-auth/react";
import { Logo } from "./Logo";
import { Button } from "./ui";

export function Header() {
  const { data: session, status } = useSession();
  const logged = status === "authenticated";
  const nome = session?.user?.name ?? undefined;

  return (
    <header className="no-print sticky top-0 z-30 border-b border-slate-200 bg-white/85 backdrop-blur">
      <div className="mx-auto flex h-16 max-w-6xl items-center gap-4 px-4">
        <Link href="/" className="flex items-center">
          <Logo theme="light" size={22} />
        </Link>

        <nav className="ml-2 hidden items-center gap-1 text-sm md:flex">
          <Link href="/" className="rounded-lg px-3 py-2 text-slate-600 hover:bg-slate-100">
            Explorar
          </Link>
          {logged && (
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
              <Link
                href="/conta/perfil"
                className="rounded-lg px-3 py-2 text-slate-600 hover:bg-slate-100"
              >
                Perfil
              </Link>
            </>
          )}
        </nav>

        <div className="ml-auto flex items-center gap-2">
          <button
            className="hidden items-center gap-1 rounded-lg px-2.5 py-2 text-sm text-slate-500 hover:bg-slate-100 sm:flex"
            title="Idioma (em breve)"
          >
            <Globe size={16} /> PT
          </button>
          {logged ? (
            <div className="flex items-center gap-2">
              <Link
                href="/conta/reservas"
                className="hidden items-center gap-2 rounded-xl bg-slate-100 px-3 py-2 text-sm font-medium text-slate-700 sm:flex"
              >
                <CalendarCheck size={16} /> {nome?.split(" ")[0] ?? "Minha conta"}
              </Link>
              <Button variant="ghost" size="sm" onClick={() => signOut({ callbackUrl: withBase("/") })}>
                Sair
              </Button>
            </div>
          ) : (
            <>
              <Button variant="ghost" size="sm" href="/login">
                Entrar
              </Button>
              <Button size="sm" href="/cadastro" className="hidden sm:inline-flex">
                <User size={16} /> Criar conta
              </Button>
            </>
          )}
        </div>
      </div>
    </header>
  );
}
