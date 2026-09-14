'use client'

import { useEffect, useState, Suspense, type ReactNode } from 'react'
import { useSearchParams } from 'next/navigation'
import { Loader2, CheckCircle, AlertCircle, XCircle, WifiOff } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { Label } from '@/components/ui/label'
import { SignaturePad } from '@/components/signature-pad'
import { Logo } from '@/components/logo'

const API_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8090/api'

type Estado =
  | { tipo: 'carregando' }
  | { tipo: 'sem-token' }
  | { tipo: 'invalido' }
  | { tipo: 'indisponivel'; mensagem: string }
  | { tipo: 'erro'; mensagem: string }
  | { tipo: 'pronto'; dados: DadosLink }
  | { tipo: 'sucesso'; instrutorNome: string }

interface DadosLink {
  instrutorNome: string
  empresa: string
  expiraEm: string
  temAssinatura: boolean
}

async function lerMensagem(r: Response, padrao: string): Promise<string> {
  const data = await r.json().catch(() => null)
  return (data && typeof data.message === 'string' && data.message) || padrao
}

function formatarData(iso: string): string {
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return iso
  return d.toLocaleString('pt-BR', {
    timeZone: 'America/Sao_Paulo',
    dateStyle: 'short',
    timeStyle: 'short',
  })
}

function Moldura({ children }: { children: ReactNode }) {
  return (
    <div className="flex min-h-screen items-start justify-center bg-gradient-to-br from-blue-50 to-blue-100 px-4 py-6 sm:items-center dark:from-gray-900 dark:to-gray-800">
      <div className="w-full max-w-md space-y-5 rounded-xl bg-white p-5 shadow-xl sm:p-8 dark:bg-gray-900">
        <div className="flex justify-center">
          <Logo theme="light" size={26} className="dark:hidden" />
          <Logo theme="dark" size={26} className="hidden dark:inline-flex" />
        </div>
        {children}
      </div>
    </div>
  )
}

function Aviso({
  icone,
  cor,
  titulo,
  children,
}: {
  icone: ReactNode
  cor: 'green' | 'yellow' | 'red' | 'blue'
  titulo: string
  children?: ReactNode
}) {
  const fundo = {
    green: 'bg-green-100 dark:bg-green-900',
    yellow: 'bg-yellow-100 dark:bg-yellow-900',
    red: 'bg-red-100 dark:bg-red-900',
    blue: 'bg-blue-100 dark:bg-blue-900',
  }[cor]
  return (
    <div className="text-center">
      <div className={`mx-auto flex h-16 w-16 items-center justify-center rounded-full ${fundo}`}>
        {icone}
      </div>
      <h1 className="mt-5 text-xl font-bold tracking-tight">{titulo}</h1>
      {children && <div className="mt-3 space-y-2 text-sm text-muted-foreground">{children}</div>}
    </div>
  )
}

