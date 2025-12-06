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

    // Clear all auth cookies
    const cookieStore = await cookies()
    const allCookies = cookieStore.getAll()

    // Delete all auth-related cookies
    for (const cookie of allCookies) {
      if (
        cookie.name.includes('authjs') ||
        cookie.name.includes('next-auth') ||
        cookie.name.includes('session')
      ) {
        cookieStore.delete(cookie.name)
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
