'use client'

import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { format, startOfMonth, endOfMonth, subMonths, addMonths } from 'date-fns'
import { ptBR } from 'date-fns/locale'
import {
  ChevronLeft,
  ChevronRight,
  TrendingUp,
  TrendingDown,
  AlertCircle,
  Calendar as CalendarIcon,
  ArrowRight,
} from 'lucide-react'
import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  ResponsiveContainer,
} from 'recharts'
import Link from 'next/link'

import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { cn } from '@/lib/utils'

import { dashboardFinanceiroService } from '@/lib/api/services'
import type { DiaFinanceiro, IndicadorFinanceiro } from '@/lib/api/types'

const formatCurrency = (value: number) => {
  return new Intl.NumberFormat('pt-BR', {
    style: 'currency',
    currency: 'BRL',
  }).format(value)
}

const formatCurrencyCompact = (value: number) => {
  if (Math.abs(value) >= 1000) {
    return new Intl.NumberFormat('pt-BR', {
      style: 'currency',
      currency: 'BRL',
      notation: 'compact',
    }).format(value)
  }
  return formatCurrency(value)
}

const getIndicadorColor = (indicador: IndicadorFinanceiro) => {
  switch (indicador) {
    case 'POSITIVO':
      return 'bg-green-100 text-green-800 hover:bg-green-200'
    case 'NEGATIVO':
      return 'bg-red-100 text-red-800 hover:bg-red-200'
    default:
      return 'bg-gray-100 text-gray-600 hover:bg-gray-200'
  }
}

const WEEKDAY_LABELS = ['Dom', 'Seg', 'Ter', 'Qua', 'Qui', 'Sex', 'Sáb']

