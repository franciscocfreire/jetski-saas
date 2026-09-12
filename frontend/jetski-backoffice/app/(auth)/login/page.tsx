'use client'

import { Suspense, useEffect, useState } from 'react'
import { signIn, signOut, useSession } from 'next-auth/react'
import { useRouter, useSearchParams } from 'next/navigation'
import Link from 'next/link'
import { Loader2 } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Logo } from '@/components/logo'

/**
 * Trampolim de login (mesmo padrão do portal do cliente): a tela de
 * credenciais é a do Keycloak (e-mail/CPF → senha ou código + Google +
 * "Criar conta gratuita" para este client) — esta página só redireciona.
 *
 * Guardas anti-loop (não remover):
 * - `?error=` (pages.error do NextAuth aponta pra cá): NÃO auto-redireciona;
 *   mostra o card manual, senão erro de auth viraria loop infinito.
 * - `authenticated`: vai direto pro dashboard sem novo fluxo OIDC.
 * - latch `entrando`: um clique/efeito duplo não abre dois fluxos PKCE.
 */
function GoogleIcon({ size = 18 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" aria-hidden="true">
      <path fill="#4285F4" d="M23.52 12.27c0-.85-.08-1.66-.22-2.45H12v4.63h6.46a5.53 5.53 0 0 1-2.4 3.62v3h3.88c2.27-2.09 3.58-5.17 3.58-8.8z" />
      <path fill="#34A853" d="M12 24c3.24 0 5.96-1.07 7.94-2.91l-3.88-3c-1.07.72-2.45 1.15-4.06 1.15-3.13 0-5.78-2.11-6.72-4.95H1.27v3.1A11.99 11.99 0 0 0 12 24z" />
      <path fill="#FBBC05" d="M5.28 14.29A7.2 7.2 0 0 1 4.9 12c0-.8.14-1.57.38-2.29v-3.1H1.27A11.99 11.99 0 0 0 0 12c0 1.94.46 3.77 1.27 5.39l4.01-3.1z" />
      <path fill="#EA4335" d="M12 4.77c1.76 0 3.34.61 4.58 1.8l3.44-3.44C17.95 1.19 15.24 0 12 0A11.99 11.99 0 0 0 1.27 6.61l4.01 3.1C6.22 6.87 8.87 4.77 12 4.77z" />
    </svg>
  )
}

export default function LoginPage() {
  return (
    <Suspense
      fallback={
        <div className="flex min-h-screen flex-col items-center justify-center gap-3 bg-auth-gradient text-muted-foreground">
          <Loader2 className="animate-spin" />
          <p className="text-sm">Carregando…</p>
        </div>
      }
    >
      <LoginInner />
    </Suspense>
  )
}

