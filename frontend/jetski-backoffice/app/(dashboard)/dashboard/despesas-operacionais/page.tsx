'use client'

import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { format, startOfMonth, endOfMonth } from 'date-fns'
import { ptBR } from 'date-fns/locale'
import {
  Plus,
  Check,
  X,
  CreditCard,
  MoreHorizontal,
  CalendarIcon,
  FilterX,
} from 'lucide-react'

import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
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
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@/components/ui/dialog'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Calendar } from '@/components/ui/calendar'
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from '@/components/ui/popover'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { useToast } from '@/hooks/use-toast'
import { cn } from '@/lib/utils'

import { despesasOperacionaisService } from '@/lib/api/services'
import type {
  DespesaOperacional,
  DespesaOperacionalCreateRequest,
  CategoriaDespesa,
  StatusDespesa,
} from '@/lib/api/types'
import { CATEGORIAS_DESPESA, STATUS_DESPESA } from '@/lib/api/types'

const formatCurrency = (value: number) => {
  return new Intl.NumberFormat('pt-BR', {
    style: 'currency',
    currency: 'BRL',
  }).format(value)
}

const getStatusBadgeVariant = (status: StatusDespesa): 'default' | 'secondary' | 'destructive' | 'outline' | 'success' | 'warning' => {
  switch (status) {
    case 'PENDENTE':
      return 'warning'
    case 'APROVADA':
      return 'default'
    case 'REJEITADA':
      return 'destructive'
    case 'PAGA':
      return 'success'
    default:
      return 'secondary'
  }
}

