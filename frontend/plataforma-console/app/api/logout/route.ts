import { cookies } from "next/headers";
import { NextResponse } from "next/server";
import { auth } from "@/lib/auth";

/**
 * Logout FEDERADO server-side (mesmo padrão do backoffice e do portal).
 *
 * Duas armadilhas, ambas já pagas neste repositório:
 *
 * 1. `/api/auth/signout` do NextAuth **não desloga por GET** — e como este app
 *    configura `pages.signOut`, o GET só redireciona para `/login`, que vê a
 *    sessão viva e manda de volta para dentro. Era o "Sair que não sai".
 * 2. Os cookies têm nome CUSTOM `[__Secure-]console.*` (lib/auth.ts): filtro por
 *    "authjs"/"next-auth" não casa nada. E o NextAuth FRAGMENTA a sessão em
 *    `.0`/`.1` quando o JWT é grande — por isso o filtro é por prefixo
 *    `console.`, que pega os fragmentos também.
 *
 * Deleção precisa repetir o atributo Secure em https: o browser REJEITA
 * Set-Cookie de cookie `__Secure-*` sem ele.
 *
 * Encerra também a sessão SSO no Keycloak (id_token_hint). Sem isso o SSO
 * sobrevive ao logout do console.
 */
export async function GET() {
  const base = process.env.NEXTAUTH_URL || "http://localhost:3005";
  const destino = `${base}/login`;

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
    // sem sessão legível: cai no redirect simples para o /login
  }

  const secure = (process.env.NEXTAUTH_URL ?? "").startsWith("https");
  const cookieStore = await cookies();
  for (const cookie of cookieStore.getAll()) {
    const nome = cookie.name;
    if (nome.includes("console.") || nome.includes("authjs") || nome.includes("next-auth")) {
      cookieStore.set(nome, "", { expires: new Date(0), path: "/", secure });
    }
  }

  return NextResponse.redirect(target);
}
