'use client'

import { useQuery } from '@tanstack/react-query'
import {
  Ship,
  Anchor,
  Users,
  DollarSign,
  TrendingUp,
  Calendar,
  AlertTriangle,
  Wrench,
} from 'lucide-react'
import { useTenantStore } from '@/lib/store/tenant-store'
import { jetskisService, locacoesService, clientesService } from '@/lib/api/services'
import { formatCurrency } from '@/lib/utils'
import { Skeleton } from '@/components/ui/skeleton'

interface StatCardProps {
  title: string
  value: string | number
  description?: string
  icon: React.ReactNode
  trend?: {
    value: number
    label: string
  }
  variant?: 'default' | 'success' | 'warning' | 'danger'
}

function StatCard({ title, value, description, icon, trend, variant = 'default' }: StatCardProps) {
  const variantStyles = {
    default: 'bg-card',
    success: 'bg-green-50 dark:bg-green-950',
    warning: 'bg-yellow-50 dark:bg-yellow-950',
    danger: 'bg-red-50 dark:bg-red-950',
  }

  return (
    <div className={`rounded-xl border p-6 ${variantStyles[variant]}`}>
      <div className="flex items-center justify-between">
        <div>
          <p className="text-sm font-medium text-muted-foreground">{title}</p>
          <p className="mt-2 text-3xl font-bold">{value}</p>
          {description && (
            <p className="mt-1 text-sm text-muted-foreground">{description}</p>
          )}
          {trend && (
            <div className="mt-2 flex items-center gap-1 text-sm">
              <TrendingUp className={`h-4 w-4 ${trend.value >= 0 ? 'text-green-600' : 'text-red-600'}`} />
              <span className={trend.value >= 0 ? 'text-green-600' : 'text-red-600'}>
                {trend.value > 0 ? '+' : ''}{trend.value}%
              </span>
              <span className="text-muted-foreground">{trend.label}</span>
            </div>
          )}
        </div>
        <div className="flex h-12 w-12 items-center justify-center rounded-full bg-primary/10 text-primary">
          {icon}
        </div>
      </div>
    </div>
  )
}

function StatCardSkeleton() {
  return (
    <div className="rounded-xl border bg-card p-6">
      <div className="flex items-center justify-between">
        <div className="space-y-2">
          <Skeleton className="h-4 w-24" />
          <Skeleton className="h-8 w-16" />
          <Skeleton className="h-4 w-32" />
        </div>
        <Skeleton className="h-12 w-12 rounded-full" />
      </div>
    </div>
  )
}

