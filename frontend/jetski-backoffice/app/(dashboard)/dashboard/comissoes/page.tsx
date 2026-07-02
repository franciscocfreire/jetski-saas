'use client'

import { useState, useMemo } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  Percent,
  Check,
  Clock,
  DollarSign,
  CheckCircle,
  AlertCircle,
  TrendingUp,
} from 'lucide-react'
import { useTenantStore } from '@/lib/store/tenant-store'
import { comissoesService, vendedoresService } from '@/lib/api/services'
import type { Comissao, StatusComissao } from '@/lib/api/types'
import { formatCurrency } from '@/lib/utils'
import { Button } from '@/components/ui/button'
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
  Tabs,
  TabsContent,
  TabsList,
  TabsTrigger,
} from '@/components/ui/tabs'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { format } from 'date-fns'
import { ptBR } from 'date-fns/locale'
import { toast } from 'sonner'

// Status configuration
const statusConfig: Record<StatusComissao, { label: string; variant: 'default' | 'secondary' | 'destructive' | 'outline' }> = {
  PENDENTE: { label: 'Pendente', variant: 'secondary' },
  APROVADA: { label: 'Aprovada', variant: 'default' },
  PAGA: { label: 'Paga', variant: 'outline' },
  CANCELADA: { label: 'Cancelada', variant: 'destructive' },
}

// Custom checkbox component (avoiding Radix issues)
function CustomCheckbox({ checked, onChange, disabled }: { checked: boolean; onChange: () => void; disabled?: boolean }) {
  return (
    <button
      type="button"
      onClick={onChange}
      disabled={disabled}
      className={`h-4 w-4 rounded border flex items-center justify-center flex-shrink-0 transition-colors ${
        checked ? 'bg-primary border-primary' : 'border-input hover:border-primary/50'
      } ${disabled ? 'opacity-50 cursor-not-allowed' : 'cursor-pointer'}`}
    >
      {checked && <Check className="h-3 w-3 text-primary-foreground" />}
    </button>
  )
}

