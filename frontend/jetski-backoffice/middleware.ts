import { NextRequest, NextResponse } from 'next/server'

/**
 * Vitrine por subdomínio: {slug}.meujet.com.br → rewrite interno para /loja/{slug}.
 *
 * O nginx (server default) apanha o wildcard *.meujet.com.br do túnel e repassa
 * o Host real; aqui só a RAIZ é reescrita (matcher '/'), então /embarcacao/*,
 * /dashboard/*, /_next/* e /api/* seguem intocados em qualquer host.
 *
 * Hosts de infraestrutura nunca viram vitrine — a lista espelha os
 * SLUGS_RESERVADOS do backend (TenantSignupService), que impede empresas de
 * registrarem esses nomes.
 */
const HOSTS_RESERVADOS = new Set([
  'www', 'app', 'cliente', 'api', 'portal', 'admin', 'auth', 'keycloak',
  'mail', 'smtp', 'email', 'webmail', 'cdn', 'static', 'assets',
  'grafana', 'prometheus', 'alertmanager', 'minio', 'storage', 'mailpit',
  'excalidraw', 'drawio', 'kroki',
])

const HOST_VITRINE = /^([a-z0-9][a-z0-9-]*)\.(meujet|pegaojet|jetsave)\.com\.br$/

export function middleware(request: NextRequest) {
  const host = (request.headers.get('host') ?? '').split(':')[0].toLowerCase()
  const match = HOST_VITRINE.exec(host)
  if (match && !HOSTS_RESERVADOS.has(match[1])) {
    const url = request.nextUrl.clone()
    url.pathname = `/loja/${match[1]}`
    return NextResponse.rewrite(url)
  }
  return NextResponse.next()
}

export const config = {
  matcher: '/',
}
