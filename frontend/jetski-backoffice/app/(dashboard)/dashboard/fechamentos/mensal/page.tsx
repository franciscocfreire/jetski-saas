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
  TrendingUp,
  TrendingDown,
} from 'lucide-react'
import { useTenantStore } from '@/lib/store/tenant-store'
import { fechamentosService } from '@/lib/api/services'
import type { FechamentoMensalResponse, FechamentoStatus } from '@/lib/api/types'
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
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Label } from '@/components/ui/label'
import { Skeleton } from '@/components/ui/skeleton'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Separator } from '@/components/ui/separator'
import { toast } from 'sonner'

const statusConfig: Record<FechamentoStatus, { label: string; variant: 'warning' | 'default' | 'success'; icon: React.ElementType }> = {
  aberto: { label: 'Aberto', variant: 'warning', icon: Unlock },
  fechado: { label: 'Fechado', variant: 'default', icon: Lock },
  aprovado: { label: 'Aprovado', variant: 'success', icon: CheckCircle },
}

const meses = [
  'Janeiro', 'Fevereiro', 'Março', 'Abril', 'Maio', 'Junho',
  'Julho', 'Agosto', 'Setembro', 'Outubro', 'Novembro', 'Dezembro'
]

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

function ConsolidarMesDialog({
  open,
  onOpenChange,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
}) {
  const queryClient = useQueryClient()
  const hoje = new Date()
  const [ano, setAno] = useState(hoje.getFullYear())
  const [mes, setMes] = useState(hoje.getMonth() + 1)

  const consolidarMutation = useMutation({
    mutationFn: ({ ano, mes }: { ano: number; mes: number }) =>
      fechamentosService.consolidarMes(ano, mes),
    onSuccess: (response) => {
      queryClient.invalidateQueries({ queryKey: ['fechamentos-mensais'] })
      toast.success(`${meses[response.mes - 1]}/${response.ano} consolidado com sucesso!`)
      onOpenChange(false)
    },
    onError: (error: Error) => {
      toast.error(`Erro ao consolidar: ${error.message}`)
    },
  })

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    consolidarMutation.mutate({ ano, mes })
  }

  const anos = Array.from({ length: 5 }, (_, i) => hoje.getFullYear() - i)

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Consolidar Mês</DialogTitle>
          <DialogDescription>
            Selecione o mês e ano para consolidar os fechamentos diários.
            Todos os dias do mês devem estar fechados.
          </DialogDescription>
        </DialogHeader>
        <form onSubmit={handleSubmit}>
          <div className="grid gap-4 py-4">
            <div className="grid grid-cols-2 gap-4">
              <div className="grid gap-2">
                <Label htmlFor="mes">Mês</Label>
                <Select value={mes.toString()} onValueChange={(v) => setMes(parseInt(v))}>
                  <SelectTrigger>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {meses.map((nome, index) => (
                      <SelectItem key={index} value={(index + 1).toString()}>
                        {nome}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <div className="grid gap-2">
                <Label htmlFor="ano">Ano</Label>
                <Select value={ano.toString()} onValueChange={(v) => setAno(parseInt(v))}>
                  <SelectTrigger>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {anos.map((a) => (
                      <SelectItem key={a} value={a.toString()}>
                        {a}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
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
  fechamento: FechamentoMensalResponse | null
  open: boolean
  onOpenChange: (open: boolean) => void
}) {
  if (!fechamento) return null

  const isPositivo = fechamento.resultadoLiquido >= 0

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent className="w-[400px] sm:w-[540px] overflow-y-auto">
        <SheetHeader>
          <SheetTitle className="flex items-center gap-2">
            <Calendar className="h-5 w-5" />
            {meses[fechamento.mes - 1]} / {fechamento.ano}
          </SheetTitle>
          <SheetDescription>
            <StatusBadge status={fechamento.status} />
          </SheetDescription>
        </SheetHeader>

        <div className="mt-6 space-y-6">
          {/* Resultado Líquido em Destaque */}
          <Card className={cn(
            'border-2',
            isPositivo ? 'border-green-200 bg-green-50' : 'border-red-200 bg-red-50'
          )}>
            <CardHeader className="pb-2">
              <CardTitle className="text-sm font-medium flex items-center gap-2">
                {isPositivo ? (
                  <TrendingUp className="h-4 w-4 text-green-600" />
                ) : (
                  <TrendingDown className="h-4 w-4 text-red-600" />
                )}
                Resultado Líquido
              </CardTitle>
            </CardHeader>
            <CardContent>
              <div className={cn(
                'text-3xl font-bold',
                isPositivo ? 'text-green-600' : 'text-red-600'
              )}>
                {formatCurrency(fechamento.resultadoLiquido)}
              </div>
            </CardContent>
          </Card>

          {/* Resumo Financeiro */}
          <Card>
            <CardHeader className="pb-2">
              <CardTitle className="text-sm font-medium text-muted-foreground">
                Resumo do Mês
              </CardTitle>
            </CardHeader>
            <CardContent className="space-y-3">
              <div className="flex justify-between">
                <span className="text-muted-foreground">Total Locações</span>
                <span className="font-medium">{fechamento.totalLocacoes}</span>
              </div>
              <Separator />
              <div className="flex justify-between">
                <span className="text-muted-foreground">Total Faturado</span>
                <span className="font-medium text-green-600">
                  {formatCurrency(fechamento.totalFaturado)}
                </span>
              </div>
              <div className="flex justify-between">
                <span className="text-muted-foreground">(-) Custos Operacionais</span>
                <span className="font-medium text-warning">
                  {formatCurrency(fechamento.totalCustos)}
                </span>
              </div>
              <div className="flex justify-between">
                <span className="text-muted-foreground">(-) Comissões</span>
                <span className="font-medium text-blue-600">
                  {formatCurrency(fechamento.totalComissoes)}
                </span>
              </div>
              <div className="flex justify-between">
                <span className="text-muted-foreground">(-) Manutenções</span>
                <span className="font-medium text-purple-600">
                  {formatCurrency(fechamento.totalManutencoes)}
                </span>
              </div>
              <Separator />
              <div className="flex justify-between font-medium">
                <span>= Resultado Líquido</span>
                <span className={isPositivo ? 'text-green-600' : 'text-red-600'}>
                  {formatCurrency(fechamento.resultadoLiquido)}
                </span>
              </div>
            </CardContent>
          </Card>

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

          {/* Botões de Exportação */}
          <div className="flex gap-2">
            <Button
              variant="outline"
              className="flex-1"
              onClick={() => {
                fechamentosService.downloadRelatorioMensal(fechamento.id, 'pdf')
                  .then(blob => {
                    const url = window.URL.createObjectURL(blob)
                    const a = document.createElement('a')
                    a.href = url
                    a.download = `fechamento_mensal_${fechamento.ano}_${String(fechamento.mes).padStart(2, '0')}.pdf`
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
                fechamentosService.downloadRelatorioMensal(fechamento.id, 'excel')
                  .then(blob => {
                    const url = window.URL.createObjectURL(blob)
                    const a = document.createElement('a')
                    a.href = url
                    a.download = `fechamento_mensal_${fechamento.ano}_${String(fechamento.mes).padStart(2, '0')}.xlsx`
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
    </Sheet>
  )
}

export default function FechamentoMensalPage() {
  const { currentTenant } = useTenantStore()
  const queryClient = useQueryClient()
  const hoje = new Date()

  const [anoFiltro, setAnoFiltro] = useState(hoje.getFullYear())
  const [consolidarOpen, setConsolidarOpen] = useState(false)
  const [selectedFechamento, setSelectedFechamento] = useState<FechamentoMensalResponse | null>(null)
  const [detalhesOpen, setDetalhesOpen] = useState(false)

  const { data: fechamentos, isLoading, error } = useQuery({
    queryKey: ['fechamentos-mensais', currentTenant?.id, anoFiltro],
    queryFn: () => fechamentosService.listarMensaisPorAno(anoFiltro),
    enabled: !!currentTenant,
  })

  // Mutations
  const fecharMutation = useMutation({
    mutationFn: (id: string) => fechamentosService.fecharMes(id),
    onSuccess: (response) => {
      queryClient.invalidateQueries({ queryKey: ['fechamentos-mensais'] })
      toast.success(`${meses[response.mes - 1]}/${response.ano} fechado!`)
    },
    onError: (error: Error) => {
      toast.error(`Erro ao fechar: ${error.message}`)
    },
  })

  const aprovarMutation = useMutation({
    mutationFn: (id: string) => fechamentosService.aprovarMes(id),
    onSuccess: (response) => {
      queryClient.invalidateQueries({ queryKey: ['fechamentos-mensais'] })
      toast.success(`${meses[response.mes - 1]}/${response.ano} aprovado!`)
    },
    onError: (error: Error) => {
      toast.error(`Erro ao aprovar: ${error.message}`)
    },
  })

  const reabrirMutation = useMutation({
    mutationFn: (id: string) => fechamentosService.reabrirMes(id),
    onSuccess: (response) => {
      queryClient.invalidateQueries({ queryKey: ['fechamentos-mensais'] })
      toast.success(`${meses[response.mes - 1]}/${response.ano} reaberto!`)
    },
    onError: (error: Error) => {
      toast.error(`Erro ao reabrir: ${error.message}`)
    },
  })

  const handleViewDetails = (fechamento: FechamentoMensalResponse) => {
    setSelectedFechamento(fechamento)
    setDetalhesOpen(true)
  }

  const handleFechar = (fechamento: FechamentoMensalResponse) => {
    if (confirm(`Confirma fechar ${meses[fechamento.mes - 1]}/${fechamento.ano}?`)) {
      fecharMutation.mutate(fechamento.id)
    }
  }

  const handleAprovar = (fechamento: FechamentoMensalResponse) => {
    if (confirm(`Confirma aprovar o fechamento de ${meses[fechamento.mes - 1]}/${fechamento.ano}?`)) {
      aprovarMutation.mutate(fechamento.id)
    }
  }

  const handleReabrir = (fechamento: FechamentoMensalResponse) => {
    if (confirm(`Confirma reabrir ${meses[fechamento.mes - 1]}/${fechamento.ano}?`)) {
      reabrirMutation.mutate(fechamento.id)
    }
  }

  // Totais do ano
  const totais = useMemo(() => {
    if (!fechamentos?.length) return null
    return {
      locacoes: fechamentos.reduce((sum, f) => sum + f.totalLocacoes, 0),
      faturado: fechamentos.reduce((sum, f) => sum + f.totalFaturado, 0),
      resultado: fechamentos.reduce((sum, f) => sum + f.resultadoLiquido, 0),
    }
  }, [fechamentos])

  const anos = Array.from({ length: 5 }, (_, i) => hoje.getFullYear() - i)

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
          <h1 className="text-2xl font-bold tracking-tight">Fechamento Mensal</h1>
          <p className="text-muted-foreground">
            Gerencie os fechamentos mensais consolidados
          </p>
        </div>
        <Button onClick={() => setConsolidarOpen(true)}>
          <Plus className="mr-2 h-4 w-4" />
          Consolidar Mês
        </Button>
      </div>

      {/* Filtro de Ano */}
      <Card>
        <CardContent className="pt-6">
          <div className="flex gap-4 items-end">
            <div className="grid gap-2">
              <Label htmlFor="ano">Ano</Label>
              <Select value={anoFiltro.toString()} onValueChange={(v) => setAnoFiltro(parseInt(v))}>
                <SelectTrigger className="w-[180px]">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {anos.map((a) => (
                    <SelectItem key={a} value={a.toString()}>
                      {a}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          </div>
        </CardContent>
      </Card>

      {/* Totais do Ano */}
      {totais && (
        <div className="grid gap-4 md:grid-cols-3">
          <Card>
            <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
              <CardTitle className="text-sm font-medium">Total Locações</CardTitle>
            </CardHeader>
            <CardContent>
              <div className="text-2xl font-bold">{totais.locacoes}</div>
              <p className="text-xs text-muted-foreground">no ano de {anoFiltro}</p>
            </CardContent>
          </Card>
          <Card>
            <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
              <CardTitle className="text-sm font-medium">Faturamento Total</CardTitle>
            </CardHeader>
            <CardContent>
              <div className="text-2xl font-bold text-green-600">
                {formatCurrency(totais.faturado)}
              </div>
              <p className="text-xs text-muted-foreground">no ano de {anoFiltro}</p>
            </CardContent>
          </Card>
          <Card>
            <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
              <CardTitle className="text-sm font-medium">Resultado Líquido</CardTitle>
            </CardHeader>
            <CardContent>
              <div className={cn(
                'text-2xl font-bold',
                totais.resultado >= 0 ? 'text-green-600' : 'text-red-600'
              )}>
                {formatCurrency(totais.resultado)}
              </div>
              <p className="text-xs text-muted-foreground">no ano de {anoFiltro}</p>
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
              <p>Nenhum fechamento mensal encontrado para {anoFiltro}</p>
              <Button
                variant="link"
                className="mt-2"
                onClick={() => setConsolidarOpen(true)}
              >
                Consolidar primeiro mês
              </Button>
            </div>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Período</TableHead>
                  <TableHead className="text-right">Locações</TableHead>
                  <TableHead className="text-right">Faturado</TableHead>
                  <TableHead className="text-right">Custos</TableHead>
                  <TableHead className="text-right">Resultado</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead className="w-[70px]"></TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {fechamentos.map((fechamento) => {
                  const isPositivo = fechamento.resultadoLiquido >= 0
                  return (
                    <TableRow key={fechamento.id}>
                      <TableCell className="font-medium">
                        {meses[fechamento.mes - 1]} / {fechamento.ano}
                      </TableCell>
                      <TableCell className="text-right">
                        {fechamento.totalLocacoes}
                      </TableCell>
                      <TableCell className="text-right text-green-600">
                        {formatCurrency(fechamento.totalFaturado)}
                      </TableCell>
                      <TableCell className="text-right text-warning">
                        {formatCurrency(fechamento.totalCustos + fechamento.totalComissoes + fechamento.totalManutencoes)}
                      </TableCell>
                      <TableCell className={cn(
                        'text-right font-medium',
                        isPositivo ? 'text-green-600' : 'text-red-600'
                      )}>
                        {formatCurrency(fechamento.resultadoLiquido)}
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
                                  onClick={() => fechamentosService.consolidarMes(fechamento.ano, fechamento.mes).then(() => {
                                    queryClient.invalidateQueries({ queryKey: ['fechamentos-mensais'] })
                                    toast.success('Reconsolidado com sucesso!')
                                  })}
                                >
                                  <RefreshCw className="mr-2 h-4 w-4" />
                                  Reconsolidar
                                </DropdownMenuItem>
                                <DropdownMenuItem onClick={() => handleFechar(fechamento)}>
                                  <Lock className="mr-2 h-4 w-4" />
                                  Fechar
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
                  )
                })}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>

      {/* Dialogs */}
      <ConsolidarMesDialog open={consolidarOpen} onOpenChange={setConsolidarOpen} />
      <DetalhesSheet
        fechamento={selectedFechamento}
        open={detalhesOpen}
        onOpenChange={setDetalhesOpen}
      />
    </div>
  )
}
