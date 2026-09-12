import { cookies } from 'next/headers'
import { NextResponse } from 'next/server'

/**
 * Fim do logout federado: o Keycloak redireciona pra cá depois do end-session.
 *
 * A esta altura a sessão do Auth.js já morreu no cliente (a página /logout
 * chama signOut() antes de sair), então esta passada é rede de segurança —
 * cobre o caminho de quem chegou direto em /api/logout.
 *
 * GOTCHAS:
 *  - deleção de cookie __Secure-* precisa do atributo Secure, senão o Chrome
 *    a rejeita em silêncio;
 *  - os Set-Cookie vão na PRÓPRIA resposta (res.cookies): mutação via
 *    cookies() do next/headers não acompanha um NextResponse.redirect criado
 *    à mão;
 *  - o destino leva `?logout=1` para o /login não devolver a pessoa ao
 *    dashboard caso ainda reste sessão legível.
 */
export async function GET() {
  const baseUrl = process.env.NEXTAUTH_URL || 'http://localhost:3001'
  const secure = (process.env.NEXTAUTH_URL ?? '').startsWith('https')

  const cookieStore = await cookies()
  const res = NextResponse.redirect(`${baseUrl}/login?logout=1`)

  for (const cookie of cookieStore.getAll()) {
    if (
      cookie.name.includes('authjs') ||
      cookie.name.includes('next-auth') ||
      cookie.name.includes('session')
    ) {
      res.cookies.set(cookie.name, '', { expires: new Date(0), path: '/', secure })
    }
  }

  return res
}
