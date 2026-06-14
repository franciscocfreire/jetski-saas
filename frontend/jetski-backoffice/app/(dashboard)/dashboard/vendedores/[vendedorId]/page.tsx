'use client'

import { useState } from 'react'
import { useParams, useRouter } from 'next/navigation'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  ArrowLeft,
  Phone,
  Mail,
  Trophy,
  Clock,
  TrendingUp,
  CheckCircle,
  DollarSign,
  Target,
  Award,
  CreditCard,
  AlertCircle,
} from 'lucide-react'
import { useTenantStore } from '@/lib/store/tenant-store'
import { vendedoresService } from '@/lib/api/services'
import type { PagamentoLoteRequest } from '@/lib/api/types'
import { formatCurrency, formatDateTime } from '@/lib/utils'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Badge } from '@/components/ui/badge'
import { Progress } from '@/components/ui/progress'
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
import { Label } from '@/components/ui/label'
import { Skeleton } from '@/components/ui/skeleton'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'

const statusComissaoConfig = {
  PENDENTE: { label: 'Pendente', variant: 'warning' as const, color: 'text-yellow-600' },
  APROVADA: { label: 'Aprovada', variant: 'default' as const, color: 'text-blue-600' },
  PAGA: { label: 'Paga', variant: 'success' as const, color: 'text-green-600' },
  CANCELADA: { label: 'Cancelada', variant: 'destructive' as const, color: 'text-red-600' },
}

const statusBonusConfig = {
  PENDENTE: { label: 'Pendente', variant: 'warning' as const },
  APROVADO: { label: 'Aprovado', variant: 'default' as const },
  PAGO: { label: 'Pago', variant: 'success' as const },
  CANCELADO: { label: 'Cancelado', variant: 'destructive' as const },
}

