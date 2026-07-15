'use client'

import { useEffect } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Building2, LogOut, Mail, Store, UserRoundSearch } from 'lucide-react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { useTenantStore } from '@/lib/store/tenant-store'
import { userTenantsService } from '@/lib/api/services'

/** Deriva a URL do portal do cliente a partir do host atual (app./www. → cliente.). */
function portalClienteUrl(): string {
  if (typeof window === 'undefined') return 'https://cliente.meujet.com.br'
  const { hostname, protocol } = window.location
  if (hostname.startsWith('app.') || hostname.startsWith('www.')) {
    return `${protocol}//${hostname.replace(/^(app|www)\./, 'cliente.')}`
  }
  if (hostname === 'localhost') return 'http://localhost:3003/portal'
  return 'https://cliente.meujet.com.br'
}

/**
 * Tela exibida quando o usuário autenticou mas NÃO pertence a nenhuma empresa
 * (ex.: entrou com Google no host errado, é cliente do portal, ou o convite da
 * equipe ainda não chegou). Sem isto o dashboard renderizava vazio — a pessoa
 * "logava e nada acontecia".
 *
 * Como o TenantStatusGate: consulta o servidor a cada 30s (e no focus) — se um
 * convite/vínculo chegar, o dashboard destrava sozinho, sem relogar.
 */
export function NoTenantGate() {
  const { setTenants, setCurrentTenant } = useTenantStore()

  const { data } = useQuery({
    queryKey: ['no-tenant-gate'],
    queryFn: () => userTenantsService.getMyTenants(),
    refetchInterval: 30_000,
    refetchOnWindowFocus: true,
  })

  useEffect(() => {
    const memberships = data?.tenants ?? []
    if (memberships.length > 0) {
      toast.success('Seu acesso chegou! Bem-vindo ao Meu Jet.')
      setTenants(memberships)
      setCurrentTenant(memberships[0]) // layout re-renderiza e tira o gate da frente
    }
  }, [data, setTenants, setCurrentTenant])

  return (
    <div className="flex min-h-screen items-center justify-center bg-auth-gradient p-6">
      <Card className="w-full max-w-lg">
        <CardHeader className="text-center">
          <UserRoundSearch className="mx-auto mb-2 size-12 text-muted-foreground" />
          <CardTitle>Sua conta ainda não está em nenhuma empresa</CardTitle>
          <p className="mt-1 text-sm text-muted-foreground">
            Você entrou como <strong>conta pessoal</strong> — este painel é para as
            equipes das locadoras. Veja qual é o seu caso:
          </p>
        </CardHeader>
        <CardContent className="space-y-3">
          <div className="flex items-start gap-3 rounded-lg border p-3">
            <Store className="mt-0.5 size-5 shrink-0 text-primary" />
            <div className="flex-1">
              <p className="text-sm font-medium">Quero alugar um jet ski</p>
              <p className="text-xs text-muted-foreground">
                Reservas, pagamentos e habilitação ficam no portal do cliente.
              </p>
              <Button asChild size="sm" className="mt-2">
                <a href={portalClienteUrl()}>Ir para o portal do cliente</a>
              </Button>
            </div>
          </div>

          <div className="flex items-start gap-3 rounded-lg border p-3">
            <Building2 className="mt-0.5 size-5 shrink-0 text-primary" />
            <div className="flex-1">
              <p className="text-sm font-medium">Tenho uma locadora e quero usar o Meu Jet</p>
              <p className="text-xs text-muted-foreground">
                Cadastre sua empresa — a conta entra em análise e você recebe a liberação.
              </p>
              <Button asChild size="sm" variant="outline" className="mt-2">
                <a href="/signup">Cadastrar minha empresa</a>
              </Button>
            </div>
          </div>

          <div className="flex items-start gap-3 rounded-lg border p-3">
            <Mail className="mt-0.5 size-5 shrink-0 text-primary" />
            <div className="flex-1">
              <p className="text-sm font-medium">Trabalho numa locadora que já usa o Meu Jet</p>
              <p className="text-xs text-muted-foreground">
                Peça ao administrador da empresa para enviar seu convite (menu{' '}
                <strong>Equipe</strong>) para <strong>este mesmo e-mail</strong>. Esta tela
                se atualiza sozinha quando o acesso chegar.
              </p>
            </div>
          </div>

          <div className="pt-1 text-center">
            <Button asChild variant="ghost" size="sm" className="text-muted-foreground">
              <a href="/logout">
                <LogOut className="mr-1 size-3.5" /> Sair da conta
              </a>
            </Button>
          </div>
        </CardContent>
      </Card>
    </div>
  )
}