export default function DashboardPage() {
  const { currentTenant } = useTenantStore()

  const { data: jetskis, isLoading: loadingJetskis } = useQuery({
    queryKey: ['jetskis', currentTenant?.id],
    queryFn: () => jetskisService.list(),
    enabled: !!currentTenant,
  })

  const { data: locacoes, isLoading: loadingLocacoes } = useQuery({
    queryKey: ['locacoes-ativas', currentTenant?.id],
    queryFn: () => locacoesService.list({ status: 'EM_CURSO' }),
    enabled: !!currentTenant,
  })

  const { data: clientes, isLoading: loadingClientes } = useQuery({
    queryKey: ['clientes', currentTenant?.id],
    queryFn: () => clientesService.list(),
    enabled: !!currentTenant,
  })

  const isLoading = loadingJetskis || loadingLocacoes || loadingClientes

  // Calculate stats - backend returns simple arrays, not paginated
  const totalJetskis = jetskis?.length || 0
  const jetskisDisponiveis = jetskis?.filter(j => j.status === 'DISPONIVEL').length || 0
  const jetskisLocados = jetskis?.filter(j => j.status === 'LOCADO').length || 0
  const jetskisManutencao = jetskis?.filter(j => j.status === 'MANUTENCAO').length || 0
  const locacoesAtivas = locacoes?.length || 0
  const totalClientes = clientes?.length || 0

  // Mock revenue data (would come from API)
  const receitaHoje = 4500.00
  const receitaMes = 45000.00

  if (!currentTenant) {
    return (
      <div className="flex h-[50vh] items-center justify-center">
        <div className="text-center">
          <AlertTriangle className="mx-auto h-12 w-12 text-yellow-500" />
          <h2 className="mt-4 text-xl font-semibold">Nenhum tenant selecionado</h2>
          <p className="mt-2 text-muted-foreground">
            Selecione um tenant no menu lateral para continuar.
          </p>
        </div>
      </div>
    )
  }

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-3xl font-bold">Dashboard</h1>
        <p className="text-muted-foreground">
          Visão geral das operações de {currentTenant.razaoSocial}
        </p>
      </div>

      {/* Main Stats */}
      <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
        {isLoading ? (
          <>
            <StatCardSkeleton />
            <StatCardSkeleton />
            <StatCardSkeleton />
            <StatCardSkeleton />
          </>
        ) : (
          <>
            <StatCard
              title="Receita Hoje"
              value={formatCurrency(receitaHoje)}
              icon={<DollarSign className="h-6 w-6" />}
              trend={{ value: 12, label: 'vs ontem' }}
              variant="success"
            />
            <StatCard
              title="Locações Ativas"
              value={locacoesAtivas}
              description={`de ${totalJetskis} jetskis`}
              icon={<Anchor className="h-6 w-6" />}
            />
            <StatCard
              title="Clientes"
              value={totalClientes}
              icon={<Users className="h-6 w-6" />}
              trend={{ value: 8, label: 'este mês' }}
            />
            <StatCard
              title="Receita Mensal"
              value={formatCurrency(receitaMes)}
              icon={<Calendar className="h-6 w-6" />}
              trend={{ value: 15, label: 'vs mês anterior' }}
            />
          </>
        )}
      </div>

      {/* Fleet Status */}
      <div className="grid gap-4 md:grid-cols-3">
        {isLoading ? (
          <>
            <StatCardSkeleton />
            <StatCardSkeleton />
            <StatCardSkeleton />
          </>
        ) : (
          <>
            <StatCard
              title="Jetskis Disponíveis"
              value={jetskisDisponiveis}
              description="prontos para locação"
              icon={<Ship className="h-6 w-6" />}
              variant="success"
            />
            <StatCard
              title="Jetskis Locados"
              value={jetskisLocados}
              description="em uso no momento"
              icon={<Anchor className="h-6 w-6" />}
              variant="default"
            />
            <StatCard
              title="Em Manutenção"
              value={jetskisManutencao}
              description="indisponíveis"
              icon={<Wrench className="h-6 w-6" />}
              variant={jetskisManutencao > 0 ? 'warning' : 'default'}
            />
          </>
        )}
      </div>

      {/* Recent Activity */}
      <div className="grid gap-4 lg:grid-cols-2">
        <div className="rounded-xl border bg-card p-6">
          <h3 className="text-lg font-semibold">Locações Recentes</h3>
          <p className="text-sm text-muted-foreground">Últimas 5 locações realizadas</p>

          <div className="mt-4 space-y-4">
            {locacoes && locacoes.length > 0 ? (
              locacoes.slice(0, 5).map((locacao) => (
                <div key={locacao.id} className="flex items-center justify-between border-b pb-2 last:border-0">
                  <div>
                    <p className="font-medium">{locacao.jetskiSerie || `Jetski #${locacao.jetskiId.slice(0, 8)}`}</p>
                    <p className="text-sm text-muted-foreground">
                      {locacao.clienteNome || 'Cliente não informado'}
                    </p>
                  </div>
                  <div className="text-right">
                    <span className="inline-flex items-center rounded-full bg-green-100 px-2.5 py-0.5 text-xs font-medium text-green-800 dark:bg-green-900 dark:text-green-100">
                      Em curso
                    </span>
                  </div>
                </div>
              ))
            ) : (
              <p className="text-center text-muted-foreground py-4">
                Nenhuma locação ativa no momento
              </p>
            )}
          </div>
        </div>

        <div className="rounded-xl border bg-card p-6">
          <h3 className="text-lg font-semibold">Ações Rápidas</h3>
          <p className="text-sm text-muted-foreground">Operações frequentes</p>

          <div className="mt-4 grid gap-2">
            <a
              href="/dashboard/locacoes?action=checkin"
              className="flex items-center gap-3 rounded-lg border p-3 hover:bg-accent transition-colors"
            >
              <div className="flex h-10 w-10 items-center justify-center rounded-full bg-green-100 text-green-600 dark:bg-green-900 dark:text-green-100">
                <Anchor className="h-5 w-5" />
              </div>
              <div>
                <p className="font-medium">Novo Check-in</p>
                <p className="text-sm text-muted-foreground">Iniciar nova locação</p>
              </div>
            </a>
            <a
              href="/dashboard/agenda"
              className="flex items-center gap-3 rounded-lg border p-3 hover:bg-accent transition-colors"
            >
              <div className="flex h-10 w-10 items-center justify-center rounded-full bg-blue-100 text-blue-600 dark:bg-blue-900 dark:text-blue-100">
                <Calendar className="h-5 w-5" />
              </div>
              <div>
                <p className="font-medium">Nova Reserva</p>
                <p className="text-sm text-muted-foreground">Agendar locação futura</p>
              </div>
            </a>
            <a
              href="/dashboard/clientes?action=new"
              className="flex items-center gap-3 rounded-lg border p-3 hover:bg-accent transition-colors"
            >
              <div className="flex h-10 w-10 items-center justify-center rounded-full bg-purple-100 text-purple-600 dark:bg-purple-900 dark:text-purple-100">
                <Users className="h-5 w-5" />
              </div>
              <div>
                <p className="font-medium">Novo Cliente</p>
                <p className="text-sm text-muted-foreground">Cadastrar cliente</p>
              </div>
            </a>
          </div>
        </div>
      </div>
    </div>
  )
}
