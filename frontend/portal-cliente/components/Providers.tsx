"use client";

import { useEffect, useRef } from "react";
import { SessionProvider, signIn, useSession } from "next-auth/react";
import { withBase } from "@/lib/base";
import { ToastProvider } from "@/components/Toast";

/**
 * O access token do Keycloak vive 5 min; quando o refresh falha (sessão SSO
 * expirada — idle de 30 min), o jwt callback marca RefreshAccessTokenError e
 * o token da sessão fica inutilizável (401 em tudo). Aqui reautenticamos:
 * se a sessão SSO ainda existir no Keycloak, volta sem pedir senha; senão o
 * cliente cai no login limpo em vez de uma tela quebrada.
 */
function SessionErrorGuard() {
  const { data: session } = useSession();
  const disparou = useRef(false);
  useEffect(() => {
    if (session?.error === "RefreshAccessTokenError" && !disparou.current) {
      disparou.current = true;
      signIn("keycloak", {
        callbackUrl: window.location.pathname + window.location.search,
      });
    }
  }, [session?.error]);
  return null;
}

export function Providers({ children }: { children: React.ReactNode }) {
  // basePath explícito: sem ele o client do NextAuth (signIn/useSession) chama
  // /api/auth na raiz do host — que pertence ao backoffice.
  // refetchInterval 240s < 300s do access token: o jwt callback renova o token
  // antes de expirar enquanto a aba estiver aberta (sem isso, só o focus da
  // janela atualizava a sessão e as chamadas à API tomavam 401 após 5 min).
  return (
    <SessionProvider basePath={withBase("/api/auth")} refetchInterval={240}>
      <SessionErrorGuard />
      <ToastProvider>{children}</ToastProvider>
    </SessionProvider>
  );
}
