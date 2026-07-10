'use client'

import { useEffect } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Clock, ShieldAlert, Ban, Loader2 } from 'lucide-react'
import { toast } from 'sonner'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { useTenantStore } from '@/lib/store/tenant-store'
import { userTenantsService } from '@/lib/api/services'
import type { TenantSummary } from '@/lib/api/types'

const OPERATIONAL_STATUSES = ['ATIVO', 'TRIAL']

/** Status em que uma liberação pode chegar a qualquer momento (aprovação/reativação). */
const AGUARDANDO_LIBERACAO = ['PENDENTE_APROVACAO', 'SUSPENSO']

const STATUS_INFO: Record<
  string,
  { icon: React.ComponentType<{ className?: string }>; title: string; message: string; color: string }
> = {
  PENDENTE_APROVACAO: {
    icon: Clock,
    title: 'Empresa em análise',
    message:
      'Seu cadastro foi recebido e está aguardando aprovação da nossa equipe. ' +
      'Você receberá um aviso assim que a empresa for liberada para uso.',
    color: 'text-amber-500',
  },
  SUSPENSO: {
    icon: ShieldAlert,
    title: 'Empresa suspensa',
    message:
      'O acesso a esta empresa está temporariamente suspenso. ' +
      'Entre em contato com o suporte para regularizar a situação.',
    color: 'text-red-500',
  },
  INATIVO: {
    icon: Ban,
    title: 'Empresa inativa',
    message: 'Esta empresa está inativa. Entre em contato com o suporte.',
    color: 'text-muted-foreground',
  },
  CANCELADO: {
    icon: Ban,
    title: 'Empresa cancelada',
    message: 'A assinatura desta empresa foi encerrada. Entre em contato com o suporte.',
    color: 'text-muted-foreground',
  },
}

/**
 * Tela exibida quando a empresa atual não está operacional (gate de status).
 * O usuário ainda pode trocar de empresa (switcher na sidebar) ou sair.
 *
 * Enquanto o status é "liberável" (pendente/suspensa), a tela consulta o servidor
 * a cada 30s (e no focus da aba): a aprovação do super admin destrava o dashboard
 * sozinha, sem o usuário precisar deslogar/relogar.
 */
export function TenantStatusGate({ tenant }: { tenant: TenantSummary }) {
  const { setTenants, setCurrentTenant } = useTenantStore()
  const aguardandoLiberacao = AGUARDANDO_LIBERACAO.includes(tenant.status)

  const { data } = useQuery({
    queryKey: ['tenant-status-gate', tenant.id],
    queryFn: () => userTenantsService.getMyTenants(),
    enabled: aguardandoLiberacao,
    refetchInterval: 30_000,
    refetchOnWindowFocus: true,
  })

  useEffect(() => {
    const memberships = data?.tenants ?? []
    const fresh = memberships.find((t) => t.id === tenant.id)
    if (fresh && fresh.status !== tenant.status) {
      if (OPERATIONAL_STATUSES.includes(fresh.status)) {
        toast.success('Sua empresa foi liberada! Bem-vindo ao Meu Jet.')
      }
      setTenants(memberships)
      setCurrentTenant(fresh) // atualiza o store → o layout tira o gate da frente
    }
  }, [data, tenant.id, tenant.status, setTenants, setCurrentTenant])

  const info = STATUS_INFO[tenant.status] ?? {
    icon: ShieldAlert,
    title: 'Empresa indisponível',
    message: 'Esta empresa não está disponível no momento.',
    color: 'text-muted-foreground',
  }
  const Icon = info.icon

  return (
    <div className="flex flex-1 items-center justify-center p-6">
      <Card className="max-w-md text-center">
        <CardHeader>
          <div className="mx-auto mb-2">
            <Icon className={`mx-auto size-12 ${info.color}`} />
          </div>
          <CardTitle>{info.title}</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <p className="text-sm text-muted-foreground">{info.message}</p>
          <div className="rounded-md bg-muted p-3 text-sm">
            <p className="font-medium">{tenant.razaoSocial}</p>
            <p className="text-xs text-muted-foreground">{tenant.slug}</p>
          </div>
          {aguardandoLiberacao && (
            <p className="flex items-center justify-center gap-2 text-xs text-muted-foreground">
              <Loader2 className="size-3 animate-spin" />
              Verificando liberação automaticamente — esta tela se atualiza sozinha.
            </p>
          )}
          <p className="text-xs text-muted-foreground">
            Se você tem acesso a outra empresa, use o seletor no topo da barra lateral.
          </p>
        </CardContent>
      </Card>
    </div>
  )
}
