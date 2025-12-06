'use client'

import { useState, useEffect } from 'react'
import { useRouter } from 'next/navigation'
import Link from 'next/link'
import { Waves, Loader2, CheckCircle, AlertCircle, ArrowLeft } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { signupService } from '@/lib/api/services/signup'

// Utility to generate slug from company name
function generateSlug(name: string): string {
  return name
    .toLowerCase()
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '') // Remove accents
    .replace(/[^a-z0-9]+/g, '-')     // Replace non-alphanumeric with dash
    .replace(/^-+|-+$/g, '')          // Remove leading/trailing dashes
    .substring(0, 30)
}

export default function SignupPage() {
  const router = useRouter()
  const [isLoading, setIsLoading] = useState(false)
  const [isCheckingSlug, setIsCheckingSlug] = useState(false)
  const [slugAvailable, setSlugAvailable] = useState<boolean | null>(null)
  const [success, setSuccess] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const [formData, setFormData] = useState({
    razaoSocial: '',
    slug: '',
    cnpj: '',
    adminEmail: '',
    adminNome: '',
  })

  // Auto-generate slug from company name
  useEffect(() => {
    if (formData.razaoSocial && !formData.slug) {
      const generatedSlug = generateSlug(formData.razaoSocial)
      setFormData(prev => ({ ...prev, slug: generatedSlug }))
    }
  }, [formData.razaoSocial])

  // Check slug availability with debounce
  useEffect(() => {
    if (!formData.slug || formData.slug.length < 3) {
      setSlugAvailable(null)
      return
    }

    const timer = setTimeout(async () => {
      setIsCheckingSlug(true)
      try {
        const available = await signupService.checkSlugAvailability(formData.slug)
        setSlugAvailable(available)
      } catch {
        setSlugAvailable(null)
      } finally {
        setIsCheckingSlug(false)
      }
    }, 500)

    return () => clearTimeout(timer)
  }, [formData.slug])

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const { name, value } = e.target
    setFormData(prev => ({ ...prev, [name]: value }))
    setError(null)
  }

  const handleSlugChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const value = e.target.value.toLowerCase().replace(/[^a-z0-9-]/g, '')
    setFormData(prev => ({ ...prev, slug: value }))
    setSlugAvailable(null)
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError(null)
    setIsLoading(true)

    try {
      await signupService.signupTenant({
        razaoSocial: formData.razaoSocial,
        slug: formData.slug,
        cnpj: formData.cnpj || undefined,
        adminEmail: formData.adminEmail,
        adminNome: formData.adminNome,
      })
      setSuccess(true)
    } catch (err: unknown) {
      const error = err as { response?: { data?: { message?: string } } }
      setError(error.response?.data?.message || 'Erro ao criar conta. Tente novamente.')
    } finally {
      setIsLoading(false)
    }
  }

  if (success) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-auth-gradient py-8">
        <div className="w-full max-w-md space-y-6 rounded-xl bg-white p-8 shadow-xl">
          <div className="text-center">
            <div className="mx-auto flex h-20 w-20 items-center justify-center rounded-full bg-green-100">
              <CheckCircle className="h-10 w-10 text-green-600" />
            </div>
            <h1 className="mt-6 text-2xl font-bold tracking-tight">
              Cadastro Realizado!
            </h1>
            <p className="mt-4 text-muted-foreground">
              Enviamos um email para <strong className="text-foreground">{formData.adminEmail}</strong> com instruções para ativar sua conta.
            </p>
            <p className="mt-2 text-sm text-muted-foreground">
              Verifique também a pasta de spam.
            </p>
          </div>

          <div className="mt-8">
            <Button
              variant="outline"
              className="w-full h-12"
              onClick={() => router.push('/login')}
            >
              <ArrowLeft className="mr-2 h-4 w-4" />
              Voltar para Login
            </Button>
          </div>
        </div>
      </div>
    )
  }

  return (
    <div className="flex min-h-screen">
      {/* Left side - Branding (hidden on mobile) */}
      <div className="hidden lg:flex lg:w-1/2 bg-ocean-gradient items-center justify-center p-12">
        <div className="text-center text-white max-w-lg">
          <Waves className="h-20 w-20 mx-auto mb-6" />
          <h1 className="text-4xl font-bold tracking-tight mb-4">
            Pega o Jet
          </h1>
          <p className="text-xl text-white/90 mb-8">
            A plataforma completa para gestão de locadoras de jet skis
          </p>

          <div className="bg-white/10 rounded-xl p-6 backdrop-blur-sm">
            <h3 className="font-semibold text-lg mb-4">Seu trial gratuito inclui:</h3>
            <ul className="text-left space-y-3 text-white/90">
              <li className="flex items-center gap-2">
                <CheckCircle className="h-5 w-5 text-green-300 flex-shrink-0" />
                <span>14 dias de acesso completo</span>
              </li>
              <li className="flex items-center gap-2">
                <CheckCircle className="h-5 w-5 text-green-300 flex-shrink-0" />
                <span>Até 5 jet skis cadastrados</span>
              </li>
              <li className="flex items-center gap-2">
                <CheckCircle className="h-5 w-5 text-green-300 flex-shrink-0" />
                <span>3 usuários inclusos</span>
              </li>
              <li className="flex items-center gap-2">
                <CheckCircle className="h-5 w-5 text-green-300 flex-shrink-0" />
                <span>Suporte por email</span>
              </li>
              <li className="flex items-center gap-2">
                <CheckCircle className="h-5 w-5 text-green-300 flex-shrink-0" />
                <span>Sem necessidade de cartão de crédito</span>
              </li>
            </ul>
          </div>
        </div>
      </div>

      {/* Right side - Signup Form */}
      <div className="flex w-full lg:w-1/2 items-center justify-center bg-auth-gradient py-8 px-4">
        <div className="w-full max-w-md space-y-6">
          {/* Mobile Logo */}
          <div className="lg:hidden text-center mb-6">
            <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-full bg-primary">
              <Waves className="h-7 w-7 text-primary-foreground" />
            </div>
            <h1 className="mt-3 text-2xl font-bold tracking-tight text-primary">
              Pega o Jet
            </h1>
          </div>

          <div className="rounded-xl bg-white p-6 sm:p-8 shadow-xl">
            <div className="text-center mb-6">
              <h2 className="text-2xl font-bold tracking-tight">
                Criar Conta
              </h2>
              <p className="mt-2 text-sm text-muted-foreground">
                Comece seu trial gratuito de 14 dias
              </p>
            </div>

            {error && (
              <Alert variant="destructive" className="mb-4">
                <AlertCircle className="h-4 w-4" />
                <AlertDescription>{error}</AlertDescription>
              </Alert>
            )}

            <form onSubmit={handleSubmit} className="space-y-4">
              <div className="space-y-2">
                <Label htmlFor="razaoSocial">Nome da Empresa *</Label>
                <Input
                  id="razaoSocial"
                  name="razaoSocial"
                  type="text"
                  required
                  value={formData.razaoSocial}
                  onChange={handleChange}
                  placeholder="Minha Locadora de Jet Skis"
                  className="h-11"
                />
              </div>

              <div className="space-y-2">
                <Label htmlFor="slug">Identificador (URL) *</Label>
                <div className="flex items-center gap-2">
                  <Input
                    id="slug"
                    name="slug"
                    type="text"
                    required
                    value={formData.slug}
                    onChange={handleSlugChange}
                    placeholder="minha-locadora"
                    className="flex-1 h-11"
                  />
                  {isCheckingSlug && (
                    <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />
                  )}
                  {!isCheckingSlug && slugAvailable === true && (
                    <CheckCircle className="h-5 w-5 text-green-600" />
                  )}
                  {!isCheckingSlug && slugAvailable === false && (
                    <AlertCircle className="h-5 w-5 text-red-600" />
                  )}
                </div>
                <p className="text-xs text-muted-foreground">
                  Seu sistema: <span className="font-medium">{formData.slug || 'identificador'}</span>.pegaojet.com.br
                </p>
                {slugAvailable === false && (
                  <p className="text-xs text-red-600">
                    Este identificador já está em uso
                  </p>
                )}
              </div>

              <div className="space-y-2">
                <Label htmlFor="cnpj">CNPJ (opcional)</Label>
                <Input
                  id="cnpj"
                  name="cnpj"
                  type="text"
                  value={formData.cnpj}
                  onChange={handleChange}
                  placeholder="00.000.000/0001-00"
                  className="h-11"
                />
              </div>

              <div className="border-t pt-4">
                <p className="text-sm font-medium mb-4 text-muted-foreground">Dados do Administrador</p>

                <div className="space-y-4">
                  <div className="space-y-2">
                    <Label htmlFor="adminNome">Seu Nome *</Label>
                    <Input
                      id="adminNome"
                      name="adminNome"
                      type="text"
                      required
                      value={formData.adminNome}
                      onChange={handleChange}
                      placeholder="João Silva"
                      className="h-11"
                    />
                  </div>

                  <div className="space-y-2">
                    <Label htmlFor="adminEmail">Seu Email *</Label>
                    <Input
                      id="adminEmail"
                      name="adminEmail"
                      type="email"
                      required
                      value={formData.adminEmail}
                      onChange={handleChange}
                      placeholder="joao@empresa.com"
                      className="h-11"
                    />
                  </div>
                </div>
              </div>

              <Button
                type="submit"
                className="w-full h-12 text-base font-semibold"
                size="lg"
                disabled={isLoading || slugAvailable === false}
              >
                {isLoading ? (
                  <>
                    <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                    Criando conta...
                  </>
                ) : (
                  'Criar Conta Gratuita'
                )}
              </Button>
            </form>

            <p className="mt-6 text-center text-sm text-muted-foreground">
              Já tem uma conta?{' '}
              <Link href="/login" className="text-primary hover:underline font-medium">
                Fazer login
              </Link>
            </p>
          </div>

          <p className="text-center text-xs text-muted-foreground px-4">
            Ao criar uma conta, você concorda com nossos{' '}
            <a href="#" className="text-primary hover:underline">Termos de Serviço</a>
            {' '}e{' '}
            <a href="#" className="text-primary hover:underline">Política de Privacidade</a>.
          </p>
        </div>
      </div>
    </div>
  )
}
