'use client'

import { useState, useEffect } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  DollarSign,
  Copy,
  Check,
  CreditCard,
  Wallet,
  AlertCircle,
  History,
  Award,
} from 'lucide-react'
import { useTenantStore } from '@/lib/store/tenant-store'
import { pagamentosService } from '@/lib/api/services'
import type {
  PendenciasPagamento,
  RegistrarPagamentoRequest,
  ItemPendente,
  TipoPagamento,
} from '@/lib/api/types'
import { formatCurrency } from '@/lib/utils'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Badge } from '@/components/ui/badge'
import { Textarea } from '@/components/ui/textarea'
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
import { Label } from '@/components/ui/label'
import { Skeleton } from '@/components/ui/skeleton'
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from '@/components/ui/tooltip'
import { format } from 'date-fns'
import { ptBR } from 'date-fns/locale'

function CopyButton({ text }: { text: string }) {
  const [copied, setCopied] = useState(false)

  const handleCopy = async () => {
    await navigator.clipboard.writeText(text)
    setCopied(true)
    setTimeout(() => setCopied(false), 2000)
  }

  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <Button variant="ghost" size="icon" className="h-6 w-6" onClick={handleCopy}>
          {copied ? (
            <Check className="h-3 w-3 text-green-500" />
          ) : (
            <Copy className="h-3 w-3" />
          )}
        </Button>
      </TooltipTrigger>
      <TooltipContent>
        <p>{copied ? 'Copiado!' : 'Copiar chave PIX'}</p>
      </TooltipContent>
    </Tooltip>
  )
}

