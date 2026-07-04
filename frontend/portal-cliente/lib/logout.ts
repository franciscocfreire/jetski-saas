import { signOut } from "next-auth/react";
import { withBase } from "@/lib/base";

/**
 * Logout FEDERADO (fix do "Sair que não sai"): o signOut do NextAuth limpa
 * só o cookie do portal — a sessão SSO do Keycloak continua viva e o
 * próximo "Entrar" loga de volta sem pedir senha. Aqui encerramos as duas:
 * cookie local + end_session no Keycloak (id_token_hint) com retorno ao
 * /login do portal.
 */
export async function sairDaConta(idToken?: string) {
  await signOut({ redirect: false });
  const base = window.location.origin;
  const destino = `${base}${withBase("/login")}`;
  if (idToken) {
    const url =
      `${base}/realms/jetski-saas/protocol/openid-connect/logout` +
      `?post_logout_redirect_uri=${encodeURIComponent(destino)}` +
      `&id_token_hint=${encodeURIComponent(idToken)}`;
    window.location.href = url;
  } else {
    window.location.href = destino;
  }
}
