import { auth, signOut } from '@/lib/auth'
import { NextResponse } from 'next/server'

interface SessionWithIdToken {
  accessToken?: string
  idToken?: string
}

export async function GET() {
  try {
    const session = await auth()

    // Se há uma sessão com id_token, fazer logout do Keycloak
    if (session?.accessToken) {
      const sessionWithIdToken = session as SessionWithIdToken
      const idToken = sessionWithIdToken.idToken

      if (idToken && process.env.KEYCLOAK_ISSUER) {
        const keycloakLogoutUrl = `${process.env.KEYCLOAK_ISSUER}/protocol/openid-connect/logout?id_token_hint=${idToken}&post_logout_redirect_uri=${encodeURIComponent(process.env.NEXTAUTH_URL + '/login')}`

        try {
          // Fazer requisição para logout do Keycloak
          await fetch(keycloakLogoutUrl, { method: 'GET' })
        } catch (error) {
          console.error('Erro ao fazer logout do Keycloak:', error)
        }
      }
    }
  } catch (error) {
    console.error('Erro durante logout:', error)
  }

  // Fazer logout do NextAuth
  await signOut({ redirectTo: '/login' })

  // Retornar uma resposta (a redireção é feita pelo signOut)
  return NextResponse.json({ success: true })
}
