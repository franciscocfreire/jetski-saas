'use client'

import { signIn } from 'next-auth/react'
import Link from 'next/link'
import { Button } from '@/components/ui/button'
import { Logo } from '@/components/logo'

/** "G" oficial do Google (lucide não tem marcas). */
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
  const handleLogin = () => {
    signIn('keycloak', { callbackUrl: '/dashboard' })
  }

  const handleLoginGoogle = () => {
    // kc_idp_hint: o Keycloak pula a própria tela e vai direto ao Google.
    // Conta staff existente com o mesmo e-mail passa pela confirmação do
    // Keycloak ("conta já existe") e mantém papéis/tenant.
    signIn('keycloak', { callbackUrl: '/dashboard' }, { kc_idp_hint: 'google' })
  }

  return (
    <div className="flex min-h-screen">
      {/* Left side - Branding */}
      <div className="hidden lg:flex lg:w-1/2 bg-ocean-gradient items-center justify-center p-12">
        <div className="text-center text-white">
          <div className="mb-8">
            <Logo variant="full" theme="dark" size={56} className="mx-auto mb-4 flex-col gap-4" />
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
          <div className="lg:hidden mb-8 flex justify-center">
            <Logo variant="full" theme="light" size={40} />
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

              <Button
                onClick={handleLoginGoogle}
                variant="outline"
                className="w-full h-12 text-base gap-2"
                size="lg"
              >
                <GoogleIcon /> Entrar com Google
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
              <a href="/termos" target="_blank" className="text-primary hover:underline">Termos de Uso</a>
              {' '}e{' '}
              <a href="/privacidade" target="_blank" className="text-primary hover:underline">Política de Privacidade</a>.
            </p>
          </div>

          <p className="text-center text-sm text-muted-foreground">
            Precisa de ajuda? Veja a{' '}
            <a href="/ajuda" className="text-primary hover:underline font-medium">Central de Ajuda</a>
            {' '}ou{' '}
            <a href="mailto:suporte@meujet.com.br" className="text-primary hover:underline font-medium">
              fale conosco
            </a>
          </p>
        </div>
      </div>
    </div>
  )
}
