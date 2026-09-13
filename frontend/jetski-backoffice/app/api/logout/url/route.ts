import { idTokenDaSessao } from '@/lib/id-token'
import { NextResponse } from 'next/server'

/**
 * Devolve a URL de logout federado do Keycloak — SEM apagar nada.
 *
 * Existe por causa da ordem: para encerrar a sessão SSO é preciso o
 * `id_token_hint`, que só existe enquanto a sessão do Auth.js estiver viva.
 * A página /logout lê esta URL primeiro, depois chama `signOut()` (que mata o
 * cookie pelo caminho oficial) e só então navega para o Keycloak. Fazer o
 * inverso deixaria a sessão SSO de pé: o próximo login entraria direto, sem
 * pedir credenciais.
 */
export async function GET() {
  const baseUrl = process.env.NEXTAUTH_URL || 'http://localhost:3001'
  const destino = `${baseUrl}/api/logout/finish`

  try {
    // do JWT no servidor — o idToken não é mais exposto no objeto de sessão
    const idToken = await idTokenDaSessao()
    const issuer = process.env.KEYCLOAK_ISSUER

    if (idToken && issuer) {
      return NextResponse.json({
        url:
          `${issuer}/protocol/openid-connect/logout` +
          `?id_token_hint=${encodeURIComponent(idToken)}` +
          `&post_logout_redirect_uri=${encodeURIComponent(destino)}`,
      })
    }
  } catch {
    // sem sessão legível: cai no destino local
  }

  // Sem id_token não há o que encerrar no Keycloak — vai direto ao fim do fluxo.
  return NextResponse.json({ url: destino })
}
