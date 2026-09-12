import { auth } from "@/lib/auth";
import { withBase } from "@/lib/base";
import { NextResponse } from "next/server";

/**
 * Devolve a URL de logout federado do Keycloak — SEM apagar nada.
 *
 * Existe por causa da ordem (mesmo desenho do backoffice): o `id_token_hint`
 * só existe enquanto a sessão do Auth.js estiver viva, então a URL é lida
 * ANTES do signOut() do cliente. Inverter deixaria a sessão SSO de pé e o
 * próximo "Entrar" logaria sem pedir nada.
 */
export async function GET() {
  const base = process.env.NEXTAUTH_URL || "http://localhost:3003";
  const destino = `${base}${withBase("/api/logout/finish")}`;

  try {
    const session = await auth();
    const idToken = session?.idToken;
    const issuer = process.env.KEYCLOAK_ISSUER;

    if (idToken && issuer) {
      return NextResponse.json({
        url:
          `${issuer}/protocol/openid-connect/logout` +
          `?post_logout_redirect_uri=${encodeURIComponent(destino)}` +
          `&id_token_hint=${encodeURIComponent(idToken)}`,
      });
    }
  } catch {
    // sem sessão legível: cai no destino local
  }

  return NextResponse.json({ url: destino });
}
