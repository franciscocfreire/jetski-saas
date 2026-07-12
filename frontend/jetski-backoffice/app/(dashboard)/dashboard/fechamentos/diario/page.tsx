'use client'

import { useState, useMemo } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  Plus,
  MoreHorizontal,
  Lock,
  Unlock,
  CheckCircle,
  Calendar,
  FileText,
  Download,
  RefreshCw,
  Eye,
  AlertCircle,
  AlertTriangle,
} from 'lucide-react'
import { useTenantStore } from '@/lib/store/tenant-store'
import { fechamentosService } from '@/lib/api/services'
import type { FechamentoDiarioResponse, FechamentoStatus, DivergenciaResponse } from '@/lib/api/types'
import { formatCurrency, cn } from '@/lib/utils'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Badge } from '@/components/ui/badge'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from '@/components/ui/sheet'
import { Label } from '@/components/ui/label'
import { Skeleton } from '@/components/ui/skeleton'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Separator } from '@/components/ui/separator'
import { toast } from 'sonner'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { PresencaVendedoresCard } from '@/components/fechamento/presenca-vendedores-card'
import { PresencaVendedoresDialog } from '@/components/fechamento/presenca-vendedores-dialog'

const statusConfig: Record<FechamentoStatus, { label: string; variant: 'warning' | 'default' | 'success'; icon: React.ElementType }> = {
  aberto: { label: 'Aberto', variant: 'warning', icon: Unlock },
  fechado: { label: 'Fechado', variant: 'default', icon: Lock },
  aprovado: { label: 'Aprovado', variant: 'success', icon: CheckCircle },
}

function StatusBadge({ status }: { status: FechamentoStatus }) {
  const config = statusConfig[status]
  const Icon = config.icon
  return (
    <Badge variant={config.variant as 'default'} className={cn(
      'gap-1',
      status === 'aberto' && 'bg-yellow-100 text-yellow-800 hover:bg-yellow-100',
      status === 'fechado' && 'bg-blue-100 text-blue-800 hover:bg-blue-100',
      status === 'aprovado' && 'bg-green-100 text-green-800 hover:bg-green-100',
    )}>
      <Icon className="h-3 w-3" />
      {config.label}
    </Badge>
  )
}

