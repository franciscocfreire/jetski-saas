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
  Clock,
  Timer,
} from 'lucide-react'
import { useTenantStore } from '@/lib/store/tenant-store'
import { jetskisService, locacoesService, clientesService, dashboardService } from '@/lib/api/services'
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

// Helper functions for time display
function formatTime(dateString: string): string {
  const date = new Date(dateString)
  return date.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })
}

function calculateEndTime(checkInDate: string, durationMinutes?: number): string {
  if (!durationMinutes) return '--:--'
  const date = new Date(checkInDate)
  date.setMinutes(date.getMinutes() + durationMinutes)
  return date.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })
}

function getTimeStatus(checkInDate: string, durationMinutes?: number): {
  status: 'ok' | 'warning' | 'exceeded'
  minutesRemaining: number
} {
  if (!durationMinutes) return { status: 'ok', minutesRemaining: 0 }

  const checkIn = new Date(checkInDate)
  const endTime = new Date(checkIn.getTime() + durationMinutes * 60 * 1000)
  const now = new Date()
  const minutesRemaining = Math.round((endTime.getTime() - now.getTime()) / (60 * 1000))

  if (minutesRemaining < 0) return { status: 'exceeded', minutesRemaining }
  if (minutesRemaining <= 15) return { status: 'warning', minutesRemaining }
  return { status: 'ok', minutesRemaining }
}

function formatTimeRemaining(minutes: number): string {
  if (minutes < 0) {
    const exceeded = Math.abs(minutes)
    if (exceeded >= 60) {
      return `+${Math.floor(exceeded / 60)}h${exceeded % 60}min`
    }
    return `+${exceeded}min`
  }
  if (minutes >= 60) {
    return `${Math.floor(minutes / 60)}h${minutes % 60}min`
  }
  return `${minutes}min`
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

  // Dashboard metrics (cached on backend)
  const { data: metrics, isLoading: loadingMetrics } = useQuery({
    queryKey: ['dashboard-metrics', currentTenant?.id],
    queryFn: () => dashboardService.getMetrics(),
    enabled: !!currentTenant,
    staleTime: 1000 * 60 * 5, // 5 minutes (match backend cache TTL)
    refetchOnWindowFocus: true, // Refetch when user returns to tab
  })

  const isLoading = loadingJetskis || loadingLocacoes || loadingClientes || loadingMetrics

  // Calculate stats - backend returns simple arrays, not paginated
  const totalJetskis = jetskis?.length || 0
  const jetskisDisponiveis = jetskis?.filter(j => j.status === 'DISPONIVEL').length || 0
  const jetskisLocados = jetskis?.filter(j => j.status === 'LOCADO').length || 0
  const jetskisManutencao = jetskis?.filter(j => j.status === 'MANUTENCAO').length || 0
  const locacoesAtivas = locacoes?.length || 0
  const totalClientes = clientes?.length || 0

  // Revenue metrics from API (cached)
  const receitaHoje = metrics?.receitaHoje || 0
  const receitaMes = metrics?.receitaMes || 0
  const locacoesHoje = metrics?.locacoesHoje || 0
  const locacoesMes = metrics?.locacoesMes || 0

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
              description={`${locacoesHoje} locações finalizadas`}
              icon={<DollarSign className="h-6 w-6" />}
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
            />
            <StatCard
              title="Receita Mensal"
              value={formatCurrency(receitaMes)}
              description={`${locacoesMes} locações no mês`}
              icon={<Calendar className="h-6 w-6" />}
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
          <h3 className="text-lg font-semibold">Locações em Curso</h3>
          <p className="text-sm text-muted-foreground">Acompanhamento em tempo real</p>

          <div className="mt-4 space-y-3">
            {locacoes && locacoes.length > 0 ? (
              locacoes.slice(0, 5).map((locacao) => {
                const timeStatus = getTimeStatus(locacao.dataCheckIn, locacao.duracaoPrevista)
                const statusColors = {
                  ok: 'bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-100',
                  warning: 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900 dark:text-yellow-100',
                  exceeded: 'bg-red-100 text-red-800 dark:bg-red-900 dark:text-red-100',
                }

                return (
                  <div key={locacao.id} className="rounded-lg border p-3 hover:bg-accent/50 transition-colors">
                    <div className="flex items-start justify-between">
                      <div className="flex-1">
                        <div className="flex items-center gap-2">
                          <p className="font-semibold">{locacao.jetskiSerie || `Jetski #${locacao.jetskiId.slice(0, 8)}`}</p>
                          {locacao.jetskiModeloNome && (
                            <span className="text-xs text-muted-foreground">({locacao.jetskiModeloNome})</span>
                          )}
                        </div>
                        <p className="text-sm text-muted-foreground mt-0.5">
                          {locacao.clienteNome || 'Cliente walk-in'}
                        </p>
                      </div>
                      <span className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium ${statusColors[timeStatus.status]}`}>
                        {timeStatus.status === 'exceeded' ? 'Excedido' : timeStatus.status === 'warning' ? 'Atenção' : 'Em curso'}
                      </span>
                    </div>

                    <div className="mt-2 flex items-center gap-4 text-sm">
                      <div className="flex items-center gap-1 text-muted-foreground">
                        <Clock className="h-3.5 w-3.5" />
                        <span>Início: <span className="font-medium text-foreground">{formatTime(locacao.dataCheckIn)}</span></span>
                      </div>
                      <div className="flex items-center gap-1 text-muted-foreground">
                        <Timer className="h-3.5 w-3.5" />
                        <span>Término: <span className="font-medium text-foreground">{calculateEndTime(locacao.dataCheckIn, locacao.duracaoPrevista)}</span></span>
                      </div>
                    </div>

                    {locacao.duracaoPrevista && (
                      <div className="mt-2">
                        <div className={`inline-flex items-center gap-1 rounded px-1.5 py-0.5 text-xs ${statusColors[timeStatus.status]}`}>
                          {timeStatus.status === 'exceeded' ? (
                            <>Excedeu {formatTimeRemaining(timeStatus.minutesRemaining)}</>
                          ) : (
                            <>Restam {formatTimeRemaining(timeStatus.minutesRemaining)}</>
                          )}
                        </div>
                      </div>
                    )}
                  </div>
                )
              })
            ) : (
              <p className="text-center text-muted-foreground py-8">
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
