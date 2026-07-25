'use client'

import { useEffect, useRef, useState } from 'react'
import { useRouter, useSearchParams } from 'next/navigation'
import { ShieldAlert } from 'lucide-react'
import { apiClient } from '@/lib/api/client'

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
  const [erro, setErro] = useState<string | null>(null)
  const resgatado = useRef(false)

  useEffect(() => {
    const codigo = params.get('codigo')
    if (!codigo) {
      setErro('Código de suporte ausente. Abra a sessão novamente pelo console.')
      return
    }
    // StrictMode monta duas vezes em dev; o código é de uso único e o segundo
    // resgate falharia — a trava evita um erro que não é erro.
    if (resgatado.current) return
    resgatado.current = true

    // apiClient (não fetch cru): o resgate exige Bearer — ele é amarrado ao operador
    // que abriu a sessão, para que um código vazado não sirva a outra pessoa.
    apiClient
      .post(`/v1/suporte/resgatar?codigo=${encodeURIComponent(codigo)}`)
      .then(() => router.replace('/dashboard'))
      .catch((e) =>
        setErro(
          e?.response?.data?.message ??
            'Não foi possível abrir a sessão de suporte. Faça login no backoffice e tente de novo.',
        ),
      )
  }, [params, router])

  return (
    <div className="flex min-h-screen items-center justify-center bg-slate-50 px-4">
      <div className="w-full max-w-md rounded-xl bg-white p-8 shadow">
        <div className="flex items-center gap-2">
          <ShieldAlert className="h-6 w-6 text-amber-600" />
          <h1 className="text-lg font-semibold">Modo suporte</h1>
        </div>

        {erro ? (
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
