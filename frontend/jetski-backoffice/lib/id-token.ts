import { getToken } from "next-auth/jwt"
import { headers } from "next/headers"
import { SESSION_COOKIE } from "@/lib/auth"

/**
 * id_token da sessão, lido SÓ no servidor, direto do JWT criptografado do cookie
 * httpOnly do Auth.js.
 *
 * O objeto de sessão (callback `session`) não carrega mais idToken/refreshToken:
 * ele é servido ao JavaScript do browser por GET /api/auth/session e ficava legível
 * por qualquer XSS/extensão. Quem precisa do id_token (id_token_hint do logout
 * federado no Keycloak) usa este helper em route handlers.
 */
export async function idTokenDaSessao(): Promise<string | undefined> {
  const secret = process.env.AUTH_SECRET ?? process.env.NEXTAUTH_SECRET
  if (!secret) return undefined
  try {
    const token = await getToken({
      req: { headers: new Headers(await headers()) },
      secret,
      secureCookie: SESSION_COOKIE.secure,
      cookieName: SESSION_COOKIE.name,
      salt: SESSION_COOKIE.name,
    })
    return typeof token?.idToken === "string" ? token.idToken : undefined
  } catch {
    return undefined
  }
}
