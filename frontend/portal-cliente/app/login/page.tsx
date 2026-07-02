"use client";

import { useRouter } from "next/navigation";
import { Anchor, ShieldCheck } from "lucide-react";
import { useStore } from "@/lib/store";
import { Button, Card, Field, inputCls } from "@/components/ui";

export default function LoginPage() {
  const login = useStore((s) => s.login);
  const signup = useStore((s) => s.signup);
  const router = useRouter();

  function entrar() {
    login("Marina Albuquerque");
    router.push("/conta/reservas");
  }

  function cadastrar() {
    signup("Marina Albuquerque");
    router.push("/conta/verificar-email");
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
        <div className="grid gap-3">
          <Field label="E-mail">
            <input className={inputCls} defaultValue="marina.alb@example.com" />
          </Field>
          <Field label="Senha">
            <input className={inputCls} type="password" defaultValue="••••••••" />
          </Field>
        </div>
        <Button className="mt-4 w-full" size="lg" onClick={entrar}>
          Entrar
        </Button>
        <div className="my-4 flex items-center gap-2 text-xs text-slate-300">
          <div className="h-px flex-1 bg-slate-200" /> ou{" "}
          <div className="h-px flex-1 bg-slate-200" />
        </div>
        <Button variant="outline" className="w-full" onClick={entrar}>
          Entrar com Keycloak (OIDC)
        </Button>
        <p className="mt-4 text-center text-sm text-slate-500">
          Não tem conta?{" "}
          <button onClick={cadastrar} className="font-medium text-brand-600">
            Cadastre-se
          </button>
        </p>
      </Card>
      <p className="mt-4 flex items-center justify-center gap-1 text-center text-xs text-slate-400">
        <ShieldCheck size={13} /> Login real será via Keycloak (PKCE) — aqui é
        mock.
      </p>
    </div>
  );
}
