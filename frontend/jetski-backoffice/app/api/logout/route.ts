import { auth } from '@/lib/auth'
import { cookies } from 'next/headers'
import { NextResponse } from 'next/server'

interface SessionWithIdToken {
  accessToken?: string
  idToken?: string
}

export async function GET() {
  const baseUrl = process.env.NEXTAUTH_URL || 'http://localhost:3001'
  const loginUrl = `${baseUrl}/login`

  try {
    const session = await auth()

    // Build Keycloak logout URL if we have a session
    let keycloakLogoutUrl: string | null = null

    if (session?.accessToken) {
      const sessionWithIdToken = session as SessionWithIdToken
      const idToken = sessionWithIdToken.idToken

      if (idToken && process.env.KEYCLOAK_ISSUER) {
        // Volta no /api/logout/finish (não direto no /login): a página /logout
        // monta o SessionProvider, que dispara um GET /api/auth/session — e o
        // Auth.js RE-EMITE o cookie de sessão a cada leitura (expiração
        // deslizante). Se essa resposta chega depois da nossa deleção, o
        // cookie RESSUSCITA (corrida real vista em prod). O finish deleta de
        // novo após o roundtrip ao Keycloak, quando não há mais renovação em
        // voo.
        const finishUrl = `${baseUrl}/api/logout/finish`
        keycloakLogoutUrl = `${process.env.KEYCLOAK_ISSUER}/protocol/openid-connect/logout?id_token_hint=${idToken}&post_logout_redirect_uri=${encodeURIComponent(finishUrl)}`
      }
    }

    // Caminho de fallback (a página /logout hoje faz signOut() antes de vir
    // para cá). GOTCHAS, os dois já pagos em produção:
    //  - a deleção de cookie __Secure-* PRECISA sair com o atributo Secure,
    //    senão o Chrome a rejeita em silêncio;
    //  - os Set-Cookie vão na PRÓPRIA resposta (res.cookies), não via
    //    cookies() do next/headers: ao devolver um NextResponse.redirect
    //    criado à mão, mutação feita no cookieStore não acompanha a resposta.
    const secure = (process.env.NEXTAUTH_URL ?? '').startsWith('https')
    const cookieStore = await cookies()
    const allCookies = cookieStore.getAll()

    // Redirect to Keycloak logout if available, otherwise to login
    const redirectUrl = keycloakLogoutUrl || `${loginUrl}?logout=1`
    const res = NextResponse.redirect(redirectUrl)

    for (const cookie of allCookies) {
      if (
        cookie.name.includes('authjs') ||
        cookie.name.includes('next-auth') ||
        cookie.name.includes('session')
      ) {
        res.cookies.set(cookie.name, '', { expires: new Date(0), path: '/', secure })
      }
    }

    return res
  } catch (error) {
    console.error('Erro durante logout:', error)
    // On error, just redirect to login
    return NextResponse.redirect(loginUrl)
  }
}
