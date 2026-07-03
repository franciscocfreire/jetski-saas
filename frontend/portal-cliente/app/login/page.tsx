"use client";

import Link from "next/link";
import { Anchor, ShieldCheck, LogIn } from "lucide-react";
import { signIn } from "next-auth/react";
import { Button, Card } from "@/components/ui";

/**
 * Login REAL: Keycloak (OIDC + PKCE, client jetski-customer-portal).
 * A tela de credenciais é do Keycloak — aqui só disparamos o fluxo.
 */
export default function LoginPage() {
  function entrar() {
    signIn("keycloak", { callbackUrl: "/conta/perfil" });
  }

  return (
    <div className="mx-auto max-w-sm py-6">
      <div className="flex flex-col items-center">
        <span className="grid h-12 w-12 place-items-center rounded-2xl bg-brand-600 text-white">
          <Anchor size={22} />
        </span>
        <h1 className="mt-3 text-2xl font-bold text-ink-900">
          Entrar no Meu Jet
        </h1>
        <p className="text-sm text-slate-500">Acesse sua conta de cliente</p>
      </div>

      <Card className="mt-6 p-6">
        <Button className="w-full gap-2" size="lg" onClick={entrar}>
          <LogIn size={16} /> Entrar com sua conta
        </Button>
        <p className="mt-3 text-center text-xs text-slate-400">
          Você será direcionado ao login seguro e voltará para cá.
        </p>
        <div className="my-4 flex items-center gap-2 text-xs text-slate-300">
          <div className="h-px flex-1 bg-slate-200" /> ou{" "}
          <div className="h-px flex-1 bg-slate-200" />
        </div>
        <p className="text-center text-sm text-slate-500">
          Não tem conta?{" "}
          <Link href="/cadastro" className="font-medium text-brand-600">
            Cadastre-se
          </Link>
        </p>
      </Card>
      <p className="mt-4 flex items-center justify-center gap-1 text-center text-xs text-slate-400">
        <ShieldCheck size={13} /> Login via Keycloak (OIDC + PKCE)
      </p>
    </div>
  );
}
