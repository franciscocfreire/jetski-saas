import { idTokenDaSessao } from "@/lib/id-token";
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

  let target = `${destino}?logout=1`;
  try {
    // do JWT no servidor — o idToken não é mais exposto no objeto de sessão
    const idToken = await idTokenDaSessao();
    const issuer = process.env.KEYCLOAK_ISSUER;
    if (idToken && issuer) {
      // Volta no /finish (não direto no /login): lá os cookies são apagados de
      // novo, já sem nenhuma leitura de sessão em voo.
      const fim = `${base}${withBase("/api/logout/finish")}`;
      target =
        `${issuer}/protocol/openid-connect/logout` +
        `?post_logout_redirect_uri=${encodeURIComponent(fim)}` +
        `&id_token_hint=${encodeURIComponent(idToken)}`;
    }
  } catch {
    // sem sessão legível, cai no redirect simples para o /login
  }

  // Apaga a sessão do portal. Os cookies têm nome CUSTOM "[__Secure-]portal.*"
  // (lib/auth.ts) — o filtro antigo por "authjs"/"next-auth" nunca casava e o
  // cookie sobrevivia ao logout (o /login via a sessão viva e mandava de volta
  // pro perfil = "Sair que não sai"). Dois gotchas já pagos em produção:
  // deleção de __Secure-* precisa do atributo Secure (o browser rejeita sem
  // ele), e os Set-Cookie vão na PRÓPRIA resposta — mutação via cookies() não
  // acompanha um NextResponse.redirect criado à mão.
  const secure = (process.env.NEXTAUTH_URL ?? "").startsWith("https");
  const cookieStore = await cookies();
  const res = NextResponse.redirect(target);

  for (const cookie of cookieStore.getAll()) {
    const nome = cookie.name;
    if (nome.includes("portal.") || nome.includes("authjs") || nome.includes("next-auth")) {
      res.cookies.set(nome, "", { expires: new Date(0), path: "/", secure });
    }
  }

  return res;
}
