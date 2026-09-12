import { withBase } from "@/lib/base";
import { cookies } from "next/headers";
import { NextResponse } from "next/server";

/**
 * Fim do logout federado: o Keycloak redireciona pra cá depois do end-session.
 * Rede de segurança — a sessão já morreu no cliente (sairDaConta chama
 * signOut() antes de sair).
 *
 * GOTCHAS:
 *  - cookies do portal têm nome CUSTOM "[__Secure-]portal.*" (lib/auth.ts);
 *  - deleção de cookie __Secure-* precisa do atributo Secure, senão o Chrome
 *    a rejeita em silêncio;
 *  - os Set-Cookie vão na PRÓPRIA resposta (res.cookies): mutação via
 *    cookies() não acompanha um NextResponse.redirect criado à mão;
 *  - `?logout=1` impede o /login (trampolim) de devolver a pessoa ao perfil
 *    caso ainda reste sessão legível.
 */
export async function GET() {
  const base = process.env.NEXTAUTH_URL || "http://localhost:3003";
  const secure = (process.env.NEXTAUTH_URL ?? "").startsWith("https");

  const cookieStore = await cookies();
  const res = NextResponse.redirect(`${base}${withBase("/login")}?logout=1`);

  for (const cookie of cookieStore.getAll()) {
    const nome = cookie.name;
    if (nome.includes("portal.") || nome.includes("authjs") || nome.includes("next-auth")) {
      res.cookies.set(nome, "", { expires: new Date(0), path: "/", secure });
    }
  }

  return res;
}
