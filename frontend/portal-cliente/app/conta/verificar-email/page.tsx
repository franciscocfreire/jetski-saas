"use client";

import Link from "next/link";
import { useState } from "react";
import { MailCheck, CheckCircle2, ShieldCheck } from "lucide-react";
import { useStore } from "@/lib/store";
import { CLIENTE } from "@/lib/mock";
import { Button, Card } from "@/components/ui";

export default function VerificarEmailPage() {
  const auth = useStore((s) => s.auth);
  const verifyEmail = useStore((s) => s.verifyEmail);
  const [codigo, setCodigo] = useState("");

  const jaVerificado = auth.emailVerified;

  return (
    <div className="mx-auto max-w-md py-4">
      <div className="flex flex-col items-center text-center">
        <span className="grid h-12 w-12 place-items-center rounded-2xl bg-brand-50 text-brand-700">
          <MailCheck size={24} />
        </span>
        <h1 className="mt-3 text-2xl font-bold text-ink-900">
          Verifique seu e-mail
        </h1>
        <p className="mt-1 text-sm text-slate-500">
          Enviamos um código de 6 dígitos para <b>{CLIENTE.email}</b>.
        </p>
      </div>

      {jaVerificado ? (
        <Card className="mt-6 flex flex-col items-center gap-3 p-8 text-center">
          <CheckCircle2 className="text-emerald-500" size={44} />
          <h2 className="text-lg font-semibold text-ink-900">
            E-mail verificado
          </h2>
          <p className="text-sm text-slate-500">
            Sua conta está completa. Suas reservas podem ser garantidas.
          </p>
          <Button href="/conta/reservas" variant="outline">
            Ir para minhas reservas
          </Button>
        </Card>
      ) : (
        <Card className="mt-6 p-6">
          <input
            inputMode="numeric"
            maxLength={6}
            value={codigo}
            onChange={(e) => setCodigo(e.target.value.replace(/\D/g, ""))}
            placeholder="••••••"
            className="h-14 w-full rounded-xl border border-slate-300 text-center text-2xl tracking-[0.6em] outline-none focus:border-brand-500 focus:ring-2 focus:ring-brand-100"
          />
          <Button
            className="mt-4 w-full"
            size="lg"
            disabled={codigo.length < 6}
            onClick={verifyEmail}
          >
            Confirmar código
          </Button>

          <div className="mt-4 text-center text-sm text-slate-500">
            Não recebeu?{" "}
            <button className="font-medium text-brand-600">
              Reenviar e-mail
            </button>
          </div>

          {/* Atalho de demonstração */}
          <button
            onClick={verifyEmail}
            className="mt-4 w-full rounded-lg border border-slate-200 bg-slate-50 py-2 text-xs font-medium text-slate-500 hover:bg-slate-100"
          >
            ▶︎ Simular clique no link do e-mail (demo)
          </button>
        </Card>
      )}

      <p className="mt-4 flex items-center justify-center gap-1 text-center text-xs text-slate-400">
        <ShieldCheck size={13} /> No produto real, a verificação é feita pelo
        Keycloak (Verify Email · claim <code>email_verified</code>).
      </p>

      <div className="mt-4 text-center">
        <Link
          href="/conta/reservas"
          className="text-xs text-slate-400 hover:text-slate-600"
        >
          ← Voltar
        </Link>
      </div>
    </div>
  );
}
