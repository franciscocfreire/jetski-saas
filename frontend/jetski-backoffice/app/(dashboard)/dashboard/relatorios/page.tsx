'use client'

import { useState } from 'react'
import {
  ChartBar,
  Calendar,
  DollarSign,
  Ship,
  TrendingUp,
  Download,
} from 'lucide-react'
import { useTenantStore } from '@/lib/store/tenant-store'
import { formatCurrency } from '@/lib/utils'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'

interface StatCardProps {
  title: string
  value: string | number
  description?: string
  icon: React.ReactNode
  trend?: number
}

function StatCard({ title, value, description, icon, trend }: StatCardProps) {
  return (
    <div className="rounded-xl border bg-card p-6">
      <div className="flex items-center justify-between">
        <div>
          <p className="text-sm font-medium text-muted-foreground">{title}</p>
          <p className="mt-2 text-3xl font-bold">{value}</p>
          {description && (
            <p className="mt-1 text-sm text-muted-foreground">{description}</p>
          )}
          {trend !== undefined && (
            <div className="mt-2 flex items-center gap-1 text-sm">
              <TrendingUp className={`h-4 w-4 ${trend >= 0 ? 'text-green-600' : 'text-red-600'}`} />
              <span className={trend >= 0 ? 'text-green-600' : 'text-red-600'}>
                {trend > 0 ? '+' : ''}{trend}%
              </span>
              <span className="text-muted-foreground">vs período anterior</span>
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

export default function RelatoriosPage() {
  const { currentTenant } = useTenantStore()

  const [periodo, setPeriodo] = useState('mes')
  const [dataInicio, setDataInicio] = useState('')
  const [dataFim, setDataFim] = useState('')

  // Mock data - would come from API
  const stats = {
    receitaTotal: 125000,
    totalLocacoes: 342,
    ticketMedio: 365.5,
    taxaOcupacao: 78,
    receitaCombustivel: 15000,
    comissoesPagas: 12500,
  }

  if (!currentTenant) {
    return (
      <div className="flex h-[50vh] items-center justify-center">
        <p className="text-muted-foreground">Selecione um tenant para continuar</p>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold">Relatórios</h1>
          <p className="text-muted-foreground">Análise de desempenho e fechamentos</p>
        </div>
        <Button variant="outline">
          <Download className="mr-2 h-4 w-4" />
          Exportar PDF
        </Button>
      </div>

      {/* Filters */}
      <div className="flex flex-wrap gap-4 rounded-lg border bg-card p-4">
        <div className="grid gap-2">
          <Label>Período</Label>
          <Select value={periodo} onValueChange={setPeriodo}>
            <SelectTrigger className="w-40">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="hoje">Hoje</SelectItem>
              <SelectItem value="semana">Esta semana</SelectItem>
              <SelectItem value="mes">Este mês</SelectItem>
              <SelectItem value="ano">Este ano</SelectItem>
              <SelectItem value="custom">Personalizado</SelectItem>
            </SelectContent>
          </Select>
        </div>

        {periodo === 'custom' && (
          <>
            <div className="grid gap-2">
              <Label>Data Início</Label>
              <Input
                type="date"
                value={dataInicio}
                onChange={(e) => setDataInicio(e.target.value)}
              />
            </div>
            <div className="grid gap-2">
              <Label>Data Fim</Label>
              <Input
                type="date"
                value={dataFim}
                onChange={(e) => setDataFim(e.target.value)}
              />
            </div>
          </>
        )}

        <div className="flex items-end">
          <Button>Aplicar Filtros</Button>
        </div>
      </div>

      {/* Main Stats */}
      <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
        <StatCard
          title="Receita Total"
          value={formatCurrency(stats.receitaTotal)}
          icon={<DollarSign className="h-6 w-6" />}
          trend={12}
        />
        <StatCard
          title="Total de Locações"
          value={stats.totalLocacoes}
          icon={<Ship className="h-6 w-6" />}
          trend={8}
        />
        <StatCard
          title="Ticket Médio"
          value={formatCurrency(stats.ticketMedio)}
          icon={<ChartBar className="h-6 w-6" />}
          trend={5}
        />
      </div>

      {/* Secondary Stats */}
      <div className="grid gap-4 md:grid-cols-3">
        <StatCard
          title="Taxa de Ocupação"
          value={`${stats.taxaOcupacao}%`}
          description="Média de utilização da frota"
          icon={<Calendar className="h-6 w-6" />}
        />
        <StatCard
          title="Receita Combustível"
          value={formatCurrency(stats.receitaCombustivel)}
          description="Venda de combustível"
          icon={<DollarSign className="h-6 w-6" />}
        />
        <StatCard
          title="Comissões Pagas"
          value={formatCurrency(stats.comissoesPagas)}
          description="Total pago a vendedores"
          icon={<DollarSign className="h-6 w-6" />}
        />
      </div>

      {/* Charts placeholder */}
      <div className="grid gap-4 lg:grid-cols-2">
        <div className="rounded-xl border bg-card p-6">
          <h3 className="text-lg font-semibold">Receita por Período</h3>
          <p className="text-sm text-muted-foreground">Evolução da receita ao longo do tempo</p>
          <div className="mt-4 flex h-64 items-center justify-center rounded-lg border-2 border-dashed">
            <div className="text-center text-muted-foreground">
              <ChartBar className="mx-auto h-12 w-12" />
              <p className="mt-2">Gráfico de linha</p>
              <p className="text-sm">Integrar com Recharts</p>
            </div>
          </div>
        </div>

        <div className="rounded-xl border bg-card p-6">
          <h3 className="text-lg font-semibold">Locações por Modelo</h3>
          <p className="text-sm text-muted-foreground">Distribuição por modelo de jetski</p>
          <div className="mt-4 flex h-64 items-center justify-center rounded-lg border-2 border-dashed">
            <div className="text-center text-muted-foreground">
              <ChartBar className="mx-auto h-12 w-12" />
              <p className="mt-2">Gráfico de pizza</p>
              <p className="text-sm">Integrar com Recharts</p>
            </div>
          </div>
        </div>
      </div>

      {/* Fechamentos */}
      <div className="rounded-xl border bg-card p-6">
        <div className="flex items-center justify-between">
          <div>
            <h3 className="text-lg font-semibold">Fechamentos</h3>
            <p className="text-sm text-muted-foreground">Fechamentos diários e mensais</p>
          </div>
          <div className="flex gap-2">
            <Button variant="outline" size="sm">
              Fechamento Diário
            </Button>
            <Button variant="outline" size="sm">
              Fechamento Mensal
            </Button>
          </div>
        </div>

        <div className="mt-6 rounded-lg border-2 border-dashed p-8 text-center">
          <Calendar className="mx-auto h-12 w-12 text-muted-foreground" />
          <h4 className="mt-4 font-medium">Nenhum fechamento pendente</h4>
          <p className="mt-2 text-sm text-muted-foreground">
            Os fechamentos aparecerão aqui quando houver dados a consolidar
          </p>
        </div>
      </div>
    </div>
  )
}
