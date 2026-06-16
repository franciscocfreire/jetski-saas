"use client";

import Link from "next/link";
import { MailWarning, ArrowRight } from "lucide-react";
import { useStore } from "@/lib/store";

export function EmailBanner() {
  const auth = useStore((s) => s.auth);
  if (!auth.logged || auth.emailVerified) return null;

  return (
    <div className="mb-5 flex flex-col gap-3 rounded-2xl border border-amber-200 bg-amber-50 p-4 sm:flex-row sm:items-center">
      <MailWarning className="shrink-0 text-amber-600" />
      <div className="flex-1 text-sm text-amber-900">
        <b>Confirme seu e-mail para garantir suas reservas.</b> Sua conta está
        restrita até a verificação — uma pré-reserva não confirmada expira em
        24h.
      </div>
      <Link
        href="/conta/verificar-email"
        className="inline-flex h-10 items-center justify-center gap-2 rounded-xl bg-amber-500 px-4 text-sm font-semibold text-white hover:bg-amber-600"
      >
        Verificar agora <ArrowRight size={15} />
      </Link>
    </div>
  );
}
