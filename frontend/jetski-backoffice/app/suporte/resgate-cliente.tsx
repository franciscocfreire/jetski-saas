'use client'

import { useEffect, useRef, useState } from 'react'
import { useRouter, useSearchParams } from 'next/navigation'
import { signIn, useSession } from 'next-auth/react'
import { ShieldAlert } from 'lucide-react'
import { apiClient, setAuthToken } from '@/lib/api/client'

/**
 * Resgate do código de suporte — a chegada do handoff console → backoffice.
 *
 * O console (admin.*) não consegue setar cookie aqui (app.*), então manda um CÓDIGO de
 * uso único e vida curta na URL. Esta página o troca pelo cookie de sessão (HttpOnly,
 * setado pelo backend) e sai da URL imediatamente: código usado é código queimado, e
 * deixá-lo no histórico/Referer não ajuda ninguém.
 *
 * Fora do grupo (dashboard) de propósito: aqui ainda não existe sessão de suporte, e o
 * layout do dashboard depende de tenant selecionado.
 */
export function ResgateCliente() {
  const router = useRouter()
  const params = useSearchParams()
  const { data: session, status } = useSession()
  const [erro, setErro] = useState<string | null>(null)
  // 409 = o código pertence a outro operador. Vale uma tela própria: é o caso
  // frequente de `app.*` e `admin.*` no mesmo navegador com contas diferentes —
  // o Keycloak é o mesmo e a sessão SSO é uma só.
  const [contaErrada, setContaErrada] = useState(false)
  const resgatado = useRef(false)

  useEffect(() => {
    // Esperar a sessão é OBRIGATÓRIO, não otimização: o token do apiClient vive em
    // sessionStorage e só é gravado pelo layout do (dashboard) — esta página está
    // FORA daquele grupo. O handoff chega numa aba que navegou de admin.* para app.*,
    // onde o sessionStorage da origem está vazio: sem isto o resgate sai sem
    // Authorization e o backend responde 401 (foi o que aconteceu em 25/jul).
    if (status === 'loading') return

    const codigo = params.get('codigo')
    if (!codigo) {
      setErro('Código de suporte ausente. Abra a sessão novamente pelo console.')
      return
    }

    // Sem sessão aqui é o caso NORMAL, não erro: o operador estava logado no console
    // (admin.*), que é outra origem — o backoffice pode nunca ter sido aberto neste
    // navegador. Mandar "vá fazer login" era um beco sem saída que queimava o código.
    // Como o Keycloak é o mesmo realm, o login volta em silêncio pelo SSO e cai de
    // novo nesta página, com o código intacto.
    if (status !== 'authenticated' || !session?.accessToken) {
      if (params.get('login') === '1') {
        // Já voltamos de uma tentativa e ainda não há sessão — parar aqui em vez de
        // ficar em pingue-pongue com o IdP.
        setErro('Não foi possível autenticar no backoffice. Abra a sessão novamente pelo console.')
        return
      }
      const volta = `/suporte?codigo=${encodeURIComponent(codigo)}&login=1`
      signIn('keycloak', { callbackUrl: volta })
      return
    }
    setAuthToken(session.accessToken)
    // StrictMode monta duas vezes em dev; o código é de uso único e o segundo
    // resgate falharia — a trava evita um erro que não é erro.
    if (resgatado.current) return
    resgatado.current = true

    // apiClient (não fetch cru): o resgate exige Bearer — ele é amarrado ao operador
    // que abriu a sessão, para que um código vazado não sirva a outra pessoa.
    apiClient
      .post(`/v1/suporte/resgatar?codigo=${encodeURIComponent(codigo)}`)
      .then(() => router.replace('/dashboard'))
      .catch((e) => {
        if (e?.response?.status === 409) {
          setContaErrada(true)
          return
        }
        setErro(
          e?.response?.data?.message ??
            'Não foi possível abrir a sessão de suporte. Faça login no backoffice e tente de novo.',
        )
      })
  }, [params, router, session, status])

  return (
    <div className="flex min-h-screen items-center justify-center bg-slate-50 px-4">
      <div className="w-full max-w-md rounded-xl bg-white p-8 shadow">
        <div className="flex items-center gap-2">
          <ShieldAlert className="h-6 w-6 text-amber-600" />
          <h1 className="text-lg font-semibold">Modo suporte</h1>
        </div>

        {contaErrada ? (
          <>
            <p className="mt-4 text-sm text-red-700">
              Esta sessão de suporte foi aberta por outro operador.
            </p>
            <p className="mt-3 text-sm text-slate-600">
              Este navegador está logado como{' '}
              <strong className="text-slate-900">{session?.user?.email ?? 'outra conta'}</strong>.
              Saia dessa conta e abra a sessão novamente pelo console.
            </p>
            <p className="mt-3 text-xs text-slate-500">
              O backoffice e o console usam o mesmo login, e o navegador guarda uma sessão
              só — por isso as duas contas não convivem na mesma janela. Para usar as duas
              ao mesmo tempo, abra o console em outro perfil do navegador ou numa janela
              anônima.
            </p>
            <a
              href="/logout"
              className="mt-5 inline-flex h-10 items-center justify-center rounded-md bg-slate-900 px-4 text-sm font-medium text-white hover:bg-slate-800"
            >
              Sair desta conta
            </a>
          </>
        ) : erro ? (
          <>
            <p className="mt-4 text-sm text-red-700">{erro}</p>
            <p className="mt-2 text-xs text-slate-500">
              Códigos de suporte valem por poucos minutos e só podem ser usados uma vez.
            </p>
          </>
        ) : (
          <p className="mt-4 text-sm text-slate-600">Abrindo a sessão…</p>
        )}
      </div>
    </div>
  )
}
