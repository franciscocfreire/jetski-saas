'use client'

import { signIn } from 'next-auth/react'
import { Ship } from 'lucide-react'
import { Button } from '@/components/ui/button'

export default function LoginPage() {
  const handleLogin = () => {
    signIn('keycloak', { callbackUrl: '/dashboard' })
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-gradient-to-br from-blue-50 to-blue-100 dark:from-gray-900 dark:to-gray-800">
      <div className="w-full max-w-md space-y-8 rounded-xl bg-white p-8 shadow-xl dark:bg-gray-900">
        <div className="text-center">
          <div className="mx-auto flex h-16 w-16 items-center justify-center rounded-full bg-primary">
            <Ship className="h-8 w-8 text-primary-foreground" />
          </div>
          <h1 className="mt-6 text-3xl font-bold tracking-tight">
            Jetski SaaS
          </h1>
          <p className="mt-2 text-sm text-muted-foreground">
            Sistema de Gestão de Locação de Jetskis
          </p>
        </div>

        <div className="mt-8 space-y-4">
          <Button
            onClick={handleLogin}
            className="w-full"
            size="lg"
          >
            Entrar com Keycloak
          </Button>

          <p className="text-center text-xs text-muted-foreground">
            Ao continuar, você concorda com nossos Termos de Serviço e Política de Privacidade.
          </p>
        </div>
      </div>
    </div>
  )
}
