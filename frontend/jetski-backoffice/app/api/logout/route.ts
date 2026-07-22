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
        keycloakLogoutUrl = `${process.env.KEYCLOAK_ISSUER}/protocol/openid-connect/logout?id_token_hint=${idToken}&post_logout_redirect_uri=${encodeURIComponent(loginUrl)}`
      }
    }

    // Clear all auth cookies. GOTCHA (mesmo bug corrigido no portal): a
    // deleção de cookie __Secure-* PRECISA sair com o atributo Secure —
    // cookieStore.delete() não o emite e o Chrome rejeita em silêncio, a
    // sessão sobrevivia e o /login (trampolim) relogava direto no dashboard.
    const secure = (process.env.NEXTAUTH_URL ?? '').startsWith('https')
    const cookieStore = await cookies()
    const allCookies = cookieStore.getAll()

    for (const cookie of allCookies) {
      if (
        cookie.name.includes('authjs') ||
        cookie.name.includes('next-auth') ||
        cookie.name.includes('session')
      ) {
        cookieStore.set(cookie.name, '', { expires: new Date(0), path: '/', secure })
      }
    }

    // Redirect to Keycloak logout if available, otherwise to login
    const redirectUrl = keycloakLogoutUrl || loginUrl
    return NextResponse.redirect(redirectUrl)
  } catch (error) {
    console.error('Erro durante logout:', error)
    // On error, just redirect to login
    return NextResponse.redirect(loginUrl)
  }
}