function PagamentoDialog({
  pendencia,
  open,
  onOpenChange,
}: {
  pendencia: PendenciasPagamento | null
  open: boolean
  onOpenChange: (open: boolean) => void
}) {
  const queryClient = useQueryClient()
  const { currentTenant } = useTenantStore()

  const [tipoPagamento, setTipoPagamento] = useState<TipoPagamento>('PIX')
  const [referenciaPagamento, setReferenciaPagamento] = useState('')
  const [observacoes, setObservacoes] = useState('')
  const [selectedItems, setSelectedItems] = useState<Set<string>>(new Set())
  const [selectAll, setSelectAll] = useState(true)
  const [initialized, setInitialized] = useState(false)

  // Fetch detailed pending items
  const { data: detalhes, isLoading: loadingDetalhes } = useQuery({
    queryKey: ['pagamentos', 'detalhes', currentTenant?.id, pendencia?.vendedorId],
    queryFn: () => pagamentosService.getDetalhesPendencias(currentTenant!.id, pendencia!.vendedorId),
    enabled: open && !!currentTenant && !!pendencia,
  })

  // Initialize all items selected when details load (only once)
  useEffect(() => {
    if (detalhes?.itens && !initialized) {
      setSelectedItems(new Set(detalhes.itens.map((i) => i.id)))
      setSelectAll(true)
      setInitialized(true)
    }
  }, [detalhes, initialized])

  // Reset form when dialog closes
  useEffect(() => {
    if (!open) {
      setTipoPagamento('PIX')
      setReferenciaPagamento('')
      setObservacoes('')
      setSelectedItems(new Set())
      setSelectAll(true)
      setInitialized(false)
    }
  }, [open])

  const pagamentoMutation = useMutation({
    mutationFn: (data: RegistrarPagamentoRequest) =>
      pagamentosService.registrarPagamento(currentTenant!.id, pendencia!.vendedorId, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['pagamentos', 'pendencias'] })
      queryClient.invalidateQueries({ queryKey: ['pagamentos', 'historico'] })
      onOpenChange(false)
    },
  })

  const handleToggleItem = (id: string) => {
    const newSet = new Set(selectedItems)
    if (newSet.has(id)) {
      newSet.delete(id)
    } else {
      newSet.add(id)
    }
    setSelectedItems(newSet)
    setSelectAll(newSet.size === detalhes?.itens.length)
  }

  const handleToggleAll = () => {
    if (selectAll) {
      setSelectedItems(new Set())
    } else {
      setSelectedItems(new Set(detalhes?.itens.map((i) => i.id) || []))
    }
    setSelectAll(!selectAll)
  }

  const valorSelecionado =
    detalhes?.itens
      .filter((i) => selectedItems.has(i.id))
      .reduce((sum, i) => sum + i.valor, 0) || 0

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (tipoPagamento === 'PIX' && !referenciaPagamento.trim()) return
    if (selectedItems.size === 0) return

    const selectedList = detalhes?.itens.filter((i) => selectedItems.has(i.id)) || []

    const request: RegistrarPagamentoRequest = {
      tipoPagamento,
      referenciaPagamento: referenciaPagamento || undefined,
      observacoes: observacoes || undefined,
      // Only send IDs if not paying all
      ...(selectAll
        ? {}
        : {
            comissaoIds: selectedList.filter((i) => i.tipo === 'COMISSAO').map((i) => i.id),
            presencaIds: selectedList.filter((i) => i.tipo === 'DIARIA').map((i) => i.id),
            bonusIds: selectedList.filter((i) => i.tipo === 'BONUS').map((i) => i.id),
          }),
    }

    pagamentoMutation.mutate(request)
  }

  if (!pendencia) return null

  const getTipoBadge = (tipo: ItemPendente['tipo']) => {
    switch (tipo) {
      case 'COMISSAO':
        return <Badge variant="default" className="text-xs">Comissao</Badge>
      case 'DIARIA':
        return <Badge variant="secondary" className="text-xs">Diaria</Badge>
      case 'BONUS':
        return <Badge className="text-xs bg-amber-100 text-amber-800 border-amber-300">Bonus</Badge>
      default:
        return <Badge variant="outline" className="text-xs">{tipo}</Badge>
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-[600px] max-h-[90vh] overflow-y-auto">
        <form onSubmit={handleSubmit}>
          <DialogHeader>
            <DialogTitle>Registrar Pagamento</DialogTitle>
            <DialogDescription>
              Confirme o pagamento para {pendencia.vendedorNome}
            </DialogDescription>
          </DialogHeader>

          <div className="grid gap-4 py-4">
            {/* PIX Info */}
            {pendencia.temPixCadastrado ? (
              <div className="rounded-lg border p-4">
                <div className="flex items-center justify-between">
                  <div>
                    <p className="text-sm text-muted-foreground">
                      Chave PIX ({pendencia.tipoChavePix})
                    </p>
                    <p className="font-mono text-sm">{pendencia.chavePix}</p>
                  </div>
                  <CopyButton text={pendencia.chavePix || ''} />
                </div>
              </div>
            ) : (
              <div className="rounded-lg border border-yellow-200 bg-yellow-50 p-4 flex items-start gap-2">
                <AlertCircle className="h-4 w-4 text-yellow-600 mt-0.5" />
                <div>
                  <p className="text-sm font-medium text-yellow-800">
                    Chave PIX nao cadastrada
                  </p>
                  <p className="text-xs text-yellow-700">
                    Edite o vendedor para adicionar a chave PIX
                  </p>
                </div>
              </div>
            )}

            {/* Item Selection */}
            <div className="space-y-2">
              <div className="flex items-center justify-between border-b pb-2">
                <button
                  type="button"
                  className="flex items-center gap-2 cursor-pointer hover:opacity-80"
                  onClick={handleToggleAll}
                >
                  <div className={`h-4 w-4 rounded border flex items-center justify-center ${
                    selectAll ? 'bg-primary border-primary' : 'border-input'
                  }`}>
                    {selectAll && <Check className="h-3 w-3 text-primary-foreground" />}
                  </div>
                  <span className="text-sm font-medium">Selecionar todos</span>
                </button>
                <span className="font-bold text-green-600">
                  {formatCurrency(valorSelecionado)}
                </span>
              </div>

              <div className="max-h-[200px] overflow-y-auto space-y-2 pr-2">
                {loadingDetalhes ? (
                  Array.from({ length: 3 }).map((_, i) => (
                    <div key={i} className="flex items-center gap-3 p-2 rounded border">
                      <Skeleton className="h-4 w-4" />
                      <Skeleton className="h-6 w-16" />
                      <Skeleton className="h-4 flex-1" />
                      <Skeleton className="h-4 w-20" />
                    </div>
                  ))
                ) : detalhes?.itens.length === 0 ? (
                  <div className="text-center py-4 text-muted-foreground">
                    Nenhum item pendente
                  </div>
                ) : (
                  detalhes?.itens.map((item) => (
                    <button
                      type="button"
                      key={item.id}
                      className={`flex items-center gap-3 p-2 rounded border cursor-pointer transition-colors w-full text-left ${
                        selectedItems.has(item.id)
                          ? 'bg-primary/5 border-primary/20'
                          : 'hover:bg-muted/50'
                      }`}
                      onClick={() => handleToggleItem(item.id)}
                    >
                      <div className={`h-4 w-4 rounded border flex items-center justify-center flex-shrink-0 ${
                        selectedItems.has(item.id) ? 'bg-primary border-primary' : 'border-input'
                      }`}>
                        {selectedItems.has(item.id) && <Check className="h-3 w-3 text-primary-foreground" />}
                      </div>
                      {getTipoBadge(item.tipo)}
                      <div className="flex-1 min-w-0">
                        <p className="text-sm truncate">{item.descricao}</p>
                        <p className="text-xs text-muted-foreground">
                          {format(new Date(item.dataReferencia), 'dd/MM/yyyy', { locale: ptBR })}
                        </p>
                      </div>
                      <span className="font-medium whitespace-nowrap">
                        {formatCurrency(item.valor)}
                      </span>
                    </button>
                  ))
                )}
              </div>
            </div>

            {/* Payment Type */}
            <div className="grid gap-2">
              <Label>Tipo de Pagamento *</Label>
              <RadioGroup
                value={tipoPagamento}
                onValueChange={(value) => setTipoPagamento(value as TipoPagamento)}
                className="flex gap-4"
              >
                <label className="flex items-center gap-2 cursor-pointer">
                  <RadioGroupItem value="PIX" id="pix" />
                  <span className="text-sm">PIX</span>
                </label>
                <label className="flex items-center gap-2 cursor-pointer">
                  <RadioGroupItem value="DINHEIRO" id="dinheiro" />
                  <span className="text-sm">Dinheiro</span>
                </label>
              </RadioGroup>
            </div>

            {/* Payment Reference */}
            <div className="grid gap-2">
              <Label htmlFor="referencia">
                {tipoPagamento === 'PIX' ? 'Referencia PIX *' : 'Referencia (opcional)'}
              </Label>
              <Input
                id="referencia"
                value={referenciaPagamento}
                onChange={(e) => setReferenciaPagamento(e.target.value)}
                placeholder={
                  tipoPagamento === 'PIX'
                    ? 'ID da transacao PIX, E2E, etc.'
                    : 'Numero do recibo, etc.'
                }
                required={tipoPagamento === 'PIX'}
              />
            </div>

            {/* Notes */}
            <div className="grid gap-2">
              <Label htmlFor="observacoes">Observacoes (opcional)</Label>
              <Textarea
                id="observacoes"
                value={observacoes}
                onChange={(e) => setObservacoes(e.target.value)}
                placeholder="Notas sobre o pagamento..."
                rows={2}
              />
            </div>
          </div>

          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
              Cancelar
            </Button>
            <Button
              type="submit"
              disabled={
                pagamentoMutation.isPending ||
                selectedItems.size === 0 ||
                (tipoPagamento === 'PIX' && !referenciaPagamento.trim())
              }
            >
              {pagamentoMutation.isPending ? 'Processando...' : `Pagar ${formatCurrency(valorSelecionado)}`}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

export default function PagamentosPage() {
  const { currentTenant } = useTenantStore()
  const [selectedPendencia, setSelectedPendencia] = useState<PendenciasPagamento | null>(null)
  const [dialogOpen, setDialogOpen] = useState(false)

  // Fetch pending payments
  const { data: pendencias, isLoading: loadingPendencias } = useQuery({
    queryKey: ['pagamentos', 'pendencias', currentTenant?.id],
    queryFn: () => pagamentosService.listPendencias(currentTenant!.id),
    enabled: !!currentTenant,
  })

  // Fetch payment history
  const { data: historico, isLoading: loadingHistorico } = useQuery({
    queryKey: ['pagamentos', 'historico', currentTenant?.id],
    queryFn: () => pagamentosService.listHistorico(currentTenant!.id),
    enabled: !!currentTenant,
  })

  const handlePagar = (pendencia: PendenciasPagamento) => {
    setSelectedPendencia(pendencia)
    setDialogOpen(true)
  }

  // Calculate totals
  const totals = pendencias?.reduce(
    (acc, p) => ({
      comissoes: acc.comissoes + p.valorComissoes,
      diarias: acc.diarias + p.valorDiarias,
      bonus: acc.bonus + (p.valorBonus || 0),
      total: acc.total + p.valorTotal,
    }),
    { comissoes: 0, diarias: 0, bonus: 0, total: 0 }
  ) || { comissoes: 0, diarias: 0, bonus: 0, total: 0 }

  if (!currentTenant) {
    return (
      <div className="flex h-[50vh] items-center justify-center">
        <p className="text-muted-foreground">Selecione um tenant para continuar</p>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-3xl font-bold">Pagamentos</h1>
        <p className="text-muted-foreground">
          Gerencie os pagamentos de comissoes e diarias dos vendedores
        </p>
      </div>

      {/* Summary Cards */}
      <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">Total Pendente</CardTitle>
            <DollarSign className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold text-orange-600">
              {formatCurrency(totals.total)}
            </div>
            <p className="text-xs text-muted-foreground">
              {pendencias?.length || 0} vendedores com pendencias
            </p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">Comissoes Pendentes</CardTitle>
            <CreditCard className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold text-blue-600">
              {formatCurrency(totals.comissoes)}
            </div>
            <p className="text-xs text-muted-foreground">
              Comissoes aprovadas aguardando
            </p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">Diarias Pendentes</CardTitle>
            <Wallet className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold text-purple-600">
              {formatCurrency(totals.diarias)}
            </div>
            <p className="text-xs text-muted-foreground">
              Diarias nao pagas
            </p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">Bonus Pendentes</CardTitle>
            <Award className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold text-amber-600">
              {formatCurrency(totals.bonus)}
            </div>
            <p className="text-xs text-muted-foreground">
              Bonus aprovados aguardando
            </p>
          </CardContent>
        </Card>
      </div>

      <Tabs defaultValue="pendencias" className="space-y-4">
        <TabsList>
          <TabsTrigger value="pendencias" className="gap-2">
            <Wallet className="h-4 w-4" />
            Pendencias
          </TabsTrigger>
          <TabsTrigger value="historico" className="gap-2">
            <History className="h-4 w-4" />
            Historico
          </TabsTrigger>
        </TabsList>

        {/* Pending Payments Tab */}
        <TabsContent value="pendencias">
          <div className="rounded-md border">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Vendedor</TableHead>
                  <TableHead>Chave PIX</TableHead>
                  <TableHead className="text-right">Comissoes</TableHead>
                  <TableHead className="text-right">Diarias</TableHead>
                  <TableHead className="text-right">Bonus</TableHead>
                  <TableHead className="text-right">Total</TableHead>
                  <TableHead className="w-[100px]"></TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {loadingPendencias ? (
                  Array.from({ length: 5 }).map((_, i) => (
                    <TableRow key={i}>
                      <TableCell><Skeleton className="h-4 w-32" /></TableCell>
                      <TableCell><Skeleton className="h-4 w-40" /></TableCell>
                      <TableCell><Skeleton className="h-4 w-24" /></TableCell>
                      <TableCell><Skeleton className="h-4 w-24" /></TableCell>
                      <TableCell><Skeleton className="h-4 w-24" /></TableCell>
                      <TableCell><Skeleton className="h-4 w-24" /></TableCell>
                      <TableCell><Skeleton className="h-8 w-20" /></TableCell>
                    </TableRow>
                  ))
                ) : pendencias?.length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={7} className="h-24 text-center">
                      <div className="flex flex-col items-center gap-2">
                        <Check className="h-8 w-8 text-green-500" />
                        <p className="text-muted-foreground">
                          Nenhuma pendencia de pagamento
                        </p>
                      </div>
                    </TableCell>
                  </TableRow>
                ) : (
                  pendencias?.map((pendencia) => (
                    <TableRow key={pendencia.vendedorId}>
                      <TableCell>
                        <div>
                          <p className="font-medium">{pendencia.vendedorNome}</p>
                          {pendencia.vendedorEmail && (
                            <p className="text-xs text-muted-foreground">{pendencia.vendedorEmail}</p>
                          )}
                        </div>
                      </TableCell>
                      <TableCell>
                        {pendencia.temPixCadastrado ? (
                          <div className="flex items-center gap-1">
                            <Badge variant="outline" className="text-xs">
                              {pendencia.tipoChavePix}
                            </Badge>
                            <span className="font-mono text-xs truncate max-w-[150px]">
                              {pendencia.chavePix}
                            </span>
                            <CopyButton text={pendencia.chavePix || ''} />
                          </div>
                        ) : (
                          <Badge variant="destructive" className="text-xs">
                            Nao cadastrado
                          </Badge>
                        )}
                      </TableCell>
                      <TableCell className="text-right">
                        <span className={pendencia.valorComissoes > 0 ? 'text-blue-600 font-medium' : 'text-muted-foreground'}>
                          {formatCurrency(pendencia.valorComissoes)}
                        </span>
                        {pendencia.qtdComissoes > 0 && (
                          <span className="text-xs text-muted-foreground ml-1">
                            ({pendencia.qtdComissoes})
                          </span>
                        )}
                      </TableCell>
                      <TableCell className="text-right">
                        <span className={pendencia.valorDiarias > 0 ? 'text-purple-600 font-medium' : 'text-muted-foreground'}>
                          {formatCurrency(pendencia.valorDiarias)}
                        </span>
                        {pendencia.qtdDiarias > 0 && (
                          <span className="text-xs text-muted-foreground ml-1">
                            ({pendencia.qtdDiarias})
                          </span>
                        )}
                      </TableCell>
                      <TableCell className="text-right">
                        <span className={(pendencia.valorBonus || 0) > 0 ? 'text-amber-600 font-medium' : 'text-muted-foreground'}>
                          {formatCurrency(pendencia.valorBonus || 0)}
                        </span>
                        {(pendencia.qtdBonus || 0) > 0 && (
                          <span className="text-xs text-muted-foreground ml-1">
                            ({pendencia.qtdBonus})
                          </span>
                        )}
                      </TableCell>
                      <TableCell className="text-right">
                        <span className="text-lg font-bold text-green-600">
                          {formatCurrency(pendencia.valorTotal)}
                        </span>
                      </TableCell>
                      <TableCell>
                        <Button size="sm" onClick={() => handlePagar(pendencia)}>
                          Pagar
                        </Button>
                      </TableCell>
                    </TableRow>
                  ))
                )}
              </TableBody>
            </Table>
          </div>
        </TabsContent>

        {/* Payment History Tab */}
        <TabsContent value="historico">
          <div className="rounded-md border">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Data</TableHead>
                  <TableHead>Vendedor</TableHead>
                  <TableHead>Tipo</TableHead>
                  <TableHead>Referencia</TableHead>
                  <TableHead className="text-right">Comissoes</TableHead>
                  <TableHead className="text-right">Diarias</TableHead>
                  <TableHead className="text-right">Bonus</TableHead>
                  <TableHead className="text-right">Total</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {loadingHistorico ? (
                  Array.from({ length: 5 }).map((_, i) => (
                    <TableRow key={i}>
                      <TableCell><Skeleton className="h-4 w-24" /></TableCell>
                      <TableCell><Skeleton className="h-4 w-32" /></TableCell>
                      <TableCell><Skeleton className="h-4 w-16" /></TableCell>
                      <TableCell><Skeleton className="h-4 w-32" /></TableCell>
                      <TableCell><Skeleton className="h-4 w-24" /></TableCell>
                      <TableCell><Skeleton className="h-4 w-24" /></TableCell>
                      <TableCell><Skeleton className="h-4 w-24" /></TableCell>
                      <TableCell><Skeleton className="h-4 w-24" /></TableCell>
                    </TableRow>
                  ))
                ) : historico?.length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={8} className="h-24 text-center">
                      <div className="flex flex-col items-center gap-2">
                        <History className="h-8 w-8 text-muted-foreground" />
                        <p className="text-muted-foreground">
                          Nenhum pagamento registrado
                        </p>
                      </div>
                    </TableCell>
                  </TableRow>
                ) : (
                  historico?.map((pagamento) => (
                    <TableRow key={pagamento.id}>
                      <TableCell>
                        <div>
                          <p className="font-medium">
                            {format(new Date(pagamento.createdAt), 'dd/MM/yyyy', { locale: ptBR })}
                          </p>
                          <p className="text-xs text-muted-foreground">
                            {format(new Date(pagamento.createdAt), 'HH:mm', { locale: ptBR })}
                          </p>
                        </div>
                      </TableCell>
                      <TableCell>
                        <p className="font-medium">{pagamento.vendedorNome}</p>
                      </TableCell>
                      <TableCell>
                        <Badge variant={pagamento.tipoPagamento === 'PIX' ? 'default' : 'secondary'}>
                          {pagamento.tipoPagamento === 'PIX' ? 'PIX' : 'Dinheiro'}
                        </Badge>
                      </TableCell>
                      <TableCell>
                        <span className="font-mono text-sm">
                          {pagamento.referenciaPagamento || '-'}
                        </span>
                      </TableCell>
                      <TableCell className="text-right">
                        <span className={pagamento.valorComissoes > 0 ? 'text-blue-600' : 'text-muted-foreground'}>
                          {formatCurrency(pagamento.valorComissoes)}
                        </span>
                        {pagamento.qtdComissoes > 0 && (
                          <span className="text-xs text-muted-foreground ml-1">
                            ({pagamento.qtdComissoes})
                          </span>
                        )}
                      </TableCell>
                      <TableCell className="text-right">
                        <span className={pagamento.valorDiarias > 0 ? 'text-purple-600' : 'text-muted-foreground'}>
                          {formatCurrency(pagamento.valorDiarias)}
                        </span>
                        {pagamento.qtdDiarias > 0 && (
                          <span className="text-xs text-muted-foreground ml-1">
                            ({pagamento.qtdDiarias})
                          </span>
                        )}
                      </TableCell>
                      <TableCell className="text-right">
                        <span className={(pagamento.valorBonus || 0) > 0 ? 'text-amber-600' : 'text-muted-foreground'}>
                          {formatCurrency(pagamento.valorBonus || 0)}
                        </span>
                        {(pagamento.qtdBonus || 0) > 0 && (
                          <span className="text-xs text-muted-foreground ml-1">
                            ({pagamento.qtdBonus})
                          </span>
                        )}
                      </TableCell>
                      <TableCell className="text-right">
                        <span className="font-bold text-green-600">
                          {formatCurrency(pagamento.valorTotal)}
                        </span>
                      </TableCell>
                    </TableRow>
                  ))
                )}
              </TableBody>
            </Table>
          </div>
        </TabsContent>
      </Tabs>

      <PagamentoDialog
        pendencia={selectedPendencia}
        open={dialogOpen}
        onOpenChange={setDialogOpen}
      />
    </div>
  )
}