export default function DespesasOperacionaisPage() {
  const { toast } = useToast()
  const queryClient = useQueryClient()

  // State for date range filter
  const [dateRange, setDateRange] = useState<{ from: Date; to: Date }>({
    from: startOfMonth(new Date()),
    to: endOfMonth(new Date()),
  })

  // State for dialogs
  const [isCreateDialogOpen, setIsCreateDialogOpen] = useState(false)
  const [editingDespesa, setEditingDespesa] = useState<DespesaOperacional | null>(null)

  // Form state
  const [formData, setFormData] = useState<DespesaOperacionalCreateRequest>({
    dtReferencia: format(new Date(), 'yyyy-MM-dd'),
    categoria: 'DIARIA_FUNCIONARIO',
    descricao: '',
    valor: 0,
    observacoes: '',
  })

  // Query for listing despesas
  const { data: despesas, isLoading } = useQuery({
    queryKey: ['despesas-operacionais', dateRange.from, dateRange.to],
    queryFn: () =>
      despesasOperacionaisService.listarPorPeriodo(
        format(dateRange.from, 'yyyy-MM-dd'),
        format(dateRange.to, 'yyyy-MM-dd')
      ),
  })

  // Mutations
  const createMutation = useMutation({
    mutationFn: despesasOperacionaisService.criar,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['despesas-operacionais'] })
      setIsCreateDialogOpen(false)
      resetForm()
      toast({ title: 'Despesa criada com sucesso' })
    },
    onError: () => {
      toast({ title: 'Erro ao criar despesa', variant: 'destructive' })
    },
  })

  const aprovarMutation = useMutation({
    mutationFn: despesasOperacionaisService.aprovar,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['despesas-operacionais'] })
      toast({ title: 'Despesa aprovada' })
    },
    onError: () => {
      toast({ title: 'Erro ao aprovar despesa', variant: 'destructive' })
    },
  })

  const rejeitarMutation = useMutation({
    mutationFn: (id: string) => despesasOperacionaisService.rejeitar(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['despesas-operacionais'] })
      toast({ title: 'Despesa rejeitada' })
    },
    onError: () => {
      toast({ title: 'Erro ao rejeitar despesa', variant: 'destructive' })
    },
  })

  const pagarMutation = useMutation({
    mutationFn: (id: string) => despesasOperacionaisService.pagar(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['despesas-operacionais'] })
      toast({ title: 'Despesa marcada como paga' })
    },
    onError: () => {
      toast({ title: 'Erro ao marcar despesa como paga', variant: 'destructive' })
    },
  })

  const deleteMutation = useMutation({
    mutationFn: despesasOperacionaisService.excluir,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['despesas-operacionais'] })
      toast({ title: 'Despesa excluída' })
    },
    onError: () => {
      toast({ title: 'Erro ao excluir despesa', variant: 'destructive' })
    },
  })

  const resetForm = () => {
    setFormData({
      dtReferencia: format(new Date(), 'yyyy-MM-dd'),
      categoria: 'DIARIA_FUNCIONARIO',
      descricao: '',
      valor: 0,
      observacoes: '',
    })
    setEditingDespesa(null)
  }

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    createMutation.mutate(formData)
  }

  // Calculate totals
  const totals = despesas?.reduce(
    (acc, d) => {
      acc.total += d.valor
      if (d.status === 'PENDENTE') acc.pendente += d.valor
      if (d.status === 'APROVADA') acc.aprovada += d.valor
      if (d.status === 'PAGA') acc.paga += d.valor
      return acc
    },
    { total: 0, pendente: 0, aprovada: 0, paga: 0 }
  ) || { total: 0, pendente: 0, aprovada: 0, paga: 0 }

  const getCategoriaLabel = (categoria: CategoriaDespesa) => {
    return CATEGORIAS_DESPESA.find((c) => c.value === categoria)?.label || categoria
  }

  const getStatusLabel = (status: StatusDespesa) => {
    return STATUS_DESPESA.find((s) => s.value === status)?.label || status
  }

  return (
    <div className="flex flex-col gap-6 p-6">
      {/* Header */}
      <div className="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
        <div>
          <h1 className="text-2xl font-bold">Despesas Operacionais</h1>
          <p className="text-muted-foreground">
            Gerencie as despesas do dia a dia da operação
          </p>
        </div>
        <Dialog open={isCreateDialogOpen} onOpenChange={setIsCreateDialogOpen}>
          <DialogTrigger asChild>
            <Button>
              <Plus className="mr-2 size-4" />
              Nova Despesa
            </Button>
          </DialogTrigger>
          <DialogContent>
            <DialogHeader>
              <DialogTitle>Nova Despesa Operacional</DialogTitle>
            </DialogHeader>
            <form onSubmit={handleSubmit} className="space-y-4">
              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-2">
                  <Label>Data</Label>
                  <Popover>
                    <PopoverTrigger asChild>
                      <Button
                        variant="outline"
                        className={cn(
                          'w-full justify-start text-left font-normal',
                          !formData.dtReferencia && 'text-muted-foreground'
                        )}
                      >
                        <CalendarIcon className="mr-2 h-4 w-4" />
                        {formData.dtReferencia
                          ? format(new Date(formData.dtReferencia), 'dd/MM/yyyy')
                          : 'Selecione'}
                      </Button>
                    </PopoverTrigger>
                    <PopoverContent className="w-auto p-0">
                      <Calendar
                        mode="single"
                        selected={
                          formData.dtReferencia
                            ? new Date(formData.dtReferencia)
                            : undefined
                        }
                        onSelect={(date) =>
                          setFormData((prev) => ({
                            ...prev,
                            dtReferencia: date
                              ? format(date, 'yyyy-MM-dd')
                              : prev.dtReferencia,
                          }))
                        }
                        locale={ptBR}
                      />
                    </PopoverContent>
                  </Popover>
                </div>
                <div className="space-y-2">
                  <Label>Valor (R$)</Label>
                  <Input
                    type="number"
                    step="0.01"
                    min="0"
                    value={formData.valor}
                    onChange={(e) =>
                      setFormData((prev) => ({
                        ...prev,
                        valor: parseFloat(e.target.value) || 0,
                      }))
                    }
                  />
                </div>
              </div>
              <div className="space-y-2">
                <Label>Categoria</Label>
                <Select
                  value={formData.categoria}
                  onValueChange={(value: CategoriaDespesa) =>
                    setFormData((prev) => ({ ...prev, categoria: value }))
                  }
                >
                  <SelectTrigger>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {CATEGORIAS_DESPESA.map((cat) => (
                      <SelectItem key={cat.value} value={cat.value}>
                        {cat.label}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <div className="space-y-2">
                <Label>Descrição</Label>
                <Input
                  value={formData.descricao || ''}
                  onChange={(e) =>
                    setFormData((prev) => ({ ...prev, descricao: e.target.value }))
                  }
                  placeholder="Descrição opcional"
                />
              </div>
              <div className="space-y-2">
                <Label>Observações</Label>
                <Textarea
                  value={formData.observacoes || ''}
                  onChange={(e) =>
                    setFormData((prev) => ({ ...prev, observacoes: e.target.value }))
                  }
                  placeholder="Observações adicionais"
                />
              </div>
              <div className="flex justify-end gap-2">
                <Button
                  type="button"
                  variant="outline"
                  onClick={() => setIsCreateDialogOpen(false)}
                >
                  Cancelar
                </Button>
                <Button type="submit" disabled={createMutation.isPending}>
                  {createMutation.isPending ? 'Salvando...' : 'Salvar'}
                </Button>
              </div>
            </form>
          </DialogContent>
        </Dialog>
      </div>

      {/* Summary Cards */}
      <div className="grid grid-cols-2 gap-4 md:grid-cols-4">
        <Card>
          <CardHeader className="pb-2">
            <CardTitle className="text-sm font-medium text-muted-foreground">
              Total Período
            </CardTitle>
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">{formatCurrency(totals.total)}</div>
          </CardContent>
        </Card>
        <Card>
          <CardHeader className="pb-2">
            <CardTitle className="text-sm font-medium text-yellow-600">
              Pendentes
            </CardTitle>
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold text-yellow-600">
              {formatCurrency(totals.pendente)}
            </div>
          </CardContent>
        </Card>
        <Card>
          <CardHeader className="pb-2">
            <CardTitle className="text-sm font-medium text-blue-600">
              Aprovadas
            </CardTitle>
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold text-blue-600">
              {formatCurrency(totals.aprovada)}
            </div>
          </CardContent>
        </Card>
        <Card>
          <CardHeader className="pb-2">
            <CardTitle className="text-sm font-medium text-green-600">
              Pagas
            </CardTitle>
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold text-green-600">
              {formatCurrency(totals.paga)}
            </div>
          </CardContent>
        </Card>
      </div>

      {/* Filters */}
      <Card>
        <CardContent className="pt-6">
          <div className="flex flex-wrap items-center gap-4">
            <div className="flex items-center gap-2">
              <Label>Período:</Label>
              <Popover>
                <PopoverTrigger asChild>
                  <Button variant="outline" className="w-[200px]">
                    <CalendarIcon className="mr-2 h-4 w-4" />
                    {format(dateRange.from, 'dd/MM/yyyy')} -{' '}
                    {format(dateRange.to, 'dd/MM/yyyy')}
                  </Button>
                </PopoverTrigger>
                <PopoverContent className="w-auto p-0" align="start">
                  <Calendar
                    mode="range"
                    selected={{ from: dateRange.from, to: dateRange.to }}
                    onSelect={(range) => {
                      if (range?.from && range?.to) {
                        setDateRange({ from: range.from, to: range.to })
                      }
                    }}
                    locale={ptBR}
                    numberOfMonths={2}
                  />
                </PopoverContent>
              </Popover>
            </div>
            <Button
              variant="ghost"
              size="sm"
              onClick={() =>
                setDateRange({
                  from: startOfMonth(new Date()),
                  to: endOfMonth(new Date()),
                })
              }
            >
              <FilterX className="mr-2 h-4 w-4" />
              Limpar
            </Button>
          </div>
        </CardContent>
      </Card>

      {/* Table */}
      <Card>
        <CardContent className="p-0">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Data</TableHead>
                <TableHead>Categoria</TableHead>
                <TableHead>Descrição</TableHead>
                <TableHead className="text-right">Valor</TableHead>
                <TableHead>Status</TableHead>
                <TableHead className="w-[50px]"></TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {isLoading ? (
                <TableRow>
                  <TableCell colSpan={6} className="text-center">
                    Carregando...
                  </TableCell>
                </TableRow>
              ) : despesas?.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={6} className="text-center text-muted-foreground">
                    Nenhuma despesa encontrada no período
                  </TableCell>
                </TableRow>
              ) : (
                despesas?.map((despesa) => (
                  <TableRow key={despesa.id}>
                    <TableCell>
                      {format(new Date(despesa.dtReferencia), 'dd/MM/yyyy')}
                    </TableCell>
                    <TableCell>{getCategoriaLabel(despesa.categoria)}</TableCell>
                    <TableCell>{despesa.descricao || '-'}</TableCell>
                    <TableCell className="text-right font-medium">
                      {formatCurrency(despesa.valor)}
                    </TableCell>
                    <TableCell>
                      <Badge variant={getStatusBadgeVariant(despesa.status)}>
                        {getStatusLabel(despesa.status)}
                      </Badge>
                    </TableCell>
                    <TableCell>
                      <DropdownMenu>
                        <DropdownMenuTrigger asChild>
                          <Button variant="ghost" size="icon">
                            <MoreHorizontal className="h-4 w-4" />
                          </Button>
                        </DropdownMenuTrigger>
                        <DropdownMenuContent align="end">
                          {despesa.status === 'PENDENTE' && (
                            <>
                              <DropdownMenuItem
                                onClick={() => aprovarMutation.mutate(despesa.id)}
                              >
                                <Check className="mr-2 h-4 w-4 text-green-500" />
                                Aprovar
                              </DropdownMenuItem>
                              <DropdownMenuItem
                                onClick={() => rejeitarMutation.mutate(despesa.id)}
                              >
                                <X className="mr-2 h-4 w-4 text-red-500" />
                                Rejeitar
                              </DropdownMenuItem>
                            </>
                          )}
                          {despesa.status === 'APROVADA' && (
                            <DropdownMenuItem
                              onClick={() => pagarMutation.mutate(despesa.id)}
                            >
                              <CreditCard className="mr-2 h-4 w-4 text-blue-500" />
                              Marcar como Paga
                            </DropdownMenuItem>
                          )}
                          {despesa.status === 'PENDENTE' && (
                            <DropdownMenuItem
                              onClick={() => deleteMutation.mutate(despesa.id)}
                              className="text-destructive"
                            >
                              Excluir
                            </DropdownMenuItem>
                          )}
                        </DropdownMenuContent>
                      </DropdownMenu>
                    </TableCell>
                  </TableRow>
                ))
              )}
            </TableBody>
          </Table>
        </CardContent>
      </Card>
    </div>
  )
}
