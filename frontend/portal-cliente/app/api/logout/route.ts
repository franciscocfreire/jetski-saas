import { auth } from "@/lib/auth";
import { withBase } from "@/lib/base";
import { cookies } from "next/headers";
import { NextResponse } from "next/server";

/**
 * Logout FEDERADO server-side (mesmo padrão do backoffice): o Keycloak vive
 * no host dedicado sso.* e só o servidor conhece o issuer (KEYCLOAK_ISSUER) —
 * o browser não tem mais ${origin}/realms/... (rotas legadas do nginx
 * removidas). Limpa os cookies do NextAuth e encerra a sessão SSO no
 * Keycloak (id_token_hint), voltando ao /login do portal.
 */
export async function GET() {
  const base = process.env.NEXTAUTH_URL || "http://localhost:3003";
  const destino = `${base}${withBase("/login")}`;

  let target = destino;
  try {
    const session = await auth();
    const idToken = session?.idToken;
    const issuer = process.env.KEYCLOAK_ISSUER;
    if (idToken && issuer) {
      target =
        `${issuer}/protocol/openid-connect/logout` +
        `?post_logout_redirect_uri=${encodeURIComponent(destino)}` +
        `&id_token_hint=${encodeURIComponent(idToken)}`;
    }
  } catch {
    // sem sessão legível, cai no redirect simples para o /login
  }

  const cookieStore = await cookies();
  for (const cookie of cookieStore.getAll()) {
    if (cookie.name.includes("authjs") || cookie.name.includes("next-auth")) {
      cookieStore.delete(cookie.name);
    }
  }

  return NextResponse.redirect(target);
}
