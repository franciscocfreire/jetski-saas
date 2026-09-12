'use client'

import { useEffect } from 'react'
import { signOut } from 'next-auth/react'
import { useTenantStore } from '@/lib/store/tenant-store'

/**
 * Saída em três tempos, nesta ordem exata (mudar a ordem quebra uma das pontas):
 *
 * 1. pega a URL de logout federado ENQUANTO a sessão existe — o
 *    `id_token_hint` some junto com ela;
 * 2. `signOut()` do Auth.js: apaga o cookie pelo caminho oficial (inclusive os
 *    fragmentos `.0`/`.1` do JWT grande) e, sobretudo, deixa o SessionProvider
 *    desta página em "unauthenticated";
 * 3. só então navega para o Keycloak.
 *
 * O passo 2 é o que fecha a corrida que derrubava o logout: cada
 * `GET /api/auth/session` RE-EMITE o cookie (expiração deslizante do Auth.js), e
 * uma resposta atrasada chegava depois das deleções do servidor, ressuscitando a
 * sessão. O /login então via `authenticated` e devolvia a pessoa ao dashboard —
 * "sair que não sai". Com a sessão já morta no cliente, não há mais o que
 * re-emitir. As deleções no servidor (/api/logout/finish) seguem como rede de
 * segurança.
 */
export default function LogoutPage() {
  const clearTenant = useTenantStore((state) => state.clearTenant)

  useEffect(() => {
    clearTenant()

    if (typeof window !== 'undefined') {
      localStorage.removeItem('tenant-storage')
    }

    let cancelado = false

    async function sair() {
      // 1. URL do logout federado, ainda com sessão viva.
      let destino = '/api/logout'
      try {
        const r = await fetch('/api/logout/url', { cache: 'no-store' })
        if (r.ok) {
          const dados = (await r.json()) as { url?: string }
          if (dados.url) destino = dados.url
        }
      } catch {
        // sem a URL, o /api/logout (fallback) refaz o trabalho do lado do servidor
      }

      // 2. mata a sessão local pelo caminho oficial do Auth.js.
      try {
        await signOut({ redirect: false })
      } catch {
        // mesmo falhando, seguimos: o servidor ainda apaga os cookies
      }

      // 3. encerra a sessão no Keycloak.
      if (!cancelado) window.location.href = destino
    }

    sair()

    return () => {
      cancelado = true
    }
  }, [clearTenant])

  return (
    <div className="flex min-h-screen items-center justify-center">
      <div className="text-center">
        <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary mx-auto mb-4"></div>
        <p className="text-muted-foreground">Saindo...</p>
      </div>
    </div>
  )
}