function ConsolidarDialog({
  open,
  onOpenChange,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
}) {
  const queryClient = useQueryClient()
  const [data, setData] = useState(() => {
    const today = new Date()
    return today.toISOString().split('T')[0]
  })

  const consolidarMutation = useMutation({
    mutationFn: (dtReferencia: string) => fechamentosService.consolidarDia(dtReferencia),
    onSuccess: (response) => {
      queryClient.invalidateQueries({ queryKey: ['fechamentos-diarios'] })
      toast.success(`Dia ${formatDate(response.dtReferencia)} consolidado com sucesso!`)
      onOpenChange(false)
    },
    onError: (error: Error) => {
      toast.error(`Erro ao consolidar: ${error.message}`)
    },
  })

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    consolidarMutation.mutate(data)
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Consolidar Dia</DialogTitle>
          <DialogDescription>
            Selecione a data para consolidar as locações do dia.
            Você pode reconsolidar um dia que ainda não esteja bloqueado.
          </DialogDescription>
        </DialogHeader>
        <form onSubmit={handleSubmit}>
          <div className="grid gap-4 py-4">
            <div className="grid gap-2">
              <Label htmlFor="data">Data de Referência</Label>
              <Input
                id="data"
                type="date"
                value={data}
                onChange={(e) => setData(e.target.value)}
                max={new Date().toISOString().split('T')[0]}
                required
              />
            </div>
          </div>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
              Cancelar
            </Button>
            <Button type="submit" disabled={consolidarMutation.isPending}>
              {consolidarMutation.isPending ? (
                <>
                  <RefreshCw className="mr-2 h-4 w-4 animate-spin" />
                  Consolidando...
                </>
              ) : (
                <>
                  <Calendar className="mr-2 h-4 w-4" />
                  Consolidar
                </>
              )}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

function DetalhesSheet({
  fechamento,
  open,
  onOpenChange,
}: {
  fechamento: FechamentoDiarioResponse | null
  open: boolean
  onOpenChange: (open: boolean) => void
}) {
  const [presencaDialogOpen, setPresencaDialogOpen] = useState(false)

  if (!fechamento) return null

  const isReadOnly = fechamento.status !== 'aberto' || fechamento.bloqueado

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent className="w-full sm:w-[540px] sm:max-w-[540px] overflow-y-auto">
        <SheetHeader>
          <SheetTitle className="flex items-center gap-2">
            <Calendar className="h-5 w-5" />
            Fechamento {formatDate(fechamento.dtReferencia)}
          </SheetTitle>
          <SheetDescription>
            <StatusBadge status={fechamento.status} />
          </SheetDescription>
        </SheetHeader>

        <div className="mt-6 space-y-6">
          {/* Resumo Financeiro */}
          <Card>
            <CardHeader className="pb-2">
              <CardTitle className="text-sm font-medium text-muted-foreground">
                Resumo Financeiro
              </CardTitle>
            </CardHeader>
            <CardContent className="space-y-3">
              <div className="flex justify-between">
                <span className="text-muted-foreground">Total Locações</span>
                <span className="font-medium">{fechamento.totalLocacoes}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-muted-foreground">Total Faturado</span>
                <span className="font-medium text-green-600">
                  {formatCurrency(fechamento.totalFaturado)}
                </span>
              </div>
              <Separator />
              <div className="flex justify-between">
                <span className="text-muted-foreground">Combustível</span>
                <span className="font-medium text-warning">
                  {formatCurrency(fechamento.totalCombustivel)}
                </span>
              </div>
              <div className="flex justify-between">
                <span className="text-muted-foreground">Comissões</span>
                <span className="font-medium text-blue-600">
                  {formatCurrency(fechamento.totalComissoes)}
                </span>
              </div>
              <div className="flex justify-between">
                <span className="text-muted-foreground">Diárias Vendedores</span>
                <span className="font-medium text-purple-600">
                  {formatCurrency(fechamento.totalDiariasVendedores || 0)}
                </span>
              </div>
            </CardContent>
          </Card>

          {/* Formas de Pagamento */}
          <Card>
            <CardHeader className="pb-2">
              <CardTitle className="text-sm font-medium text-muted-foreground">
                Por Forma de Pagamento
              </CardTitle>
            </CardHeader>
            <CardContent className="space-y-3">
              <div className="flex justify-between">
                <span className="text-muted-foreground">Dinheiro</span>
                <span className="font-medium">{formatCurrency(fechamento.totalDinheiro)}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-muted-foreground">Cartão</span>
                <span className="font-medium">{formatCurrency(fechamento.totalCartao)}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-muted-foreground">PIX</span>
                <span className="font-medium">{formatCurrency(fechamento.totalPix)}</span>
              </div>
            </CardContent>
          </Card>

          {/* Diárias de Vendedores */}
          <PresencaVendedoresCard
            dtReferencia={fechamento.dtReferencia}
            onRegistrarClick={() => setPresencaDialogOpen(true)}
            readOnly={isReadOnly}
          />

          {/* Status */}
          <Card>
            <CardHeader className="pb-2">
              <CardTitle className="text-sm font-medium text-muted-foreground">
                Status do Fechamento
              </CardTitle>
            </CardHeader>
            <CardContent className="space-y-3">
              <div className="flex justify-between items-center">
                <span className="text-muted-foreground">Status</span>
                <StatusBadge status={fechamento.status} />
              </div>
              <div className="flex justify-between items-center">
                <span className="text-muted-foreground">Bloqueado</span>
                <Badge variant={fechamento.bloqueado ? 'destructive' : 'outline'}>
                  {fechamento.bloqueado ? 'Sim' : 'Não'}
                </Badge>
              </div>
              {fechamento.dtFechamento && (
                <div className="flex justify-between">
                  <span className="text-muted-foreground">Fechado em</span>
                  <span className="font-medium text-sm">
                    {new Date(fechamento.dtFechamento).toLocaleString('pt-BR')}
                  </span>
                </div>
              )}
            </CardContent>
          </Card>

          {/* Observações */}
          {fechamento.observacoes && (
            <Card>
              <CardHeader className="pb-2">
                <CardTitle className="text-sm font-medium text-muted-foreground">
                  Observações
                </CardTitle>
              </CardHeader>
              <CardContent>
                <p className="text-sm">{fechamento.observacoes}</p>
              </CardContent>
            </Card>
          )}

          {/* Divergências */}
          {fechamento.divergenciasJson && (
            <Card className="border-yellow-200 bg-yellow-50">
              <CardHeader className="pb-2">
                <CardTitle className="text-sm font-medium text-yellow-800 flex items-center gap-2">
                  <AlertCircle className="h-4 w-4" />
                  Divergências Encontradas
                </CardTitle>
              </CardHeader>
              <CardContent>
                <pre className="text-xs text-yellow-700 whitespace-pre-wrap">
                  {fechamento.divergenciasJson}
                </pre>
              </CardContent>
            </Card>
          )}

          {/* Botões de Exportação */}
          <div className="flex gap-2">
            <Button
              variant="outline"
              className="flex-1"
              onClick={() => {
                fechamentosService.downloadRelatorioDiario(fechamento.id, 'pdf')
                  .then(blob => {
                    const url = window.URL.createObjectURL(blob)
                    const a = document.createElement('a')
                    a.href = url
                    a.download = `fechamento_diario_${fechamento.dtReferencia}.pdf`
                    a.click()
                    window.URL.revokeObjectURL(url)
                  })
                  .catch(err => toast.error('Erro ao gerar PDF'))
              }}
            >
              <FileText className="mr-2 h-4 w-4" />
              Exportar PDF
            </Button>
            <Button
              variant="outline"
              className="flex-1"
              onClick={() => {
                fechamentosService.downloadRelatorioDiario(fechamento.id, 'excel')
                  .then(blob => {
                    const url = window.URL.createObjectURL(blob)
                    const a = document.createElement('a')
                    a.href = url
                    a.download = `fechamento_diario_${fechamento.dtReferencia}.xlsx`
                    a.click()
                    window.URL.revokeObjectURL(url)
                  })
                  .catch(err => toast.error('Erro ao gerar Excel'))
              }}
            >
              <Download className="mr-2 h-4 w-4" />
              Exportar Excel
            </Button>
          </div>
        </div>
      </SheetContent>

      {/* Dialog de Presenças */}
      <PresencaVendedoresDialog
        dtReferencia={fechamento.dtReferencia}
        open={presencaDialogOpen}
        onOpenChange={setPresencaDialogOpen}
      />
    </Sheet>
  )
}