function LoginInner() {
  const params = useSearchParams()
  const router = useRouter()
  const { status, data: session } = useSession()
  // Sessão que existe mas já morreu no Keycloak (refresh vencido, ou a sessão
  // SSO do navegador passou a ser de outra conta). O Auth.js segue devolvendo
  // `user` com `error` — tratar isso como "autenticado" mandava a pessoa ao
  // dashboard, onde toda API responde 401, o cliente força signOut e volta
  // para cá: loop infinito de ~1 volta por segundo (HAR de 12/set/2026).
  const sessaoMorta = !!session?.error
  const veioComErro = !!params.get('error')
  // Marca posta pelo /api/logout/finish: "esta pessoa acabou de sair".
  const veioDeLogout = !!params.get('logout')
  const [entrando, setEntrando] = useState(false)

  async function limparSessaoMorta() {
    // O cookie velho ressuscitaria entre a ida e a volta do OIDC (o Auth.js
    // re-emite a sessão a cada leitura), então some com ele antes de recomeçar.
    if (!sessaoMorta) return
    try {
      await signOut({ redirect: false })
    } catch {
      // seguir mesmo assim: o novo fluxo OIDC sobrescreve a sessão
    }
  }

  async function entrar() {
    if (entrando) return
    setEntrando(true)
    await limparSessaoMorta()
    signIn('keycloak', { callbackUrl: '/dashboard' })
  }

  async function entrarComGoogle() {
    if (entrando) return
    setEntrando(true)
    await limparSessaoMorta()
    // kc_idp_hint: o Keycloak pula a própria tela e vai direto ao Google.
    signIn('keycloak', { callbackUrl: '/dashboard' }, { kc_idp_hint: 'google' })
  }

  // Fluxo normal (sem erro): já logado → dashboard; deslogado → Keycloak direto.
  //
  // `?logout=1` (vem do /api/logout/finish) NUNCA volta ao dashboard: se uma
  // leitura atrasada de /api/auth/session tiver ressuscitado o cookie, quem
  // acabou de sair era mandado de volta para dentro — o "sair que não sai".
  // Aqui a sessão residual é ignorada e o fluxo de login recomeça.
  useEffect(() => {
    if (veioComErro || sessaoMorta || status === 'loading') return
    if (status === 'authenticated' && !veioDeLogout) {
      router.replace('/dashboard')
      return
    }
    entrar()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [veioComErro, veioDeLogout, sessaoMorta, status])

  // Sessão morta mostra o card manual (mesma tela do ?error=): auto-entrar aqui
  // seria pior que o loop — com o SSO do navegador em outra conta, a pessoa
  // entraria calada com a identidade errada.
  if (!veioComErro && !sessaoMorta) {
    return (
      <div className="flex min-h-screen flex-col items-center justify-center gap-3 bg-auth-gradient text-muted-foreground">
        <Loader2 className="animate-spin" />
        <p className="text-sm">Abrindo login seguro…</p>
      </div>
    )
  }

  // Fallback com ?error= (sessão expirada / falha de auth): acesso manual.
  return (
    <div className="flex min-h-screen items-center justify-center bg-auth-gradient p-8">
      <div className="w-full max-w-md space-y-8">
        <div className="flex justify-center">
          <Logo variant="full" theme="light" size={40} />
        </div>

        <div className="rounded-xl bg-white p-8 shadow-xl">
          <div className="text-center mb-8">
            <h2 className="text-2xl font-bold tracking-tight">Bem-vindo de volta!</h2>
            <p className="mt-2 text-sm text-muted-foreground">
              Sua sessão expirou ou o login não pôde ser concluído. Tente novamente.
            </p>
          </div>

          <div className="space-y-4">
            <Button onClick={entrar} disabled={entrando} className="w-full h-12 text-base font-semibold" size="lg">
              Entrar com sua conta
            </Button>

            <Button onClick={entrarComGoogle} disabled={entrando} variant="outline" className="w-full h-12 text-base gap-2" size="lg">
              <GoogleIcon /> Entrar com Google
            </Button>

            <div className="relative my-6">
              <div className="absolute inset-0 flex items-center">
                <span className="w-full border-t" />
              </div>
              <div className="relative flex justify-center text-xs uppercase">
                <span className="bg-white px-2 text-muted-foreground">Novo por aqui?</span>
              </div>
            </div>

            <Button
              variant="outline"
              className="w-full h-12 text-base border-2 border-primary text-primary hover:bg-primary hover:text-primary-foreground transition-all"
              size="lg"
              asChild
            >
              <Link href="/signup">Criar Conta Gratuita</Link>
            </Button>
          </div>

          <p className="mt-6 text-center text-xs text-muted-foreground">
            Ao continuar, você concorda com nossos{' '}
            <a href="/termos" target="_blank" className="text-primary hover:underline">Termos de Uso</a>
            {' '}e{' '}
            <a href="/privacidade" target="_blank" className="text-primary hover:underline">Política de Privacidade</a>.
          </p>
        </div>

        <p className="text-center text-sm text-muted-foreground">
          Precisa de ajuda? Veja a{' '}
          <a href="/ajuda" className="text-primary hover:underline font-medium">Central de Ajuda</a>
          {' '}ou{' '}
          <a href="mailto:suporte@meujet.com.br" className="text-primary hover:underline font-medium">fale conosco</a>
        </p>
      </div>
    </div>
  )
}
