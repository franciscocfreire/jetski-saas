import { getToken } from "next-auth/jwt";
import { headers } from "next/headers";
import { SESSION_COOKIE } from "@/lib/auth";

/**
 * id_token da sessão, lido SÓ no servidor, direto do JWT criptografado do cookie
 * httpOnly (`[__Secure-]portal.session-token`).
 *
 * O objeto de sessão não carrega mais o idToken: ele é servido ao browser por
 * /api/auth/session e ficava legível por qualquer XSS/extensão. O logout federado
 * (id_token_hint) usa este helper nos route handlers.
 */
export async function idTokenDaSessao(): Promise<string | undefined> {
  const secret = process.env.AUTH_SECRET ?? process.env.NEXTAUTH_SECRET;
  if (!secret) return undefined;
  try {
    const token = await getToken({
      req: { headers: new Headers(await headers()) },
      secret,
      secureCookie: SESSION_COOKIE.secure,
      cookieName: SESSION_COOKIE.name,
      salt: SESSION_COOKIE.name,
    });
    return typeof token?.idToken === "string" ? token.idToken : undefined;
  } catch {
    return undefined;
  }
}
