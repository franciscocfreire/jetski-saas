'use client'

import { signIn } from 'next-auth/react'
import Link from 'next/link'
import { Waves } from 'lucide-react'
import { Button } from '@/components/ui/button'

export default function LoginPage() {
  const handleLogin = () => {
    signIn('keycloak', { callbackUrl: '/dashboard' })
  }

  return (
    <div className="flex min-h-screen">
      {/* Left side - Branding */}
      <div className="hidden lg:flex lg:w-1/2 bg-ocean-gradient items-center justify-center p-12">
        <div className="text-center text-white">
          <div className="mb-8">
            <Waves className="h-24 w-24 mx-auto mb-4 animate-pulse" />
            <h1 className="text-5xl font-bold tracking-tight">
              Pega o Jet
            </h1>
            <p className="mt-4 text-xl text-white/80">
              Gestão completa para sua frota de jet skis
            </p>
          </div>
          <div className="mt-12 space-y-4 text-left max-w-md mx-auto">
            <div className="flex items-center gap-3">
              <div className="h-10 w-10 rounded-full bg-white/20 flex items-center justify-center">
                <span className="text-lg">1</span>
              </div>
              <p className="text-white/90">Controle de reservas e locações em tempo real</p>
            </div>
            <div className="flex items-center gap-3">
              <div className="h-10 w-10 rounded-full bg-white/20 flex items-center justify-center">
                <span className="text-lg">2</span>
              </div>
              <p className="text-white/90">Check-in e check-out com fotos obrigatórias</p>
            </div>
            <div className="flex items-center gap-3">
              <div className="h-10 w-10 rounded-full bg-white/20 flex items-center justify-center">
                <span className="text-lg">3</span>
              </div>
              <p className="text-white/90">Gestão de manutenção e combustível</p>
            </div>
            <div className="flex items-center gap-3">
              <div className="h-10 w-10 rounded-full bg-white/20 flex items-center justify-center">
                <span className="text-lg">4</span>
              </div>
              <p className="text-white/90">Comissões e fechamentos automatizados</p>
            </div>
          </div>
        </div>
      </div>

      {/* Right side - Login Form */}
      <div className="flex w-full lg:w-1/2 items-center justify-center bg-auth-gradient p-8">
        <div className="w-full max-w-md space-y-8">
          {/* Mobile Logo */}
          <div className="lg:hidden text-center mb-8">
            <div className="mx-auto flex h-16 w-16 items-center justify-center rounded-full bg-primary">
              <Waves className="h-8 w-8 text-primary-foreground" />
            </div>
            <h1 className="mt-4 text-3xl font-bold tracking-tight text-primary">
              Pega o Jet
            </h1>
          </div>

          <div className="rounded-xl bg-white p-8 shadow-xl">
            <div className="text-center mb-8">
              <h2 className="text-2xl font-bold tracking-tight">
                Bem-vindo de volta!
              </h2>
              <p className="mt-2 text-sm text-muted-foreground">
                Acesse sua conta para gerenciar sua frota
              </p>
            </div>

            <div className="space-y-4">
              <Button
                onClick={handleLogin}
                className="w-full h-12 text-base font-semibold"
                size="lg"
              >
                Entrar com sua conta
              </Button>

              <div className="relative my-6">
                <div className="absolute inset-0 flex items-center">
                  <span className="w-full border-t" />
                </div>
                <div className="relative flex justify-center text-xs uppercase">
                  <span className="bg-white px-2 text-muted-foreground">
                    Novo por aqui?
                  </span>
                </div>
              </div>

              <Button
                variant="outline"
                className="w-full h-12 text-base border-2 border-primary text-primary hover:bg-primary hover:text-primary-foreground transition-all"
                size="lg"
                asChild
              >
                <Link href="/signup">
                  Criar Conta Gratuita
                </Link>
              </Button>
            </div>

            <p className="mt-6 text-center text-xs text-muted-foreground">
              Ao continuar, você concorda com nossos{' '}
              <a href="#" className="text-primary hover:underline">Termos de Serviço</a>
              {' '}e{' '}
              <a href="#" className="text-primary hover:underline">Política de Privacidade</a>.
            </p>
          </div>

          <p className="text-center text-sm text-muted-foreground">
            Precisa de ajuda?{' '}
            <a href="mailto:suporte@pegaojet.com.br" className="text-primary hover:underline font-medium">
              Fale conosco
            </a>
          </p>
        </div>
      </div>
    </div>
  )
}