export default function DashboardFinanceiroPage() {
  const [currentDate, setCurrentDate] = useState(new Date())
  const currentYear = currentDate.getFullYear()
  const currentMonth = currentDate.getMonth() + 1

  // Query for calendar data
  const { data: calendario, isLoading: isLoadingCalendario } = useQuery({
    queryKey: ['dashboard-financeiro-calendario', currentYear, currentMonth],
    queryFn: () => dashboardFinanceiroService.getCalendario(currentYear, currentMonth),
  })

  // Query for chart data (same month)
  const { data: receitasDespesas, isLoading: isLoadingChart } = useQuery({
    queryKey: ['dashboard-financeiro-receitas-despesas', currentYear, currentMonth],
    queryFn: () => {
      const dataInicio = format(startOfMonth(currentDate), 'yyyy-MM-dd')
      const dataFim = format(endOfMonth(currentDate), 'yyyy-MM-dd')
      return dashboardFinanceiroService.getReceitasDespesas(dataInicio, dataFim)
    },
  })

  // Query for DRE
  const { data: dre, isLoading: isLoadingDRE } = useQuery({
    queryKey: ['dashboard-financeiro-dre', currentYear, currentMonth],
    queryFn: () => dashboardFinanceiroService.getDRE(currentYear, currentMonth),
  })

  // Query for pending items
  const { data: pendentes, isLoading: isLoadingPendentes } = useQuery({
    queryKey: ['dashboard-financeiro-pendentes'],
    queryFn: () => dashboardFinanceiroService.getPendentes(),
  })

  const navigateMonth = (direction: 'prev' | 'next') => {
    setCurrentDate((prev) => (direction === 'prev' ? subMonths(prev, 1) : addMonths(prev, 1)))
  }

  // Build calendar grid
  const buildCalendarGrid = (dias: DiaFinanceiro[] | undefined) => {
    if (!dias) return []

    const firstDayOfMonth = new Date(currentYear, currentMonth - 1, 1)
    const startDayOfWeek = firstDayOfMonth.getDay()
    const daysInMonth = new Date(currentYear, currentMonth, 0).getDate()

    const grid: (DiaFinanceiro | null)[] = []

    // Empty cells for days before the first day of month
    for (let i = 0; i < startDayOfWeek; i++) {
      grid.push(null)
    }

    // Fill in the days
    for (let day = 1; day <= daysInMonth; day++) {
      const dateStr = `${currentYear}-${String(currentMonth).padStart(2, '0')}-${String(day).padStart(2, '0')}`
      const diaData = dias.find((d) => d.data === dateStr)
      grid.push(
        diaData || {
          data: dateStr,
          receita: 0,
          despesasOperacionais: 0,
          combustivel: 0,
          comissoes: 0,
          diariasVendedores: 0,
          manutencoes: 0,
          totalDespesas: 0,
          saldo: 0,
          indicador: 'NEUTRO' as IndicadorFinanceiro,
          temFechamento: false,
        }
      )
    }

    return grid
  }

  const calendarGrid = buildCalendarGrid(calendario?.dias)

  // Chart data transformation
  const chartData =
    receitasDespesas?.map((d) => ({
      data: format(new Date(d.data), 'dd'),
      Receita: d.receita,
      'Despesas Op.': d.despesasOperacionais,
      Combustível: d.combustivel,
      Comissões: d.comissoes,
      'Diárias Vend.': d.diariasVendedores,
      Manutenções: d.manutencoes,
    })) || []

  return (
    <div className="flex flex-col gap-6 p-6">
      {/* Header */}
      <div className="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
        <div>
          <h1 className="text-2xl font-bold">Dashboard Financeiro</h1>
          <p className="text-muted-foreground">
            Visão geral das finanças da operação
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Button variant="outline" size="icon" onClick={() => navigateMonth('prev')}>
            <ChevronLeft className="h-4 w-4" />
          </Button>
          <div className="min-w-[150px] text-center font-medium">
            {format(currentDate, 'MMMM yyyy', { locale: ptBR })}
          </div>
          <Button variant="outline" size="icon" onClick={() => navigateMonth('next')}>
            <ChevronRight className="h-4 w-4" />
          </Button>
        </div>
      </div>

      {/* Summary Cards */}
      <div className="grid grid-cols-2 gap-4 md:grid-cols-4">
        <Card>
          <CardHeader className="pb-2">
            <CardTitle className="text-sm font-medium text-muted-foreground">
              Receitas do Mês
            </CardTitle>
          </CardHeader>
          <CardContent>
            <div className="flex items-center gap-2">
              <TrendingUp className="h-4 w-4 text-green-500" />
              <span className="text-2xl font-bold text-green-600">
                {formatCurrency(calendario?.totalReceitas || 0)}
              </span>
            </div>
          </CardContent>
        </Card>
        <Card>
          <CardHeader className="pb-2">
            <CardTitle className="text-sm font-medium text-muted-foreground">
              Despesas do Mês
            </CardTitle>
          </CardHeader>
          <CardContent>
            <div className="flex items-center gap-2">
              <TrendingDown className="h-4 w-4 text-red-500" />
              <span className="text-2xl font-bold text-red-600">
                {formatCurrency(calendario?.totalDespesas || 0)}
              </span>
            </div>
          </CardContent>
        </Card>
        <Card>
          <CardHeader className="pb-2">
            <CardTitle className="text-sm font-medium text-muted-foreground">
              Saldo do Mês
            </CardTitle>
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">
              <span
                className={
                  (calendario?.saldoMes || 0) >= 0 ? 'text-green-600' : 'text-red-600'
                }
              >
                {formatCurrency(calendario?.saldoMes || 0)}
              </span>
            </div>
          </CardContent>
        </Card>
        <Card>
          <CardHeader className="pb-2">
            <CardTitle className="text-sm font-medium text-muted-foreground">
              Margem Líquida
            </CardTitle>
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">
              <span className={(dre?.margemLiquida || 0) >= 0 ? 'text-green-600' : 'text-red-600'}>
                {(dre?.margemLiquida || 0).toFixed(1)}%
              </span>
            </div>
          </CardContent>
        </Card>
      </div>

      <Tabs defaultValue="calendario" className="space-y-4">
        <TabsList>
          <TabsTrigger value="calendario">Calendário</TabsTrigger>
          <TabsTrigger value="grafico">Receitas vs Despesas</TabsTrigger>
          <TabsTrigger value="dre">DRE</TabsTrigger>
          <TabsTrigger value="pendentes">Pendentes</TabsTrigger>
        </TabsList>

        {/* Calendar Tab */}
        <TabsContent value="calendario">
          <Card>
            <CardHeader>
              <CardTitle className="flex items-center gap-2">
                <CalendarIcon className="h-5 w-5" />
                Calendário Financeiro
              </CardTitle>
              <CardDescription>
                Visão diária: verde = lucro, vermelho = prejuízo, cinza = neutro
              </CardDescription>
            </CardHeader>
            <CardContent>
              {isLoadingCalendario ? (
                <div className="flex h-64 items-center justify-center">
                  Carregando...
                </div>
              ) : (
                <div className="space-y-4">
                  {/* Legend */}
                  <div className="flex gap-4 text-sm">
                    <div className="flex items-center gap-1">
                      <div className="h-4 w-4 rounded bg-green-100" />
                      <span>Positivo ({calendario?.diasPositivos || 0})</span>
                    </div>
                    <div className="flex items-center gap-1">
                      <div className="h-4 w-4 rounded bg-red-100" />
                      <span>Negativo ({calendario?.diasNegativos || 0})</span>
                    </div>
                    <div className="flex items-center gap-1">
                      <div className="h-4 w-4 rounded bg-gray-100" />
                      <span>Neutro ({calendario?.diasNeutros || 0})</span>
                    </div>
                  </div>

                  {/* Calendar Grid */}
                  <div className="grid grid-cols-7 gap-1">
                    {/* Weekday headers */}
                    {WEEKDAY_LABELS.map((label) => (
                      <div
                        key={label}
                        className="p-2 text-center text-xs font-medium text-muted-foreground"
                      >
                        {label}
                      </div>
                    ))}

                    {/* Calendar cells */}
                    {calendarGrid.map((dia, index) => (
                      <div
                        key={index}
                        className={cn(
                          'min-h-[80px] rounded-lg border p-2 text-xs transition-colors',
                          dia ? getIndicadorColor(dia.indicador) : 'bg-transparent'
                        )}
                      >
                        {dia && (
                          <>
                            <div className="font-medium">
                              {new Date(dia.data).getDate()}
                            </div>
                            {dia.temFechamento && (
                              <div className="mt-1 space-y-0.5">
                                <div className="text-green-700">
                                  +{formatCurrencyCompact(dia.receita)}
                                </div>
                                <div className="text-red-700">
                                  -{formatCurrencyCompact(dia.totalDespesas)}
                                </div>
                              </div>
                            )}
                            {!dia.temFechamento && dia.saldo === 0 && (
                              <div className="mt-2 text-center text-muted-foreground">
                                -
                              </div>
                            )}
                          </>
                        )}
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </CardContent>
          </Card>
        </TabsContent>

        {/* Chart Tab */}
        <TabsContent value="grafico">
          <Card>
            <CardHeader>
              <CardTitle>Receitas vs Despesas</CardTitle>
              <CardDescription>
                Comparativo diário de entradas e saídas
              </CardDescription>
            </CardHeader>
            <CardContent>
              {isLoadingChart ? (
                <div className="flex h-[400px] items-center justify-center">
                  Carregando...
                </div>
              ) : (
                <ResponsiveContainer width="100%" height={400}>
                  <BarChart data={chartData}>
                    <CartesianGrid strokeDasharray="3 3" />
                    <XAxis dataKey="data" />
                    <YAxis tickFormatter={(value) => formatCurrencyCompact(value)} />
                    <Tooltip
                      formatter={(value: number) => formatCurrency(value)}
                      labelFormatter={(label) => `Dia ${label}`}
                    />
                    <Legend />
                    <Bar dataKey="Receita" fill="#22c55e" stackId="positive" />
                    <Bar dataKey="Despesas Op." fill="#ef4444" stackId="negative" />
                    <Bar dataKey="Combustível" fill="#f97316" stackId="negative" />
                    <Bar dataKey="Comissões" fill="#3b82f6" stackId="negative" />
                    <Bar dataKey="Diárias Vend." fill="#8b5cf6" stackId="negative" />
                    <Bar dataKey="Manutenções" fill="#ec4899" stackId="negative" />
                  </BarChart>
                </ResponsiveContainer>
              )}
            </CardContent>
          </Card>
        </TabsContent>

        {/* DRE Tab */}
        <TabsContent value="dre">
          <Card>
            <CardHeader>
              <CardTitle>DRE Simplificado</CardTitle>
              <CardDescription>
                Demonstração do Resultado do Exercício -{' '}
                {format(currentDate, 'MMMM yyyy', { locale: ptBR })}
              </CardDescription>
            </CardHeader>
            <CardContent>
              {isLoadingDRE ? (
                <div className="flex h-64 items-center justify-center">
                  Carregando...
                </div>
              ) : (
                <div className="space-y-4">
                  {/* Receita Bruta */}
                  <div className="flex items-center justify-between border-b pb-2">
                    <span className="font-medium">(+) Receita Bruta</span>
                    <span className="text-lg font-bold text-green-600">
                      {formatCurrency(dre?.receitaBruta || 0)}
                    </span>
                  </div>

                  {/* Deduções */}
                  <div className="flex items-center justify-between pl-4 text-sm">
                    <span className="text-muted-foreground">(-) Deduções</span>
                    <span className="text-red-600">
                      {formatCurrency(dre?.deducoes || 0)}
                    </span>
                  </div>

                  {/* Receita Líquida */}
                  <div className="flex items-center justify-between border-b bg-muted/50 p-2">
                    <span className="font-medium">(=) Receita Líquida</span>
                    <span className="font-bold">
                      {formatCurrency(dre?.receitaLiquida || 0)}
                    </span>
                  </div>

                  {/* Custos Variáveis */}
                  <div className="space-y-2 pl-4">
                    <div className="flex items-center justify-between text-sm">
                      <span className="text-muted-foreground">(-) Combustível</span>
                      <span className="text-red-600">
                        {formatCurrency(dre?.combustivel || 0)}
                      </span>
                    </div>
                    <div className="flex items-center justify-between text-sm">
                      <span className="text-muted-foreground">(-) Comissões</span>
                      <span className="text-red-600">
                        {formatCurrency(dre?.comissoes || 0)}
                      </span>
                    </div>
                  </div>

                  {/* Lucro Bruto */}
                  <div className="flex items-center justify-between border-b bg-muted/50 p-2">
                    <span className="font-medium">(=) Lucro Bruto</span>
                    <span className="font-bold">
                      {formatCurrency(dre?.lucroBruto || 0)}
                    </span>
                  </div>

                  {/* Despesas Operacionais */}
                  <div className="space-y-2 pl-4">
                    <div className="flex items-center justify-between text-sm">
                      <span className="text-muted-foreground">(-) Diárias Funcionários</span>
                      <span className="text-red-600">
                        {formatCurrency(dre?.despesasDiarias || 0)}
                      </span>
                    </div>
                    <div className="flex items-center justify-between text-sm">
                      <span className="text-muted-foreground">(-) Diárias Vendedores</span>
                      <span className="text-red-600">
                        {formatCurrency(dre?.diariasVendedores || 0)}
                      </span>
                    </div>
                    <div className="flex items-center justify-between text-sm">
                      <span className="text-muted-foreground">(-) Refeições</span>
                      <span className="text-red-600">
                        {formatCurrency(dre?.despesasRefeicao || 0)}
                      </span>
                    </div>
                    <div className="flex items-center justify-between text-sm">
                      <span className="text-muted-foreground">(-) Transporte</span>
                      <span className="text-red-600">
                        {formatCurrency(dre?.despesasTransporte || 0)}
                      </span>
                    </div>
                    <div className="flex items-center justify-between text-sm">
                      <span className="text-muted-foreground">(-) Limpeza</span>
                      <span className="text-red-600">
                        {formatCurrency(dre?.despesasLimpeza || 0)}
                      </span>
                    </div>
                    <div className="flex items-center justify-between text-sm">
                      <span className="text-muted-foreground">(-) Outras</span>
                      <span className="text-red-600">
                        {formatCurrency(dre?.outrasDesepsas || 0)}
                      </span>
                    </div>
                  </div>

                  {/* Manutenções */}
                  <div className="flex items-center justify-between pl-4 text-sm">
                    <span className="text-muted-foreground">(-) Manutenções</span>
                    <span className="text-red-600">
                      {formatCurrency(dre?.manutencoes || 0)}
                    </span>
                  </div>

                  {/* Resultado Líquido */}
                  <div
                    className={cn(
                      'flex items-center justify-between rounded-lg p-4',
                      (dre?.resultadoLiquido || 0) >= 0
                        ? 'bg-green-100'
                        : 'bg-red-100'
                    )}
                  >
                    <span className="text-lg font-bold">(=) Resultado Líquido</span>
                    <span
                      className={cn(
                        'text-2xl font-bold',
                        (dre?.resultadoLiquido || 0) >= 0
                          ? 'text-green-700'
                          : 'text-red-700'
                      )}
                    >
                      {formatCurrency(dre?.resultadoLiquido || 0)}
                    </span>
                  </div>

                  {/* Margem */}
                  <div className="text-right text-sm text-muted-foreground">
                    Margem Líquida: {(dre?.margemLiquida || 0).toFixed(1)}%
                  </div>
                </div>
              )}
            </CardContent>
          </Card>
        </TabsContent>

        {/* Pending Items Tab */}
        <TabsContent value="pendentes">
          <div className="grid gap-4 md:grid-cols-2">
            {/* Comissões Pendentes */}
            <Card>
              <CardHeader>
                <CardTitle className="flex items-center gap-2">
                  <AlertCircle className="h-5 w-5 text-yellow-500" />
                  Comissões Pendentes
                </CardTitle>
              </CardHeader>
              <CardContent>
                {isLoadingPendentes ? (
                  <div className="text-center">Carregando...</div>
                ) : (
                  <div className="space-y-4">
                    <div className="flex items-center justify-between">
                      <span className="text-2xl font-bold">
                        {pendentes?.quantidadeComissoesPendentes || 0}
                      </span>
                      <Badge variant="secondary">
                        {formatCurrency(pendentes?.totalComissoesPendentes || 0)}
                      </Badge>
                    </div>
                    {(pendentes?.comissoesPendentes?.length || 0) > 0 && (
                      <div className="space-y-2">
                        {pendentes?.comissoesPendentes?.slice(0, 3).map((c) => (
                          <div
                            key={c.id}
                            className="flex items-center justify-between text-sm"
                          >
                            <span className="text-muted-foreground">
                              {format(new Date(c.dtReferencia), 'dd/MM/yyyy')}
                            </span>
                            <span>{formatCurrency(c.valor)}</span>
                          </div>
                        ))}
                      </div>
                    )}
                  </div>
                )}
              </CardContent>
            </Card>

            {/* Despesas Pendentes */}
            <Card>
              <CardHeader>
                <CardTitle className="flex items-center gap-2">
                  <AlertCircle className="h-5 w-5 text-yellow-500" />
                  Despesas Pendentes
                </CardTitle>
              </CardHeader>
              <CardContent>
                {isLoadingPendentes ? (
                  <div className="text-center">Carregando...</div>
                ) : (
                  <div className="space-y-4">
                    <div className="flex items-center justify-between">
                      <span className="text-2xl font-bold">
                        {pendentes?.quantidadeDespesasPendentes || 0}
                      </span>
                      <Badge variant="secondary">
                        {formatCurrency(pendentes?.totalDespesasPendentes || 0)}
                      </Badge>
                    </div>
                    {(pendentes?.despesasPendentes?.length || 0) > 0 && (
                      <div className="space-y-2">
                        {pendentes?.despesasPendentes?.slice(0, 3).map((d) => (
                          <div
                            key={d.id}
                            className="flex items-center justify-between text-sm"
                          >
                            <span className="text-muted-foreground">
                              {d.categoria} - {d.descricao || 'Sem descrição'}
                            </span>
                            <span>{formatCurrency(d.valor)}</span>
                          </div>
                        ))}
                      </div>
                    )}
                    <Link href="/dashboard/despesas-operacionais">
                      <Button variant="outline" className="w-full" size="sm">
                        Ver todas <ArrowRight className="ml-2 h-4 w-4" />
                      </Button>
                    </Link>
                  </div>
                )}
              </CardContent>
            </Card>

            {/* Dias sem Fechamento */}
            <Card>
              <CardHeader>
                <CardTitle className="flex items-center gap-2">
                  <CalendarIcon className="h-5 w-5 text-orange-500" />
                  Dias sem Fechamento
                </CardTitle>
              </CardHeader>
              <CardContent>
                {isLoadingPendentes ? (
                  <div className="text-center">Carregando...</div>
                ) : (
                  <div className="space-y-4">
                    <div className="text-2xl font-bold">
                      {pendentes?.quantidadeDiasSemFechamento || 0}
                      <span className="ml-2 text-sm font-normal text-muted-foreground">
                        nos últimos 30 dias
                      </span>
                    </div>
                    {(pendentes?.diasSemFechamento?.length || 0) > 0 && (
                      <div className="flex flex-wrap gap-1">
                        {pendentes?.diasSemFechamento?.slice(0, 10).map((d) => (
                          <Badge key={d} variant="outline" className="text-xs">
                            {format(new Date(d), 'dd/MM')}
                          </Badge>
                        ))}
                        {(pendentes?.diasSemFechamento?.length || 0) > 10 && (
                          <Badge variant="outline" className="text-xs">
                            +{(pendentes?.diasSemFechamento?.length || 0) - 10}
                          </Badge>
                        )}
                      </div>
                    )}
                    <Link href="/dashboard/fechamentos/diario">
                      <Button variant="outline" className="w-full" size="sm">
                        Ir para Fechamentos <ArrowRight className="ml-2 h-4 w-4" />
                      </Button>
                    </Link>
                  </div>
                )}
              </CardContent>
            </Card>

            {/* Fechamentos Abertos */}
            <Card>
              <CardHeader>
                <CardTitle className="flex items-center gap-2">
                  <AlertCircle className="h-5 w-5 text-blue-500" />
                  Fechamentos Abertos
                </CardTitle>
              </CardHeader>
              <CardContent>
                {isLoadingPendentes ? (
                  <div className="text-center">Carregando...</div>
                ) : (
                  <div className="space-y-4">
                    <div className="text-2xl font-bold">
                      {pendentes?.quantidadeFechamentosAbertos || 0}
                      <span className="ml-2 text-sm font-normal text-muted-foreground">
                        aguardando fechamento
                      </span>
                    </div>
                    {(pendentes?.fechamentosAbertos?.length || 0) > 0 && (
                      <div className="space-y-2">
                        {pendentes?.fechamentosAbertos?.slice(0, 5).map((f) => (
                          <div
                            key={f.id}
                            className="flex items-center justify-between text-sm"
                          >
                            <span className="text-muted-foreground">
                              {format(new Date(f.dtReferencia), 'dd/MM/yyyy')}
                            </span>
                            <span>{formatCurrency(f.totalFaturado)}</span>
                          </div>
                        ))}
                      </div>
                    )}
                    <Link href="/dashboard/fechamentos/diario">
                      <Button variant="outline" className="w-full" size="sm">
                        Ir para Fechamentos <ArrowRight className="ml-2 h-4 w-4" />
                      </Button>
                    </Link>
                  </div>
                )}
              </CardContent>
            </Card>
          </div>
        </TabsContent>
      </Tabs>
    </div>
  )
}