function AssinarInstrutorContent() {
  const searchParams = useSearchParams()
  const token = searchParams.get('token')
  const [estado, setEstado] = useState<Estado>({ tipo: 'carregando' })
  const [confirmado, setConfirmado] = useState(false)
  const [assinatura, setAssinatura] = useState<string | null>(null)
  const [enviando, setEnviando] = useState(false)
  const [erroEnvio, setErroEnvio] = useState<string | null>(null)

  useEffect(() => {
    if (!token) {
      setEstado({ tipo: 'sem-token' })
      return
    }
    let cancelado = false
    ;(async () => {
      try {
        const r = await fetch(`${API_URL}/v1/public/instrutor-assinatura/${encodeURIComponent(token)}`)
        if (cancelado) return
        if (r.ok) {
          setEstado({ tipo: 'pronto', dados: (await r.json()) as DadosLink })
        } else if (r.status === 404) {
          setEstado({ tipo: 'invalido' })
        } else if (r.status === 410) {
          setEstado({
            tipo: 'indisponivel',
            mensagem: await lerMensagem(r, 'Este link não está mais disponível.'),
          })
        } else {
          setEstado({
            tipo: 'erro',
            mensagem: await lerMensagem(r, 'Não foi possível abrir o link. Tente novamente.'),
          })
        }
      } catch {
        if (!cancelado) {
          setEstado({ tipo: 'erro', mensagem: 'Erro de conexão. Verifique a internet e tente novamente.' })
        }
      }
    })()
    return () => {
      cancelado = true
    }
  }, [token])

  async function enviar() {
    if (!token || !assinatura || !confirmado) return
    setEnviando(true)
    setErroEnvio(null)
    try {
      const r = await fetch(`${API_URL}/v1/public/instrutor-assinatura/${encodeURIComponent(token)}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ assinaturaBase64: assinatura }),
      })
      if (r.ok) {
        const data = (await r.json().catch(() => ({}))) as { instrutorNome?: string }
        const nome = data.instrutorNome ?? (estado.tipo === 'pronto' ? estado.dados.instrutorNome : '')
        setEstado({ tipo: 'sucesso', instrutorNome: nome })
      } else if (r.status === 404) {
        setEstado({ tipo: 'invalido' })
      } else if (r.status === 410) {
        setEstado({
          tipo: 'indisponivel',
          mensagem: await lerMensagem(r, 'Este link não está mais disponível.'),
        })
      } else if (r.status === 400) {
        setErroEnvio(await lerMensagem(r, 'A imagem da assinatura é inválida. Limpe e assine novamente.'))
      } else {
        setErroEnvio(await lerMensagem(r, 'Não foi possível enviar a assinatura. Tente novamente.'))
      }
    } catch {
      setErroEnvio('Erro de conexão. Verifique a internet e tente novamente.')
    } finally {
      setEnviando(false)
    }
  }

  switch (estado.tipo) {
    case 'carregando':
      return (
        <Moldura>
          <Aviso
            cor="blue"
            icone={<Loader2 className="h-8 w-8 animate-spin text-blue-600 dark:text-blue-400" />}
            titulo="Abrindo o link..."
          />
        </Moldura>
      )

    case 'sem-token':
    case 'invalido':
      return (
        <Moldura>
          <Aviso
            cor="red"
            icone={<XCircle className="h-8 w-8 text-red-600 dark:text-red-400" />}
            titulo="Link inválido"
          >
            <p>
              {estado.tipo === 'sem-token'
                ? 'O endereço está incompleto.'
                : 'Não encontramos este link de assinatura.'}{' '}
              Confira se copiou o link inteiro ou peça um novo link à empresa.
            </p>
          </Aviso>
        </Moldura>
      )

    case 'indisponivel':
      return (
        <Moldura>
          <Aviso
            cor="yellow"
            icone={<AlertCircle className="h-8 w-8 text-yellow-600 dark:text-yellow-400" />}
            titulo="Link indisponível"
          >
            <p>{estado.mensagem}</p>
            <p>Peça um novo link de assinatura à empresa.</p>
          </Aviso>
        </Moldura>
      )

    case 'erro':
      return (
        <Moldura>
          <Aviso
            cor="red"
            icone={<WifiOff className="h-8 w-8 text-red-600 dark:text-red-400" />}
            titulo="Não foi possível carregar"
          >
            <p>{estado.mensagem}</p>
          </Aviso>
          <Button className="w-full" variant="outline" onClick={() => window.location.reload()}>
            Tentar novamente
          </Button>
        </Moldura>
      )

    case 'sucesso':
      return (
        <Moldura>
          <Aviso
            cor="green"
            icone={<CheckCircle className="h-8 w-8 text-green-600 dark:text-green-400" />}
            titulo="Assinatura registrada"
          >
            <p>
              {estado.instrutorNome ? `Obrigado, ${estado.instrutorNome}. ` : 'Obrigado. '}
              Sua assinatura foi enviada. Pode fechar esta página.
            </p>
          </Aviso>
        </Moldura>
      )

    case 'pronto': {
      const { dados } = estado
      const podeEnviar = confirmado && !!assinatura && !enviando
      return (
        <Moldura>
          <div className="space-y-2">
            <p className="text-center text-xs font-medium uppercase tracking-wide text-muted-foreground">
              {dados.empresa}
            </p>
            <h1 className="text-center text-xl font-bold tracking-tight">Olá, {dados.instrutorNome}</h1>
            <p className="text-sm text-muted-foreground">
              A <b className="text-foreground">{dados.empresa}</b> pediu a sua assinatura. Ela será
              usada no <b className="text-foreground">Atestado de Demonstração (Anexo 5-B-1)</b> dos
              alunos em que você for o instrutor responsável.
            </p>
            {dados.temAssinatura && (
              <p className="rounded-md bg-amber-50 p-2 text-xs text-amber-900 dark:bg-amber-950 dark:text-amber-200">
                Já existe uma assinatura sua cadastrada. A nova assinatura vai substituí-la.
              </p>
            )}
          </div>

          <div>
            <Label className="mb-1 block text-sm">Sua assinatura</Label>
            <SignaturePad
              height={200}
              testId="assinar-instrutor-canvas"
              onChange={(dataUrl) => {
                setAssinatura(dataUrl)
                setErroEnvio(null)
              }}
            />
          </div>

          <div className="flex items-start gap-3">
            <Checkbox
              id="confirmo"
              checked={confirmado}
              onCheckedChange={(v) => setConfirmado(v === true)}
              className="mt-0.5"
            />
            <Label htmlFor="confirmo" className="text-sm font-normal leading-snug">
              Confirmo que sou {dados.instrutorNome} e que esta é a minha assinatura.
            </Label>
          </div>

          {erroEnvio && (
            <p className="rounded-md bg-red-50 p-2 text-sm text-red-800 dark:bg-red-950 dark:text-red-200">
              {erroEnvio}
            </p>
          )}

          <Button
            className="w-full"
            size="lg"
            disabled={!podeEnviar}
            onClick={enviar}
            data-testid="assinar-instrutor-enviar"
          >
            {enviando ? (
              <>
                <Loader2 className="mr-2 h-4 w-4 animate-spin" /> Enviando...
              </>
            ) : (
              'Enviar assinatura'
            )}
          </Button>

          <p className="text-center text-xs text-muted-foreground">
            Link de uso único, válido até {formatarData(dados.expiraEm)}.
          </p>
        </Moldura>
      )
    }
  }
}

export default function AssinarInstrutorPage() {
  return (
    <Suspense
      fallback={
        <Moldura>
          <Aviso
            cor="blue"
            icone={<Loader2 className="h-8 w-8 animate-spin text-blue-600 dark:text-blue-400" />}
            titulo="Carregando..."
          />
        </Moldura>
      }
    >
      <AssinarInstrutorContent />
    </Suspense>
  )
}