function PagarLoteDialog({
  vendedorId,
  vendedorNome,
  totalAprovadas,
  open,
  onOpenChange,
}: {
  vendedorId: string
  vendedorNome: string
  totalAprovadas: number
  open: boolean
  onOpenChange: (open: boolean) => void
}) {
  const queryClient = useQueryClient()
  const [formData, setFormData] = useState<PagamentoLoteRequest>({
    referenciaPagamento: '',
    observacao: '',
  })

  const pagarMutation = useMutation({
    mutationFn: (data: PagamentoLoteRequest) =>
      vendedoresService.pagarLote(vendedorId, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['vendedor-detalhes'] })
      queryClient.invalidateQueries({ queryKey: ['vendedor-comissoes'] })
      queryClient.invalidateQueries({ queryKey: ['vendedores'] })
      onOpenChange(false)
      setFormData({ referenciaPagamento: '', observacao: '' })
    },
  })

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    pagarMutation.mutate(formData)
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-[425px]">
        <form onSubmit={handleSubmit}>
          <DialogHeader>
            <DialogTitle>Pagar Comissões em Lote</DialogTitle>
            <DialogDescription>
              Marcar todas as comissões aprovadas de {vendedorNome} como pagas
            </DialogDescription>
          </DialogHeader>

          <div className="py-4 space-y-4">
            <div className="rounded-lg border bg-muted/30 p-4">
              <div className="flex justify-between items-center">
                <span className="text-muted-foreground">Total a pagar:</span>
                <span className="text-2xl font-bold text-green-600">
                  {formatCurrency(totalAprovadas)}
                </span>
              </div>
            </div>

            <div className="grid gap-2">
              <Label htmlFor="referencia">Referência do Pagamento *</Label>
              <Input
                id="referencia"
                value={formData.referenciaPagamento}
                onChange={(e) => setFormData({ ...formData, referenciaPagamento: e.target.value })}
                placeholder="Ex: PIX-2024-001 ou TED-12345"
                required
              />
            </div>

            <div className="grid gap-2">
              <Label htmlFor="observacao">Observação</Label>
              <Input
                id="observacao"
                value={formData.observacao || ''}
                onChange={(e) => setFormData({ ...formData, observacao: e.target.value })}
                placeholder="Observação opcional"
              />
            </div>

            {pagarMutation.isError && (
              <div className="rounded-lg border border-destructive bg-destructive/10 p-3 text-sm text-destructive">
                {(pagarMutation.error as Error)?.message || 'Erro ao processar pagamento'}
              </div>
            )}
          </div>

          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
              Cancelar
            </Button>
            <Button type="submit" disabled={pagarMutation.isPending || !formData.referenciaPagamento}>
              {pagarMutation.isPending ? 'Processando...' : 'Confirmar Pagamento'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

export default function VendedorDetalhesPage() {
  const params = useParams()
  const router = useRouter()
  const { currentTenant } = useTenantStore()
  const vendedorId = params.vendedorId as string

  const [statusFilter, setStatusFilter] = useState<string>('all')
  const [pagarDialogOpen, setPagarDialogOpen] = useState(false)

  // Fetch vendedor details
  const { data: vendedor, isLoading: isLoadingVendedor } = useQuery({
    queryKey: ['vendedor-detalhes', currentTenant?.id, vendedorId],
    queryFn: () => vendedoresService.getDetails(vendedorId),
    enabled: !!currentTenant && !!vendedorId,
  })

  // Fetch commissions
  const { data: comissoes, isLoading: isLoadingComissoes } = useQuery({
    queryKey: ['vendedor-comissoes', currentTenant?.id, vendedorId, statusFilter],
    queryFn: () => vendedoresService.listComissoes(vendedorId, {
      status: statusFilter !== 'all' ? statusFilter : undefined,
    }),
    enabled: !!currentTenant && !!vendedorId,
  })

  // Fetch bonus history
  const { data: bonus, isLoading: isLoadingBonus } = useQuery({
    queryKey: ['vendedor-bonus', currentTenant?.id, vendedorId],
    queryFn: () => vendedoresService.listBonus(vendedorId),
    enabled: !!currentTenant && !!vendedorId,
  })

  if (!currentTenant) {
    return (
      <div className="flex h-[50vh] items-center justify-center">
        <p className="text-muted-foreground">Selecione um tenant para continuar</p>
      </div>
    )
  }

  if (isLoadingVendedor) {
    return (
      <div className="space-y-6">
        <Skeleton className="h-10 w-48" />
        <div className="grid gap-4 md:grid-cols-4">
          {Array.from({ length: 4 }).map((_, i) => (
            <Skeleton key={i} className="h-32" />
          ))}
        </div>
        <Skeleton className="h-64" />
      </div>
    )
  }

  if (!vendedor) {
    return (
      <div className="flex h-[50vh] flex-col items-center justify-center gap-4">
        <AlertCircle className="h-12 w-12 text-muted-foreground" />
        <p className="text-muted-foreground">Vendedor não encontrado</p>
        <Button variant="outline" onClick={() => router.back()}>
          <ArrowLeft className="mr-2 h-4 w-4" />
          Voltar
        </Button>
      </div>
    )
  }

  const bonusStatus = vendedor.bonusStatus
  const progressPercent = bonusStatus?.elegivel && bonusStatus.metaNecessaria > 0
    ? Math.min(100, (bonusStatus.metaAtual / bonusStatus.metaNecessaria) * 100)
    : 0

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center gap-4">
        <Button variant="ghost" size="icon" onClick={() => router.back()}>
          <ArrowLeft className="h-4 w-4" />
        </Button>
        <div className="flex-1">
          <div className="flex items-center gap-3">
            <h1 className="text-3xl font-bold">{vendedor.nome}</h1>
            <Badge variant={vendedor.ativo ? 'success' : 'destructive'}>
              {vendedor.ativo ? 'Ativo' : 'Inativo'}
            </Badge>
            <Badge variant={vendedor.tipo === 'INTERNO' ? 'default' : 'secondary'}>
              {vendedor.tipo === 'INTERNO' ? 'Interno' : 'Parceiro'}
            </Badge>
          </div>
          <div className="flex items-center gap-4 mt-1 text-muted-foreground">
            {vendedor.email && (
              <span className="flex items-center gap-1">
                <Mail className="h-4 w-4" />
                {vendedor.email}
              </span>
            )}
            {vendedor.telefone && (
              <span className="flex items-center gap-1">
                <Phone className="h-4 w-4" />
                {vendedor.telefone}
              </span>
            )}
          </div>
        </div>
        {(vendedor.totalAprovadas || 0) > 0 && (
          <Button onClick={() => setPagarDialogOpen(true)}>
            <CreditCard className="mr-2 h-4 w-4" />
            Pagar Comissões ({formatCurrency(vendedor.totalAprovadas || 0)})
          </Button>
        )}
      </div>

      {/* Summary Cards */}
      <div className="grid gap-4 md:grid-cols-4">
        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">Pendentes</CardTitle>
            <Clock className="h-4 w-4 text-yellow-600" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold text-yellow-600">
              {formatCurrency(vendedor.totalPendentes || 0)}
            </div>
            <p className="text-xs text-muted-foreground">
              Aguardando aprovação
            </p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">Aprovadas</CardTitle>
            <TrendingUp className="h-4 w-4 text-blue-600" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold text-blue-600">
              {formatCurrency(vendedor.totalAprovadas || 0)}
            </div>
            <p className="text-xs text-muted-foreground">
              Aguardando pagamento
            </p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">Pagas</CardTitle>
            <CheckCircle className="h-4 w-4 text-green-600" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold text-green-600">
              {formatCurrency(vendedor.totalPagas || 0)}
            </div>
            <p className="text-xs text-muted-foreground">
              Total pago (histórico)
            </p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">Locações</CardTitle>
            <Target className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">
              {vendedor.qtdLocacoes || 0}
            </div>
            <p className="text-xs text-muted-foreground">
              {vendedor.qtdAcimaPrecoBase || 0} acima do preço base
            </p>
          </CardContent>
        </Card>
      </div>

      {/* Bonus Progress Card */}
      {bonusStatus?.elegivel && (
        <Card className="border-2 border-amber-200 bg-amber-50/50">
          <CardHeader>
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <Trophy className="h-5 w-5 text-amber-600" />
                <CardTitle className="text-lg">Progresso do Bônus</CardTitle>
              </div>
              <Badge variant="outline" className="text-amber-600 border-amber-600">
                Bônus de {formatCurrency(bonusStatus.valorBonus || 0)}
              </Badge>
            </div>
            <CardDescription>
              Vendas acima do preço base para atingir a próxima meta
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="space-y-2">
              <div className="flex justify-between text-sm">
                <span>Progresso atual</span>
                <span className="font-medium">
                  {bonusStatus.metaAtual} / {bonusStatus.metaNecessaria} vendas
                </span>
              </div>
              <Progress value={progressPercent} className="h-3" />
            </div>
            <div className="flex items-center justify-between">
              <span className="text-sm text-muted-foreground">
                Faltam <span className="font-medium text-foreground">{bonusStatus.vendasFaltando}</span> vendas para o próximo bônus
              </span>
              {progressPercent >= 80 && (
                <Badge variant="outline" className="text-green-600 border-green-600">
                  Quase lá!
                </Badge>
              )}
            </div>
          </CardContent>
        </Card>
      )}

      {/* Tabs */}
      <Tabs defaultValue="comissoes" className="space-y-4">
        <TabsList>
          <TabsTrigger value="comissoes">Comissões</TabsTrigger>
          <TabsTrigger value="bonus">Histórico de Bônus</TabsTrigger>
        </TabsList>

        {/* Comissões Tab */}
        <TabsContent value="comissoes" className="space-y-4">
          <div className="flex justify-between items-center">
            <h2 className="text-xl font-semibold">Comissões</h2>
            <Select value={statusFilter} onValueChange={setStatusFilter}>
              <SelectTrigger className="w-48">
                <SelectValue placeholder="Filtrar por status" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">Todos</SelectItem>
                <SelectItem value="PENDENTE">Pendentes</SelectItem>
                <SelectItem value="APROVADA">Aprovadas</SelectItem>
                <SelectItem value="PAGA">Pagas</SelectItem>
                <SelectItem value="CANCELADA">Canceladas</SelectItem>
              </SelectContent>
            </Select>
          </div>

          <div className="rounded-md border">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Data</TableHead>
                  <TableHead>Locação</TableHead>
                  <TableHead>Percentual</TableHead>
                  <TableHead>Valor Locação</TableHead>
                  <TableHead>Valor Comissão</TableHead>
                  <TableHead>Abaixo Base</TableHead>
                  <TableHead>Status</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {isLoadingComissoes ? (
                  Array.from({ length: 5 }).map((_, i) => (
                    <TableRow key={i}>
                      <TableCell><Skeleton className="h-4 w-24" /></TableCell>
                      <TableCell><Skeleton className="h-4 w-32" /></TableCell>
                      <TableCell><Skeleton className="h-4 w-16" /></TableCell>
                      <TableCell><Skeleton className="h-4 w-20" /></TableCell>
                      <TableCell><Skeleton className="h-4 w-20" /></TableCell>
                      <TableCell><Skeleton className="h-4 w-12" /></TableCell>
                      <TableCell><Skeleton className="h-6 w-20" /></TableCell>
                    </TableRow>
                  ))
                ) : !comissoes || comissoes.length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={7} className="h-24 text-center">
                      <div className="flex flex-col items-center gap-2">
                        <DollarSign className="h-8 w-8 text-muted-foreground" />
                        <p className="text-muted-foreground">Nenhuma comissão encontrada</p>
                      </div>
                    </TableCell>
                  </TableRow>
                ) : (
                  comissoes.map((comissao) => (
                    <TableRow key={comissao.id}>
                      <TableCell>{formatDateTime(comissao.createdAt)}</TableCell>
                      <TableCell className="font-medium">
                        {comissao.locacaoId?.substring(0, 8)}...
                      </TableCell>
                      <TableCell>{comissao.percentualAplicado}%</TableCell>
                      <TableCell>{formatCurrency(comissao.valorTotalLocacao)}</TableCell>
                      <TableCell className="font-medium">
                        {formatCurrency(comissao.valorComissao)}
                      </TableCell>
                      <TableCell>
                        {!comissao.vendaAcimaPrecoBase ? (
                          <Badge variant="warning" className="text-xs">Sim</Badge>
                        ) : (
                          <Badge variant="secondary" className="text-xs">Não</Badge>
                        )}
                      </TableCell>
                      <TableCell>
                        <Badge variant={statusComissaoConfig[comissao.status]?.variant || 'default'}>
                          {statusComissaoConfig[comissao.status]?.label || comissao.status}
                        </Badge>
                      </TableCell>
                    </TableRow>
                  ))
                )}
              </TableBody>
            </Table>
          </div>
        </TabsContent>

        {/* Bônus Tab */}
        <TabsContent value="bonus" className="space-y-4">
          <h2 className="text-xl font-semibold">Histórico de Bônus</h2>

          <div className="rounded-md border">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Data</TableHead>
                  <TableHead>Meta Atingida</TableHead>
                  <TableHead>Valor</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead>Aprovado Em</TableHead>
                  <TableHead>Pago Em</TableHead>
                  <TableHead>Referência</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {isLoadingBonus ? (
                  Array.from({ length: 3 }).map((_, i) => (
                    <TableRow key={i}>
                      <TableCell><Skeleton className="h-4 w-24" /></TableCell>
                      <TableCell><Skeleton className="h-4 w-20" /></TableCell>
                      <TableCell><Skeleton className="h-4 w-20" /></TableCell>
                      <TableCell><Skeleton className="h-6 w-20" /></TableCell>
                      <TableCell><Skeleton className="h-4 w-24" /></TableCell>
                      <TableCell><Skeleton className="h-4 w-24" /></TableCell>
                      <TableCell><Skeleton className="h-4 w-24" /></TableCell>
                    </TableRow>
                  ))
                ) : !bonus || bonus.length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={7} className="h-24 text-center">
                      <div className="flex flex-col items-center gap-2">
                        <Award className="h-8 w-8 text-muted-foreground" />
                        <p className="text-muted-foreground">Nenhum bônus conquistado ainda</p>
                      </div>
                    </TableCell>
                  </TableRow>
                ) : (
                  bonus.map((b) => (
                    <TableRow key={b.id}>
                      <TableCell>{formatDateTime(b.createdAt)}</TableCell>
                      <TableCell className="font-medium">
                        {b.metaAtingida} vendas
                      </TableCell>
                      <TableCell className="font-medium text-green-600">
                        {formatCurrency(b.valorBonus)}
                      </TableCell>
                      <TableCell>
                        <Badge variant={statusBonusConfig[b.status]?.variant || 'default'}>
                          {statusBonusConfig[b.status]?.label || b.status}
                        </Badge>
                      </TableCell>
                      <TableCell>
                        {b.aprovadoEm ? formatDateTime(b.aprovadoEm) : '-'}
                      </TableCell>
                      <TableCell>
                        {b.pagoEm ? formatDateTime(b.pagoEm) : '-'}
                      </TableCell>
                      <TableCell className="text-muted-foreground">
                        {b.referenciaPagamento || '-'}
                      </TableCell>
                    </TableRow>
                  ))
                )}
              </TableBody>
            </Table>
          </div>
        </TabsContent>
      </Tabs>

      {/* Pagar Dialog */}
      <PagarLoteDialog
        vendedorId={vendedorId}
        vendedorNome={vendedor.nome}
        totalAprovadas={vendedor.totalAprovadas || 0}
        open={pagarDialogOpen}
        onOpenChange={setPagarDialogOpen}
      />
    </div>
  )
}