export default function ComissoesPage() {
  const { currentTenant } = useTenantStore()
  const queryClient = useQueryClient()

  const [selectedTab, setSelectedTab] = useState('pendentes')
  const [selectedComissoes, setSelectedComissoes] = useState<Set<string>>(new Set())
  const [filtroVendedor, setFiltroVendedor] = useState<string>('todos')
  const [dialogOpen, setDialogOpen] = useState(false)

  // Fetch commissions
  const { data: pendentes, isLoading: loadingPendentes } = useQuery({
    queryKey: ['comissoes', 'pendentes', currentTenant?.id],
    queryFn: () => comissoesService.listPendentes(currentTenant!.id),
    enabled: !!currentTenant,
  })

  const { data: aprovadas, isLoading: loadingAprovadas } = useQuery({
    queryKey: ['comissoes', 'aprovadas', currentTenant?.id],
    queryFn: () => comissoesService.listAguardandoPagamento(currentTenant!.id),
    enabled: !!currentTenant,
  })

  // Fetch vendors for mapping names
  const { data: vendedores } = useQuery({
    queryKey: ['vendedores', currentTenant?.id],
    queryFn: () => vendedoresService.list(),
    enabled: !!currentTenant,
  })

  // Create vendor name map
  const vendedorMap = useMemo(() => {
    const map = new Map<string, string>()
    vendedores?.forEach((v) => map.set(v.id, v.nome))
    return map
  }, [vendedores])

  // Filter commissions by vendor
  const filteredPendentes = useMemo(() => {
    if (!pendentes) return []
    if (filtroVendedor === 'todos') return pendentes
    return pendentes.filter((c) => c.vendedorId === filtroVendedor)
  }, [pendentes, filtroVendedor])

  const filteredAprovadas = useMemo(() => {
    if (!aprovadas) return []
    if (filtroVendedor === 'todos') return aprovadas
    return aprovadas.filter((c) => c.vendedorId === filtroVendedor)
  }, [aprovadas, filtroVendedor])

  // Calculate totals
  const totals = useMemo(() => ({
    pendentes: pendentes?.reduce((sum, c) => sum + c.valorComissao, 0) || 0,
    aprovadas: aprovadas?.reduce((sum, c) => sum + c.valorComissao, 0) || 0,
    qtdPendentes: pendentes?.length || 0,
    qtdAprovadas: aprovadas?.length || 0,
  }), [pendentes, aprovadas])

  // Approve single commission
  const aprovarMutation = useMutation({
    mutationFn: (id: string) => comissoesService.aprovar(currentTenant!.id, id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['comissoes'] })
      toast.success('Comissao aprovada com sucesso')
    },
    onError: () => {
      toast.error('Erro ao aprovar comissao')
    },
  })

  // Approve multiple commissions
  const aprovarLoteMutation = useMutation({
    mutationFn: (ids: string[]) => comissoesService.aprovarLote(currentTenant!.id, ids),
    onSuccess: (data) => {
      queryClient.invalidateQueries({ queryKey: ['comissoes'] })
      setSelectedComissoes(new Set())
      setDialogOpen(false)
      toast.success(`${data.length} comissoes aprovadas com sucesso`)
    },
    onError: () => {
      toast.error('Erro ao aprovar comissoes em lote')
    },
  })

  // Toggle selection
  const toggleSelection = (id: string) => {
    const newSet = new Set(selectedComissoes)
    if (newSet.has(id)) {
      newSet.delete(id)
    } else {
      newSet.add(id)
    }
    setSelectedComissoes(newSet)
  }

  // Toggle all selections
  const toggleAll = () => {
    if (selectedComissoes.size === filteredPendentes.length) {
      setSelectedComissoes(new Set())
    } else {
      setSelectedComissoes(new Set(filteredPendentes.map((c) => c.id)))
    }
  }

  // Calculate selected total
  const selectedTotal = useMemo(() => {
    return filteredPendentes
      .filter((c) => selectedComissoes.has(c.id))
      .reduce((sum, c) => sum + c.valorComissao, 0)
  }, [filteredPendentes, selectedComissoes])

  // Handle bulk approve
  const handleBulkApprove = () => {
    if (selectedComissoes.size === 0) return
    setDialogOpen(true)
  }

  const confirmBulkApprove = () => {
    aprovarLoteMutation.mutate(Array.from(selectedComissoes))
  }

  if (!currentTenant) {
    return (
      <div className="flex h-[50vh] items-center justify-center">
        <p className="text-muted-foreground">Selecione um tenant para continuar</p>
      </div>
    )
  }

  const renderComissaoTable = (comissoes: Comissao[], showCheckbox: boolean, showApproveButton: boolean) => (
    <Table>
      <TableHeader>
        <TableRow>
          {showCheckbox && (
            <TableHead className="w-[40px]">
              <CustomCheckbox
                checked={selectedComissoes.size === filteredPendentes.length && filteredPendentes.length > 0}
                onChange={toggleAll}
              />
            </TableHead>
          )}
          <TableHead>Data</TableHead>
          <TableHead>Vendedor</TableHead>
          <TableHead className="text-right">Valor Locacao</TableHead>
          <TableHead className="text-right">Comissao</TableHead>
          <TableHead className="text-center">%</TableHead>
          <TableHead className="text-center">Status</TableHead>
          {showApproveButton && <TableHead className="w-[100px]"></TableHead>}
        </TableRow>
      </TableHeader>
      <TableBody>
        {comissoes.length === 0 ? (
          <TableRow>
            <TableCell colSpan={showCheckbox ? 8 : 7} className="h-24 text-center">
              <div className="flex flex-col items-center gap-2">
                <CheckCircle className="h-8 w-8 text-green-500" />
                <p className="text-muted-foreground">
                  {showApproveButton ? 'Nenhuma comissao pendente de aprovacao' : 'Nenhuma comissao encontrada'}
                </p>
              </div>
            </TableCell>
          </TableRow>
        ) : (
          comissoes.map((comissao) => (
            <TableRow key={comissao.id}>
              {showCheckbox && (
                <TableCell>
                  <CustomCheckbox
                    checked={selectedComissoes.has(comissao.id)}
                    onChange={() => toggleSelection(comissao.id)}
                  />
                </TableCell>
              )}
              <TableCell>
                <div>
                  <p className="font-medium">
                    {format(new Date(comissao.dataLocacao), 'dd/MM/yyyy', { locale: ptBR })}
                  </p>
                  <p className="text-xs text-muted-foreground">
                    {format(new Date(comissao.dataLocacao), 'HH:mm', { locale: ptBR })}
                  </p>
                </div>
              </TableCell>
              <TableCell>
                <div>
                  <p className="font-medium">{vendedorMap.get(comissao.vendedorId) || 'Vendedor'}</p>
                  {comissao.vendaAcimaPrecoBase && (
                    <Badge variant="outline" className="text-xs mt-1 bg-green-50 text-green-700 border-green-200">
                      <TrendingUp className="h-3 w-3 mr-1" />
                      Acima do preco base
                    </Badge>
                  )}
                </div>
              </TableCell>
              <TableCell className="text-right">
                {formatCurrency(comissao.valorTotalLocacao)}
              </TableCell>
              <TableCell className="text-right font-bold text-green-600">
                {formatCurrency(comissao.valorComissao)}
              </TableCell>
              <TableCell className="text-center">
                <Badge variant="secondary">
                  {comissao.percentualAplicado ? `${comissao.percentualAplicado}%` : '-'}
                </Badge>
              </TableCell>
              <TableCell className="text-center">
                <Badge variant={statusConfig[comissao.status].variant}>
                  {statusConfig[comissao.status].label}
                </Badge>
              </TableCell>
              {showApproveButton && (
                <TableCell>
                  <Button
                    size="sm"
                    onClick={() => aprovarMutation.mutate(comissao.id)}
                    disabled={aprovarMutation.isPending}
                  >
                    Aprovar
                  </Button>
                </TableCell>
              )}
            </TableRow>
          ))
        )}
      </TableBody>
    </Table>
  )

  const renderSkeleton = () => (
    <div className="space-y-4">
      {Array.from({ length: 5 }).map((_, i) => (
        <div key={i} className="flex items-center gap-4 p-4">
          <Skeleton className="h-4 w-4" />
          <Skeleton className="h-4 w-24" />
          <Skeleton className="h-4 w-32" />
          <Skeleton className="h-4 w-20" />
          <Skeleton className="h-4 w-20" />
          <Skeleton className="h-6 w-12" />
          <Skeleton className="h-6 w-20" />
          <Skeleton className="h-8 w-20" />
        </div>
      ))}
    </div>
  )

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-3xl font-bold">Comissoes</h1>
        <p className="text-muted-foreground">
          Aprove e gerencie as comissoes dos vendedores
        </p>
      </div>

      {/* Summary Cards */}
      <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">Pendentes de Aprovacao</CardTitle>
            <Clock className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold text-warning">
              {formatCurrency(totals.pendentes)}
            </div>
            <p className="text-xs text-muted-foreground">
              {totals.qtdPendentes} comissoes aguardando
            </p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">Aprovadas (Aguard. Pgto)</CardTitle>
            <CheckCircle className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold text-blue-600">
              {formatCurrency(totals.aprovadas)}
            </div>
            <p className="text-xs text-muted-foreground">
              {totals.qtdAprovadas} comissoes aprovadas
            </p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">Selecionadas</CardTitle>
            <Percent className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold text-purple-600">
              {formatCurrency(selectedTotal)}
            </div>
            <p className="text-xs text-muted-foreground">
              {selectedComissoes.size} comissoes selecionadas
            </p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">Total Geral</CardTitle>
            <DollarSign className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold text-green-600">
              {formatCurrency(totals.pendentes + totals.aprovadas)}
            </div>
            <p className="text-xs text-muted-foreground">
              Pendentes + Aprovadas
            </p>
          </CardContent>
        </Card>
      </div>

      {/* Filters and Actions */}
      <div className="flex flex-col sm:flex-row gap-4 items-start sm:items-center justify-between">
        <div className="flex gap-4 items-center">
          <Select value={filtroVendedor} onValueChange={setFiltroVendedor}>
            <SelectTrigger className="w-[200px]">
              <SelectValue placeholder="Filtrar por vendedor" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="todos">Todos os vendedores</SelectItem>
              {vendedores?.map((v) => (
                <SelectItem key={v.id} value={v.id}>
                  {v.nome}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>

        {selectedTab === 'pendentes' && selectedComissoes.size > 0 && (
          <Button onClick={handleBulkApprove}>
            Aprovar {selectedComissoes.size} selecionada{selectedComissoes.size > 1 ? 's' : ''}
          </Button>
        )}
      </div>

      {/* Tabs */}
      <Tabs value={selectedTab} onValueChange={setSelectedTab} className="space-y-4">
        <TabsList>
          <TabsTrigger value="pendentes" className="gap-2">
            <Clock className="h-4 w-4" />
            Pendentes ({totals.qtdPendentes})
          </TabsTrigger>
          <TabsTrigger value="aprovadas" className="gap-2">
            <CheckCircle className="h-4 w-4" />
            Aprovadas ({totals.qtdAprovadas})
          </TabsTrigger>
        </TabsList>

        <TabsContent value="pendentes">
          <div className="rounded-md border">
            {loadingPendentes ? renderSkeleton() : renderComissaoTable(filteredPendentes, true, true)}
          </div>
        </TabsContent>

        <TabsContent value="aprovadas">
          <div className="rounded-md border">
            {loadingAprovadas ? renderSkeleton() : renderComissaoTable(filteredAprovadas, false, false)}
          </div>
        </TabsContent>
      </Tabs>

      {/* Bulk Approve Dialog */}
      <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Confirmar Aprovacao em Lote</DialogTitle>
            <DialogDescription>
              Voce esta prestes a aprovar {selectedComissoes.size} comissao{selectedComissoes.size > 1 ? 'es' : ''}.
            </DialogDescription>
          </DialogHeader>

          <div className="py-4">
            <div className="flex justify-between items-center p-4 bg-muted rounded-lg">
              <div>
                <p className="text-sm text-muted-foreground">Quantidade</p>
                <p className="text-2xl font-bold">{selectedComissoes.size}</p>
              </div>
              <div className="text-right">
                <p className="text-sm text-muted-foreground">Valor Total</p>
                <p className="text-2xl font-bold text-green-600">{formatCurrency(selectedTotal)}</p>
              </div>
            </div>

            <div className="mt-4 p-3 bg-yellow-50 border border-yellow-200 rounded-lg flex items-start gap-2">
              <AlertCircle className="h-4 w-4 text-yellow-600 mt-0.5 flex-shrink-0" />
              <p className="text-sm text-yellow-800">
                Apos a aprovacao, as comissoes ficarao disponiveis para pagamento na pagina de Pagamentos.
              </p>
            </div>
          </div>

          <DialogFooter>
            <Button variant="outline" onClick={() => setDialogOpen(false)}>
              Cancelar
            </Button>
            <Button
              onClick={confirmBulkApprove}
              disabled={aprovarLoteMutation.isPending}
            >
              {aprovarLoteMutation.isPending ? 'Aprovando...' : 'Confirmar Aprovacao'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