function formatDate(dateStr: string): string {
  const date = new Date(dateStr + 'T00:00:00')
  return date.toLocaleDateString('pt-BR')
}

function getDefaultDateRange() {
  const hoje = new Date()
  const inicioMes = new Date(hoje.getFullYear(), hoje.getMonth(), 1)
  return {
    dataInicio: inicioMes.toISOString().split('T')[0],
    dataFim: hoje.toISOString().split('T')[0],
  }
}

export default function FechamentoDiarioPage() {
  const { currentTenant } = useTenantStore()
  const queryClient = useQueryClient()

  const [dateRange, setDateRange] = useState(getDefaultDateRange)
  const [consolidarOpen, setConsolidarOpen] = useState(false)
  const [selectedFechamento, setSelectedFechamento] = useState<FechamentoDiarioResponse | null>(null)
  const [detalhesOpen, setDetalhesOpen] = useState(false)

  const { data: fechamentos, isLoading, error } = useQuery({
    queryKey: ['fechamentos-diarios', currentTenant?.id, dateRange],
    queryFn: () => fechamentosService.listarDiarios(dateRange.dataInicio, dateRange.dataFim),
    enabled: !!currentTenant,
  })

  // Query para verificar divergencias (roda automaticamente quando ha fechamentos)
  const { data: divergencias, refetch: refetchDivergencias, isLoading: isCheckingDivergencias } = useQuery({
    queryKey: ['fechamentos-divergencias', currentTenant?.id, dateRange],
    queryFn: () => fechamentosService.verificarDivergencias(dateRange.dataInicio, dateRange.dataFim),
    enabled: !!currentTenant && !!fechamentos?.length,
    staleTime: 30000, // Cache por 30s para evitar queries excessivas
  })

  // Helper para verificar se um fechamento tem divergencia
  const hasDivergencia = (fechamentoId: string) => {
    return divergencias?.some(d => d.fechamentoId === fechamentoId)
  }

  // Obter divergencia de um fechamento especifico
  const getDivergencia = (fechamentoId: string) => {
    return divergencias?.find(d => d.fechamentoId === fechamentoId)
  }

  // Mutations
  const fecharMutation = useMutation({
    mutationFn: (id: string) => fechamentosService.fecharDia(id),
    onSuccess: (response) => {
      queryClient.invalidateQueries({ queryKey: ['fechamentos-diarios'] })
      toast.success(`Dia ${formatDate(response.dtReferencia)} fechado e bloqueado!`)
    },
    onError: (error: Error) => {
      toast.error(`Erro ao fechar: ${error.message}`)
    },
  })

  const aprovarMutation = useMutation({
    mutationFn: (id: string) => fechamentosService.aprovarDia(id),
    onSuccess: (response) => {
      queryClient.invalidateQueries({ queryKey: ['fechamentos-diarios'] })
      toast.success(`Dia ${formatDate(response.dtReferencia)} aprovado!`)
    },
    onError: (error: Error) => {
      toast.error(`Erro ao aprovar: ${error.message}`)
    },
  })

  const reabrirMutation = useMutation({
    mutationFn: (id: string) => fechamentosService.reabrirDia(id),
    onSuccess: (response) => {
      queryClient.invalidateQueries({ queryKey: ['fechamentos-diarios'] })
      toast.success(`Dia ${formatDate(response.dtReferencia)} reaberto!`)
    },
    onError: (error: Error) => {
      toast.error(`Erro ao reabrir: ${error.message}`)
    },
  })

  const handleViewDetails = (fechamento: FechamentoDiarioResponse) => {
    setSelectedFechamento(fechamento)
    setDetalhesOpen(true)
  }

  const handleFechar = (fechamento: FechamentoDiarioResponse) => {
    if (confirm(`Confirma fechar e bloquear o dia ${formatDate(fechamento.dtReferencia)}?\n\nIsso impedirá edições retroativas nas locações deste dia.`)) {
      fecharMutation.mutate(fechamento.id)
    }
  }

  const handleAprovar = (fechamento: FechamentoDiarioResponse) => {
    if (confirm(`Confirma aprovar o fechamento do dia ${formatDate(fechamento.dtReferencia)}?`)) {
      aprovarMutation.mutate(fechamento.id)
    }
  }

  const handleReabrir = (fechamento: FechamentoDiarioResponse) => {
    if (confirm(`Confirma reabrir o dia ${formatDate(fechamento.dtReferencia)}?\n\nIsso permitirá edições e reconsolidação.`)) {
      reabrirMutation.mutate(fechamento.id)
    }
  }

  // Totais do período
  const totais = useMemo(() => {
    if (!fechamentos?.length) return null
    return {
      locacoes: fechamentos.reduce((sum, f) => sum + f.totalLocacoes, 0),
      faturado: fechamentos.reduce((sum, f) => sum + f.totalFaturado, 0),
      combustivel: fechamentos.reduce((sum, f) => sum + f.totalCombustivel, 0),
      comissoes: fechamentos.reduce((sum, f) => sum + f.totalComissoes, 0),
      diarias: fechamentos.reduce((sum, f) => sum + (f.totalDiariasVendedores || 0), 0),
    }
  }, [fechamentos])

  if (!currentTenant) {
    return (
      <div className="flex items-center justify-center h-[60vh]">
        <p className="text-muted-foreground">Selecione um tenant para continuar</p>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">Fechamento Diário</h1>
          <p className="text-muted-foreground">
            Gerencie os fechamentos diários de caixa
          </p>
        </div>
        <Button onClick={() => setConsolidarOpen(true)}>
          <Plus className="mr-2 h-4 w-4" />
          Consolidar Dia
        </Button>
      </div>

      {/* Filtros de Data */}
      <Card>
        <CardContent className="pt-6">
          <div className="flex flex-wrap gap-4 items-end">
            <div className="grid gap-2">
              <Label htmlFor="dataInicio">Data Início</Label>
              <Input
                id="dataInicio"
                type="date"
                value={dateRange.dataInicio}
                onChange={(e) => setDateRange(prev => ({ ...prev, dataInicio: e.target.value }))}
              />
            </div>
            <div className="grid gap-2">
              <Label htmlFor="dataFim">Data Fim</Label>
              <Input
                id="dataFim"
                type="date"
                value={dateRange.dataFim}
                onChange={(e) => setDateRange(prev => ({ ...prev, dataFim: e.target.value }))}
              />
            </div>
            <Button
              variant="outline"
              onClick={() => setDateRange(getDefaultDateRange())}
            >
              Mes Atual
            </Button>
            <Button
              variant="outline"
              size="default"
              onClick={() => refetchDivergencias()}
              disabled={isCheckingDivergencias || !fechamentos?.length}
            >
              {isCheckingDivergencias ? (
                <RefreshCw className="mr-2 h-4 w-4 animate-spin" />
              ) : (
                <RefreshCw className="mr-2 h-4 w-4" />
              )}
              Verificar Divergencias
            </Button>
          </div>
        </CardContent>
      </Card>

      {/* Alerta de Divergencias */}
      {divergencias && divergencias.length > 0 && (
        <Alert variant="default" className="border-yellow-500 bg-yellow-50">
          <AlertTriangle className="h-4 w-4 text-yellow-600" />
          <AlertTitle className="text-yellow-800">Divergencias Detectadas</AlertTitle>
          <AlertDescription className="text-yellow-700">
            <div className="mt-2">
              <span className="font-medium">
                {divergencias.length} fechamento(s) com valores desatualizados.
              </span>
              <ul className="mt-2 space-y-1 text-sm">
                {divergencias.slice(0, 3).map(d => (
                  <li key={d.fechamentoId} className="flex items-center gap-2">
                    <span className="font-medium">{formatDate(d.dtReferencia)}:</span>
                    <span>diferenca de {formatCurrency(d.diferencaFaturado)}</span>
                    {d.locacoesAlteradas && d.locacoesAlteradas.length > 0 && (
                      <span className="text-yellow-600">
                        ({d.locacoesAlteradas.length} locacao(oes) alterada(s))
                      </span>
                    )}
                  </li>
                ))}
                {divergencias.length > 3 && (
                  <li className="text-yellow-600 font-medium">
                    ... e mais {divergencias.length - 3} fechamento(s)
                  </li>
                )}
              </ul>
              <p className="mt-3 text-xs">
                Reconsolide os dias afetados para atualizar os valores.
              </p>
            </div>
          </AlertDescription>
        </Alert>
      )}

      {/* Totais do Período */}
      {totais && (
        <div className="grid gap-4 md:grid-cols-5">
          <Card>
            <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
              <CardTitle className="text-sm font-medium">Total Locações</CardTitle>
            </CardHeader>
            <CardContent>
              <div className="text-2xl font-bold">{totais.locacoes}</div>
            </CardContent>
          </Card>
          <Card>
            <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
              <CardTitle className="text-sm font-medium">Total Faturado</CardTitle>
            </CardHeader>
            <CardContent>
              <div className="text-2xl font-bold text-green-600">
                {formatCurrency(totais.faturado)}
              </div>
            </CardContent>
          </Card>
          <Card>
            <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
              <CardTitle className="text-sm font-medium">Combustível</CardTitle>
            </CardHeader>
            <CardContent>
              <div className="text-2xl font-bold text-warning">
                {formatCurrency(totais.combustivel)}
              </div>
            </CardContent>
          </Card>
          <Card>
            <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
              <CardTitle className="text-sm font-medium">Comissões</CardTitle>
            </CardHeader>
            <CardContent>
              <div className="text-2xl font-bold text-blue-600">
                {formatCurrency(totais.comissoes)}
              </div>
            </CardContent>
          </Card>
          <Card>
            <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
              <CardTitle className="text-sm font-medium">Diárias Vendedores</CardTitle>
            </CardHeader>
            <CardContent>
              <div className="text-2xl font-bold text-purple-600">
                {formatCurrency(totais.diarias)}
              </div>
            </CardContent>
          </Card>
        </div>
      )}

      {/* Tabela */}
      <Card>
        <CardContent className="pt-6">
          {isLoading ? (
            <div className="space-y-3">
              {[...Array(5)].map((_, i) => (
                <Skeleton key={i} className="h-12 w-full" />
              ))}
            </div>
          ) : error ? (
            <div className="flex items-center justify-center py-10 text-destructive">
              <AlertCircle className="mr-2 h-5 w-5" />
              Erro ao carregar fechamentos
            </div>
          ) : !fechamentos?.length ? (
            <div className="flex flex-col items-center justify-center py-10 text-muted-foreground">
              <Calendar className="h-12 w-12 mb-4" />
              <p>Nenhum fechamento encontrado no período</p>
              <Button
                variant="link"
                className="mt-2"
                onClick={() => setConsolidarOpen(true)}
              >
                Consolidar primeiro dia
              </Button>
            </div>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Data</TableHead>
                  <TableHead className="text-right">Locações</TableHead>
                  <TableHead className="text-right">Faturado</TableHead>
                  <TableHead className="text-right">Combustível</TableHead>
                  <TableHead className="text-right">Comissões</TableHead>
                  <TableHead className="text-right">Diárias</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead className="w-[70px]"></TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {fechamentos.map((fechamento) => (
                  <TableRow
                    key={fechamento.id}
                    className={cn(
                      hasDivergencia(fechamento.id) && "bg-yellow-50 border-l-4 border-l-yellow-500"
                    )}
                  >
                    <TableCell className="font-medium">
                      <div className="flex items-center gap-2">
                        {formatDate(fechamento.dtReferencia)}
                        {hasDivergencia(fechamento.id) && (
                          <Badge variant="outline" className="bg-yellow-100 text-yellow-800 border-yellow-500 text-xs">
                            <AlertTriangle className="h-3 w-3 mr-1" />
                            Desatualizado
                          </Badge>
                        )}
                      </div>
                    </TableCell>
                    <TableCell className="text-right">
                      {fechamento.totalLocacoes}
                    </TableCell>
                    <TableCell className="text-right text-green-600">
                      {formatCurrency(fechamento.totalFaturado)}
                    </TableCell>
                    <TableCell className="text-right text-warning">
                      {formatCurrency(fechamento.totalCombustivel)}
                    </TableCell>
                    <TableCell className="text-right text-blue-600">
                      {formatCurrency(fechamento.totalComissoes)}
                    </TableCell>
                    <TableCell className="text-right text-purple-600">
                      {formatCurrency(fechamento.totalDiariasVendedores || 0)}
                    </TableCell>
                    <TableCell>
                      <StatusBadge status={fechamento.status} />
                    </TableCell>
                    <TableCell>
                      <DropdownMenu>
                        <DropdownMenuTrigger asChild>
                          <Button variant="ghost" size="icon">
                            <MoreHorizontal className="h-4 w-4" />
                          </Button>
                        </DropdownMenuTrigger>
                        <DropdownMenuContent align="end">
                          <DropdownMenuItem onClick={() => handleViewDetails(fechamento)}>
                            <Eye className="mr-2 h-4 w-4" />
                            Ver Detalhes
                          </DropdownMenuItem>
                          <DropdownMenuSeparator />
                          {fechamento.status === 'aberto' && (
                            <>
                              <DropdownMenuItem
                                onClick={() => fechamentosService.consolidarDia(fechamento.dtReferencia).then(() => {
                                  queryClient.invalidateQueries({ queryKey: ['fechamentos-diarios'] })
                                  toast.success('Reconsolidado com sucesso!')
                                })}
                              >
                                <RefreshCw className="mr-2 h-4 w-4" />
                                Reconsolidar
                              </DropdownMenuItem>
                              <DropdownMenuItem onClick={() => handleFechar(fechamento)}>
                                <Lock className="mr-2 h-4 w-4" />
                                Fechar e Bloquear
                              </DropdownMenuItem>
                            </>
                          )}
                          {fechamento.status === 'fechado' && (
                            <>
                              <DropdownMenuItem onClick={() => handleAprovar(fechamento)}>
                                <CheckCircle className="mr-2 h-4 w-4" />
                                Aprovar
                              </DropdownMenuItem>
                              <DropdownMenuItem onClick={() => handleReabrir(fechamento)}>
                                <Unlock className="mr-2 h-4 w-4" />
                                Reabrir
                              </DropdownMenuItem>
                            </>
                          )}
                          {fechamento.status === 'aprovado' && (
                            <DropdownMenuItem
                              onClick={() => handleReabrir(fechamento)}
                              className="text-destructive"
                            >
                              <Unlock className="mr-2 h-4 w-4" />
                              Forçar Reabertura
                            </DropdownMenuItem>
                          )}
                        </DropdownMenuContent>
                      </DropdownMenu>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>

      {/* Dialogs */}
      <ConsolidarDialog open={consolidarOpen} onOpenChange={setConsolidarOpen} />
      <DetalhesSheet
        fechamento={selectedFechamento}
        open={detalhesOpen}
        onOpenChange={setDetalhesOpen}
      />
    </div>
  )
}
