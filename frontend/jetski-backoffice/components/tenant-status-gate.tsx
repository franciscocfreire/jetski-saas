'use client'

import { Clock, ShieldAlert, Ban } from 'lucide-react'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import type { TenantSummary } from '@/lib/api/types'

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
 */
export function TenantStatusGate({ tenant }: { tenant: TenantSummary }) {
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
          <p className="text-xs text-muted-foreground">
            Se você tem acesso a outra empresa, use o seletor no topo da barra lateral.
          </p>
        </CardContent>
      </Card>
    </div>
  )
}
