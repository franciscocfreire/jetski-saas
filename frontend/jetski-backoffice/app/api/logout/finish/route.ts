import { cookies } from 'next/headers'
import { NextResponse } from 'next/server'

/**
 * Segunda passada do logout federado (anti-corrida): o Keycloak redireciona
 * pra cá após o end-session. A esta altura nenhuma renovação de sessão está
 * em voo (as páginas do app já foram descarregadas), então a deleção dos
 * cookies aqui é final — mesmo que uma resposta atrasada de
 * /api/auth/session tenha ressuscitado o cookie durante a primeira passada
 * (/api/logout).
 *
 * GOTCHA mantido: deleção de cookie __Secure-* precisa do atributo Secure,
 * senão o Chrome a rejeita em silêncio.
 */
export async function GET() {
  const baseUrl = process.env.NEXTAUTH_URL || 'http://localhost:3001'
  const secure = (process.env.NEXTAUTH_URL ?? '').startsWith('https')

  const cookieStore = await cookies()
  for (const cookie of cookieStore.getAll()) {
    if (
      cookie.name.includes('authjs') ||
      cookie.name.includes('next-auth') ||
      cookie.name.includes('session')
    ) {
      cookieStore.set(cookie.name, '', { expires: new Date(0), path: '/', secure })
    }
  }

  return NextResponse.redirect(`${baseUrl}/login`)
}
