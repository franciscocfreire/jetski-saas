"use client";

import { withBase } from "@/lib/base";
import { Suspense, useEffect, useState } from "react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { Anchor, ShieldCheck, LogIn, Loader2, RefreshCcw } from "lucide-react";
import { signIn, useSession } from "next-auth/react";
import { Button, Card } from "@/components/ui";
import { GoogleIcon } from "@/components/GoogleIcon";

/**
 * Login REAL: Keycloak (OIDC + PKCE, client jetski-customer-portal).
 * A tela de credenciais (senha + Google) é do Keycloak — esta rota é só um
 * TRAMPOLIM: chegou sem erro → dispara o fluxo sozinha (zero fricção; com
 * SSO ativo o usuário nem vê tela). Ela continua existindo como ponto de
 * pouso de ERROS do callback (sessão PKCE expirada etc.), onde mostra o
 * aviso e os botões manuais.
 *
 * O disparo trava após o clique (duplo clique gera dois fluxos PKCE e o
 * segundo invalida o cookie do primeiro → callback falha).
 */
export default function LoginPage() {
  return (
    <Suspense fallback={<div className="py-24 text-center text-slate-400">Carregando…</div>}>
      <LoginInner />
    </Suspense>
  );
}

function LoginInner() {
  const params = useSearchParams();
  const router = useRouter();
  const { status } = useSession();
  const veioComErro = !!params.get("error");
  const [entrando, setEntrando] = useState(false);

  function entrar() {
    if (entrando) return;
    setEntrando(true);
    signIn("keycloak", { callbackUrl: withBase("/conta/perfil") });
  }

  function entrarComGoogle() {
    if (entrando) return;
    setEntrando(true);
    // kc_idp_hint: o Keycloak pula a própria tela e vai direto ao Google.
    // IdP desabilitado (dev sem credenciais) → hint ignorado, tela normal.
    signIn("keycloak", { callbackUrl: withBase("/conta/perfil") }, { kc_idp_hint: "google" });
  }

  // Fluxo normal (sem erro): já logado → perfil; deslogado → Keycloak direto.
  useEffect(() => {
    if (veioComErro || status === "loading") return;
    if (status === "authenticated") {
      router.replace("/conta/perfil");
      return;
    }
    entrar();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [veioComErro, status]);

  if (!veioComErro) {
    return (
      <div className="flex flex-col items-center gap-3 py-24 text-slate-400">
        <Loader2 className="animate-spin" />
        <p className="text-sm">Abrindo login seguro…</p>
      </div>
    );
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
        <p className="text-sm text-slate-500">Entre com seu e-mail ou CPF (só números)</p>
      </div>

      <div className="mt-5 flex items-start gap-2 rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900">
        <RefreshCcw size={15} className="mt-0.5 shrink-0" />
        <span>
          A sessão de login anterior expirou ou foi interrompida — é só
          entrar novamente.
        </span>
      </div>

      <Card className="mt-6 p-6">
        <Button className="w-full gap-2" size="lg" onClick={entrar} disabled={entrando}>
          {entrando ? <Loader2 size={16} className="animate-spin" /> : <LogIn size={16} />}
          {entrando ? "Abrindo login seguro…" : "Entrar com sua conta"}
        </Button>
        <Button
          variant="outline"
          className="mt-3 w-full gap-2"
          size="lg"
          onClick={entrarComGoogle}
          disabled={entrando}
        >
          <GoogleIcon size={16} /> Entrar com Google
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
