'use client'

import { useEffect, useState, Suspense } from 'react'
import { useRouter, useSearchParams } from 'next/navigation'
import Link from 'next/link'
import { Ship, Loader2, CheckCircle, AlertCircle, XCircle } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Alert, AlertDescription } from '@/components/ui/alert'

const API_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8090/api'

type ActivationStatus = 'loading' | 'success' | 'error' | 'expired' | 'invalid'

function MagicActivateContent() {
  const router = useRouter()
  const searchParams = useSearchParams()
  const [status, setStatus] = useState<ActivationStatus>('loading')
  const [errorMessage, setErrorMessage] = useState<string>('')

  useEffect(() => {
    const token = searchParams.get('token')

    if (!token) {
      setStatus('invalid')
      setErrorMessage('Link de ativação inválido. Token não encontrado.')
      return
    }

    const activateAccount = async () => {
      try {
        // Try signup magic-activate first, if it fails with 404, try auth magic-activate
        let response = await fetch(`${API_URL}/v1/signup/magic-activate`, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
          },
          body: JSON.stringify({ magicToken: token }),
        })

        // If signup activation returns 404 (not found), try invite activation
        if (response.status === 404) {
          response = await fetch(`${API_URL}/v1/auth/magic-activate`, {
            method: 'POST',
            headers: {
              'Content-Type': 'application/json',
            },
            body: JSON.stringify({ magicToken: token }),
          })
        }

        if (response.ok) {
          setStatus('success')
        } else {
          const data = await response.json()

          if (response.status === 410) {
            setStatus('expired')
            setErrorMessage('Este link de ativação expirou.')
          } else if (response.status === 409) {
            setStatus('error')
            setErrorMessage(data.message || 'Esta conta já foi ativada.')
          } else if (response.status === 403) {
            setStatus('invalid')
            setErrorMessage('Credenciais inválidas.')
          } else {
            setStatus('error')
            setErrorMessage(data.message || 'Erro ao ativar conta.')
          }
        }
      } catch {
        setStatus('error')
        setErrorMessage('Erro de conexão. Tente novamente.')
      }
    }

    activateAccount()
  }, [searchParams])

  const renderContent = () => {
    switch (status) {
      case 'loading':
        return (
          <>
            <div className="mx-auto flex h-16 w-16 items-center justify-center rounded-full bg-blue-100 dark:bg-blue-900">
              <Loader2 className="h-8 w-8 animate-spin text-blue-600 dark:text-blue-400" />
            </div>
            <h1 className="mt-6 text-2xl font-bold tracking-tight">
              Ativando sua conta...
            </h1>
            <p className="mt-4 text-sm text-muted-foreground">
              Aguarde enquanto configuramos tudo para você.
            </p>
          </>
        )

      case 'success':
        return (
          <>
            <div className="mx-auto flex h-16 w-16 items-center justify-center rounded-full bg-green-100 dark:bg-green-900">
              <CheckCircle className="h-8 w-8 text-green-600 dark:text-green-400" />
            </div>
            <h1 className="mt-6 text-2xl font-bold tracking-tight">
              Conta Ativada!
            </h1>
            <p className="mt-4 text-sm text-muted-foreground">
              Sua conta foi ativada com sucesso. Você já pode fazer login e começar a usar o sistema.
            </p>
            <Alert className="mt-4 bg-blue-50 dark:bg-blue-950 border-blue-200">
              <AlertDescription>
                Use o email e a senha temporária que você recebeu por email para fazer o primeiro login.
                Você será solicitado a alterar sua senha.
              </AlertDescription>
            </Alert>
            <div className="mt-8">
              <Button
                className="w-full"
                size="lg"
                onClick={() => router.push('/login')}
              >
                Ir para Login
              </Button>
            </div>
          </>
        )

      case 'expired':
        return (
          <>
            <div className="mx-auto flex h-16 w-16 items-center justify-center rounded-full bg-yellow-100 dark:bg-yellow-900">
              <AlertCircle className="h-8 w-8 text-yellow-600 dark:text-yellow-400" />
            </div>
            <h1 className="mt-6 text-2xl font-bold tracking-tight">
              Link Expirado
            </h1>
            <p className="mt-4 text-sm text-muted-foreground">
              {errorMessage}
            </p>
            <p className="mt-2 text-sm text-muted-foreground">
              Entre em contato com o suporte para solicitar um novo link de ativação.
            </p>
            <div className="mt-8 space-y-4">
              <Button
                variant="outline"
                className="w-full"
                onClick={() => router.push('/signup')}
              >
                Criar Nova Conta
              </Button>
              <Button
                variant="ghost"
                className="w-full"
                onClick={() => router.push('/login')}
              >
                Voltar para Login
              </Button>
            </div>
          </>
        )

      case 'invalid':
      case 'error':
      default:
        return (
          <>
            <div className="mx-auto flex h-16 w-16 items-center justify-center rounded-full bg-red-100 dark:bg-red-900">
              <XCircle className="h-8 w-8 text-red-600 dark:text-red-400" />
            </div>
            <h1 className="mt-6 text-2xl font-bold tracking-tight">
              Erro na Ativação
            </h1>
            <p className="mt-4 text-sm text-muted-foreground">
              {errorMessage}
            </p>
            <div className="mt-8 space-y-4">
              <Button
                variant="outline"
                className="w-full"
                onClick={() => router.push('/signup')}
              >
                Criar Nova Conta
              </Button>
              <Button
                variant="ghost"
                className="w-full"
                onClick={() => router.push('/login')}
              >
                Voltar para Login
              </Button>
            </div>
          </>
        )
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-gradient-to-br from-blue-50 to-blue-100 dark:from-gray-900 dark:to-gray-800">
      <div className="w-full max-w-md space-y-6 rounded-xl bg-white p-8 shadow-xl dark:bg-gray-900 text-center">
        <Link href="/" className="inline-block">
          <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-full bg-primary mb-4">
            <Ship className="h-6 w-6 text-primary-foreground" />
          </div>
        </Link>
        {renderContent()}
      </div>
    </div>
  )
}

export default function MagicActivatePage() {
  return (
    <Suspense fallback={
      <div className="flex min-h-screen items-center justify-center bg-gradient-to-br from-blue-50 to-blue-100 dark:from-gray-900 dark:to-gray-800">
        <div className="w-full max-w-md space-y-6 rounded-xl bg-white p-8 shadow-xl dark:bg-gray-900 text-center">
          <div className="mx-auto flex h-16 w-16 items-center justify-center rounded-full bg-blue-100 dark:bg-blue-900">
            <Loader2 className="h-8 w-8 animate-spin text-blue-600 dark:text-blue-400" />
          </div>
          <h1 className="mt-6 text-2xl font-bold tracking-tight">
            Carregando...
          </h1>
        </div>
      </div>
    }>
      <MagicActivateContent />
    </Suspense>
  )
}
