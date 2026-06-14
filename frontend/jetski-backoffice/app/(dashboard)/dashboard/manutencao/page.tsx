'use client'

import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Plus, Search, MoreHorizontal, Wrench, Edit, CheckCircle, Play, Pause, XCircle, Clock, DollarSign, CalendarDays } from 'lucide-react'
import { useTenantStore } from '@/lib/store/tenant-store'
import { manutencoesService, jetskisService, despesasManutencaoService } from '@/lib/api/services'
import type { Manutencao, ManutencaoCreateRequest, ManutencaoFinishRequest, ManutencaoStatus, ManutencaoTipo, ManutencaoPrioridade, GerarDespesaManutencaoRequest } from '@/lib/api/types'
import { formatDate } from '@/lib/utils'
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
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Label } from '@/components/ui/label'
import { Skeleton } from '@/components/ui/skeleton'
import { Textarea } from '@/components/ui/textarea'

// Tipo extendido para o formulário de edição
interface ManutencaoFormData extends ManutencaoCreateRequest {
  valorPecas?: number
  valorMaoObra?: number
  diagnostico?: string
  solucao?: string
}

const statusConfig: Record<ManutencaoStatus, { label: string; variant: 'default' | 'success' | 'warning' | 'destructive' }> = {
  ABERTA: { label: 'Aberta', variant: 'warning' },
  EM_ANDAMENTO: { label: 'Em andamento', variant: 'default' },
  AGUARDANDO_PECAS: { label: 'Aguardando Peças', variant: 'warning' },
  CONCLUIDA: { label: 'Concluída', variant: 'success' },
  CANCELADA: { label: 'Cancelada', variant: 'destructive' },
}

const tipoConfig: Record<ManutencaoTipo, { label: string }> = {
  PREVENTIVA: { label: 'Preventiva' },
  CORRETIVA: { label: 'Corretiva' },
}

const prioridadeConfig: Record<ManutencaoPrioridade, { label: string; variant: 'default' | 'warning' | 'destructive' }> = {
  BAIXA: { label: 'Baixa', variant: 'default' },
  MEDIA: { label: 'Média', variant: 'default' },
  ALTA: { label: 'Alta', variant: 'warning' },
  URGENTE: { label: 'Urgente', variant: 'destructive' },
}

function ManutencaoFormDialog({
  manutencao,
  open,
  onOpenChange,
}: {
  manutencao?: Manutencao
  open: boolean
  onOpenChange: (open: boolean) => void
}) {
  const queryClient = useQueryClient()
  const { currentTenant } = useTenantStore()
  const isEditing = !!manutencao

  const [formData, setFormData] = useState<ManutencaoFormData>({
    jetskiId: manutencao?.jetskiId || '',
    tipo: manutencao?.tipo || 'CORRETIVA',
    prioridade: manutencao?.prioridade || 'MEDIA',
    descricaoProblema: manutencao?.descricaoProblema || '',
    observacoes: manutencao?.observacoes || '',
    valorPecas: manutencao?.valorPecas || 0,
    valorMaoObra: manutencao?.valorMaoObra || 0,
    diagnostico: manutencao?.diagnostico || '',
    solucao: manutencao?.solucao || '',
  })

  const { data: jetskis } = useQuery({
    queryKey: ['jetskis-manutencao', currentTenant?.id],
    queryFn: () => jetskisService.list(),
    enabled: !!currentTenant && open,
  })

  const createMutation = useMutation({
    mutationFn: (data: ManutencaoCreateRequest) => manutencoesService.create(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['manutencoes'] })
      queryClient.invalidateQueries({ queryKey: ['jetskis'] })
      onOpenChange(false)
    },
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: string; data: Partial<ManutencaoCreateRequest> }) =>
      manutencoesService.update(id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['manutencoes'] })
      queryClient.invalidateQueries({ queryKey: ['jetskis'] })
      onOpenChange(false)
    },
  })

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (manutencao) {
      updateMutation.mutate({ id: manutencao.id, data: formData })
    } else {
      createMutation.mutate(formData)
    }
  }

  const isLoading = createMutation.isPending || updateMutation.isPending

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-[500px]">
        <form onSubmit={handleSubmit}>
          <DialogHeader>
            <DialogTitle>{manutencao ? 'Editar OS' : 'Nova Ordem de Serviço'}</DialogTitle>
            <DialogDescription>
              {manutencao ? 'Atualize os dados da manutenção' : 'Abra uma nova ordem de serviço'}
            </DialogDescription>
          </DialogHeader>

          <div className="grid gap-4 py-4">
            <div className="grid gap-2">
              <Label htmlFor="jetski">Jetski *</Label>
              <Select
                value={formData.jetskiId}
                onValueChange={(value) => setFormData({ ...formData, jetskiId: value })}
                disabled={!!manutencao}
              >
                <SelectTrigger>
                  <SelectValue placeholder="Selecione um jetski" />
                </SelectTrigger>
                <SelectContent>
                  {jetskis?.map((jetski) => (
                    <SelectItem key={jetski.id} value={jetski.id}>
                      {jetski.serie} - {jetski.modelo?.nome}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div className="grid gap-2">
                <Label htmlFor="tipo">Tipo *</Label>
                <Select
                  value={formData.tipo}
                  onValueChange={(value: ManutencaoTipo) => setFormData({ ...formData, tipo: value })}
                >
                  <SelectTrigger>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="PREVENTIVA">Preventiva</SelectItem>
                    <SelectItem value="CORRETIVA">Corretiva</SelectItem>
                  </SelectContent>
                </Select>
              </div>

              <div className="grid gap-2">
                <Label htmlFor="prioridade">Prioridade *</Label>
                <Select
                  value={formData.prioridade}
                  onValueChange={(value: ManutencaoPrioridade) => setFormData({ ...formData, prioridade: value })}
                >
                  <SelectTrigger>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="BAIXA">Baixa</SelectItem>
                    <SelectItem value="MEDIA">Média</SelectItem>
                    <SelectItem value="ALTA">Alta</SelectItem>
                    <SelectItem value="URGENTE">Urgente</SelectItem>
                  </SelectContent>
                </Select>
              </div>
            </div>

            <div className="grid gap-2">
              <Label htmlFor="descricaoProblema">Descrição do Problema *</Label>
              <Input
                id="descricaoProblema"
                value={formData.descricaoProblema}
                onChange={(e) => setFormData({ ...formData, descricaoProblema: e.target.value })}
                placeholder="Descreva o problema ou serviço a ser realizado"
                required
              />
            </div>

            <div className="grid gap-2">
              <Label htmlFor="observacoes">Observações</Label>
              <Input
                id="observacoes"
                value={formData.observacoes || ''}
                onChange={(e) => setFormData({ ...formData, observacoes: e.target.value })}
                placeholder="Observações adicionais"
              />
            </div>

            {/* Campos extras para edição */}
            {isEditing && (
              <>
                <div className="grid grid-cols-2 gap-4">
                  <div className="grid gap-2">
                    <Label htmlFor="valorPecas">Valor Peças (R$)</Label>
                    <Input
                      id="valorPecas"
                      type="number"
                      step="0.01"
                      min="0"
                      value={formData.valorPecas || ''}
                      onChange={(e) => setFormData({ ...formData, valorPecas: parseFloat(e.target.value) || 0 })}
                      placeholder="0,00"
                    />
                  </div>
                  <div className="grid gap-2">
                    <Label htmlFor="valorMaoObra">Valor Mão de Obra (R$)</Label>
                    <Input
                      id="valorMaoObra"
                      type="number"
                      step="0.01"
                      min="0"
                      value={formData.valorMaoObra || ''}
                      onChange={(e) => setFormData({ ...formData, valorMaoObra: parseFloat(e.target.value) || 0 })}
                      placeholder="0,00"
                    />
                  </div>
                </div>

                <div className="grid gap-2">
                  <Label htmlFor="diagnostico">Diagnóstico</Label>
                  <Textarea
                    id="diagnostico"
                    value={formData.diagnostico || ''}
                    onChange={(e) => setFormData({ ...formData, diagnostico: e.target.value })}
                    placeholder="Descreva o diagnóstico técnico"
                    rows={2}
                  />
                </div>

                <div className="grid gap-2">
                  <Label htmlFor="solucao">Solução Aplicada</Label>
                  <Textarea
                    id="solucao"
                    value={formData.solucao || ''}
                    onChange={(e) => setFormData({ ...formData, solucao: e.target.value })}
                    placeholder="Descreva a solução aplicada"
                    rows={2}
                  />
                </div>
              </>
            )}
          </div>

          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
              Cancelar
            </Button>
            <Button type="submit" disabled={isLoading}>
              {isLoading ? 'Salvando...' : manutencao ? 'Salvar' : 'Abrir OS'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

function ConcluirDialog({
  manutencao,
  open,
  onOpenChange,
}: {
  manutencao: Manutencao | null
  open: boolean
  onOpenChange: (open: boolean) => void
}) {
  const queryClient = useQueryClient()
  const [formData, setFormData] = useState<ManutencaoFinishRequest>({
    horimetroFechamento: 0,
    valorPecas: 0,
    valorMaoObra: 0,
    observacoesFinais: '',
  })

  const finishMutation = useMutation({
    mutationFn: ({ id, data }: { id: string; data: ManutencaoFinishRequest }) =>
      manutencoesService.finish(id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['manutencoes'] })
      queryClient.invalidateQueries({ queryKey: ['jetskis'] })
      onOpenChange(false)
    },
  })

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (manutencao) {
      finishMutation.mutate({ id: manutencao.id, data: formData })
    }
  }

  // Reset form when dialog opens with new manutencao
  const horimetroSugerido = manutencao?.horimetroAbertura ? Number(manutencao.horimetroAbertura) + 1 : 0

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-[450px]">
        <form onSubmit={handleSubmit}>
          <DialogHeader>
            <DialogTitle>Concluir OS</DialogTitle>
            <DialogDescription>
              Informe os dados de conclusão da manutenção do jetski {manutencao?.jetski?.serie}
            </DialogDescription>
          </DialogHeader>

          <div className="grid gap-4 py-4">
            <div className="grid gap-2">
              <Label htmlFor="horimetroFechamento">Horímetro de Fechamento *</Label>
              <Input
                id="horimetroFechamento"
                type="number"
                step="0.1"
                value={formData.horimetroFechamento || ''}
                onChange={(e) => setFormData({ ...formData, horimetroFechamento: parseFloat(e.target.value) || 0 })}
                placeholder={`Horímetro atual (abertura: ${manutencao?.horimetroAbertura || 'N/A'})`}
                required
              />
              <p className="text-xs text-muted-foreground">
                Horímetro na abertura: {manutencao?.horimetroAbertura || 'N/A'}
              </p>
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div className="grid gap-2">
                <Label htmlFor="valorPecas">Valor Peças (R$)</Label>
                <Input
                  id="valorPecas"
                  type="number"
                  step="0.01"
                  value={formData.valorPecas || ''}
                  onChange={(e) => setFormData({ ...formData, valorPecas: parseFloat(e.target.value) || 0 })}
                  placeholder="0.00"
                />
              </div>
              <div className="grid gap-2">
                <Label htmlFor="valorMaoObra">Valor Mão de Obra (R$)</Label>
                <Input
                  id="valorMaoObra"
                  type="number"
                  step="0.01"
                  value={formData.valorMaoObra || ''}
                  onChange={(e) => setFormData({ ...formData, valorMaoObra: parseFloat(e.target.value) || 0 })}
                  placeholder="0.00"
                />
              </div>
            </div>

            <div className="grid gap-2">
              <Label htmlFor="observacoesFinais">Observações Finais</Label>
              <Input
                id="observacoesFinais"
                value={formData.observacoesFinais || ''}
                onChange={(e) => setFormData({ ...formData, observacoesFinais: e.target.value })}
                placeholder="Descreva o serviço realizado"
              />
            </div>
          </div>

          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
              Cancelar
            </Button>
            <Button type="submit" disabled={finishMutation.isPending}>
              {finishMutation.isPending ? 'Concluindo...' : 'Concluir OS'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

function GerarDespesaDialog({
  manutencao,
  open,
  onOpenChange,
}: {
  manutencao: Manutencao | null
  open: boolean
  onOpenChange: (open: boolean) => void
}) {
  const queryClient = useQueryClient()
  const [formData, setFormData] = useState<GerarDespesaManutencaoRequest>({
    numeroParcelas: 1,
    primeiroVencimento: new Date().toISOString().split('T')[0],
    observacoes: '',
  })
  const [hasExpense, setHasExpense] = useState(false)
  const [checkingExpense, setCheckingExpense] = useState(false)

  // Check if expense already exists when dialog opens
  const checkExpense = async () => {
    if (!manutencao) return
    setCheckingExpense(true)
    try {
      const exists = await despesasManutencaoService.temDespesa(manutencao.id)
      setHasExpense(exists)
    } catch {
      setHasExpense(false)
    } finally {
      setCheckingExpense(false)
    }
  }

  // Check when dialog opens
  if (open && manutencao && !checkingExpense && !hasExpense) {
    checkExpense()
  }

  const gerarMutation = useMutation({
    mutationFn: ({ osId, request }: { osId: string; request: GerarDespesaManutencaoRequest }) =>
      despesasManutencaoService.gerarDespesas(osId, request),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['manutencoes'] })
      queryClient.invalidateQueries({ queryKey: ['despesas-manutencao'] })
      onOpenChange(false)
      setHasExpense(true)
    },
  })

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (manutencao) {
      gerarMutation.mutate({ osId: manutencao.id, request: formData })
    }
  }

  const valorTotal = (manutencao?.valorPecas || 0) + (manutencao?.valorMaoObra || 0)
  const valorParcela = formData.numeroParcelas > 0 ? valorTotal / formData.numeroParcelas : 0

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-[450px]">
        <form onSubmit={handleSubmit}>
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2">
              <DollarSign className="h-5 w-5" />
              Gerar Despesa de Manutencao
            </DialogTitle>
            <DialogDescription>
              Gere despesas parceladas para a OS do jetski {manutencao?.jetski?.serie}
            </DialogDescription>
          </DialogHeader>

          {checkingExpense ? (
            <div className="py-8 text-center">
              <p className="text-muted-foreground">Verificando...</p>
            </div>
          ) : hasExpense ? (
            <div className="py-8 text-center">
              <p className="text-amber-600 font-medium">Esta OS ja possui despesas geradas.</p>
              <p className="text-sm text-muted-foreground mt-2">
                Acesse a pagina de Despesas de Manutencao para gerencia-las.
              </p>
            </div>
          ) : valorTotal <= 0 ? (
            <div className="py-8 text-center">
              <p className="text-amber-600 font-medium">Esta OS nao possui valores informados.</p>
              <p className="text-sm text-muted-foreground mt-2">
                Edite a OS e informe os valores de pecas e mao de obra antes de gerar a despesa.
              </p>
            </div>
          ) : (
            <>
              <div className="grid gap-4 py-4">
                <div className="bg-muted/50 rounded-lg p-3">
                  <p className="text-sm text-muted-foreground">Valor Total da OS</p>
                  <p className="text-2xl font-bold">
                    R$ {valorTotal.toFixed(2).replace('.', ',')}
                  </p>
                  <div className="flex gap-4 mt-1 text-xs text-muted-foreground">
                    <span>Pecas: R$ {(manutencao?.valorPecas || 0).toFixed(2).replace('.', ',')}</span>
                    <span>Mao de Obra: R$ {(manutencao?.valorMaoObra || 0).toFixed(2).replace('.', ',')}</span>
                  </div>
                </div>

                <div className="grid gap-2">
                  <Label htmlFor="numeroParcelas">Numero de Parcelas *</Label>
                  <Select
                    value={String(formData.numeroParcelas)}
                    onValueChange={(value) => setFormData({ ...formData, numeroParcelas: parseInt(value) })}
                  >
                    <SelectTrigger>
                      <SelectValue placeholder="Selecione" />
                    </SelectTrigger>
                    <SelectContent>
                      {[1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12].map((n) => (
                        <SelectItem key={n} value={String(n)}>
                          {n === 1 ? 'A vista' : `${n}x de R$ ${(valorTotal / n).toFixed(2).replace('.', ',')}`}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>

                <div className="grid gap-2">
                  <Label htmlFor="primeiroVencimento">Data do Primeiro Vencimento *</Label>
                  <Input
                    id="primeiroVencimento"
                    type="date"
                    value={formData.primeiroVencimento}
                    onChange={(e) => setFormData({ ...formData, primeiroVencimento: e.target.value })}
                    required
                  />
                </div>

                <div className="grid gap-2">
                  <Label htmlFor="observacoes">Observacoes</Label>
                  <Input
                    id="observacoes"
                    value={formData.observacoes || ''}
                    onChange={(e) => setFormData({ ...formData, observacoes: e.target.value })}
                    placeholder="Observacoes sobre a despesa"
                  />
                </div>

                {formData.numeroParcelas > 1 && (
                  <div className="bg-blue-50 dark:bg-blue-950 rounded-lg p-3 text-sm">
                    <p className="font-medium text-blue-700 dark:text-blue-300">Resumo do Parcelamento</p>
                    <p className="text-blue-600 dark:text-blue-400 mt-1">
                      {formData.numeroParcelas} parcelas de R$ {valorParcela.toFixed(2).replace('.', ',')} cada,
                      com vencimentos mensais a partir de {formData.primeiroVencimento}.
                    </p>
                  </div>
                )}
              </div>

              <DialogFooter>
                <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
                  Cancelar
                </Button>
                <Button type="submit" disabled={gerarMutation.isPending}>
                  {gerarMutation.isPending ? 'Gerando...' : 'Gerar Despesas'}
                </Button>
              </DialogFooter>
            </>
          )}

          {(hasExpense || valorTotal <= 0) && (
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
                Fechar
              </Button>
            </DialogFooter>
          )}
        </form>
      </DialogContent>
    </Dialog>
  )
}

export default function ManutencaoPage() {
  const { currentTenant } = useTenantStore()
  const queryClient = useQueryClient()

  const [search, setSearch] = useState('')
  const [statusFilter, setStatusFilter] = useState<string>('all')
  const [dialogOpen, setDialogOpen] = useState(false)
  const [editingManutencao, setEditingManutencao] = useState<Manutencao | undefined>()
  const [concluirDialogOpen, setConcluirDialogOpen] = useState(false)
  const [manutencaoToConcluir, setManutencaoToConcluir] = useState<Manutencao | null>(null)
  const [gerarDespesaDialogOpen, setGerarDespesaDialogOpen] = useState(false)
  const [manutencaoToGerarDespesa, setManutencaoToGerarDespesa] = useState<Manutencao | null>(null)

  const { data: manutencoes, isLoading } = useQuery({
    queryKey: ['manutencoes', currentTenant?.id, statusFilter],
    queryFn: () =>
      manutencoesService.list({
        status: statusFilter !== 'all' ? statusFilter as ManutencaoStatus : undefined,
      }),
    enabled: !!currentTenant,
  })

  // Mutation para iniciar trabalho
  const startMutation = useMutation({
    mutationFn: (id: string) => manutencoesService.start(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['manutencoes'] })
      queryClient.invalidateQueries({ queryKey: ['jetskis'] })
    },
  })

  // Mutation para aguardar peças
  const waitForPartsMutation = useMutation({
    mutationFn: (id: string) => manutencoesService.waitForParts(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['manutencoes'] })
    },
  })

  // Mutation para retomar trabalho
  const resumeMutation = useMutation({
    mutationFn: (id: string) => manutencoesService.resume(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['manutencoes'] })
    },
  })

  // Mutation para cancelar
  const cancelMutation = useMutation({
    mutationFn: (id: string) => manutencoesService.cancel(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['manutencoes'] })
      queryClient.invalidateQueries({ queryKey: ['jetskis'] })
    },
  })

  const handleEdit = (manutencao: Manutencao) => {
    setEditingManutencao(manutencao)
    setDialogOpen(true)
  }

  const handleConcluir = (manutencao: Manutencao) => {
    setManutencaoToConcluir(manutencao)
    setConcluirDialogOpen(true)
  }

  const handleGerarDespesa = (manutencao: Manutencao) => {
    setManutencaoToGerarDespesa(manutencao)
    setGerarDespesaDialogOpen(true)
  }

  const handleNew = () => {
    setEditingManutencao(undefined)
    setDialogOpen(true)
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
          <h1 className="text-3xl font-bold">Manutenção</h1>
          <p className="text-muted-foreground">Gerencie as ordens de serviço</p>
        </div>
        <Button onClick={handleNew}>
          <Plus className="mr-2 h-4 w-4" />
          Nova OS
        </Button>
      </div>

      <div className="flex gap-4">
        <div className="relative flex-1">
          <Search className="absolute left-2.5 top-2.5 h-4 w-4 text-muted-foreground" />
          <Input
            placeholder="Buscar..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="pl-8"
          />
        </div>
        <Select value={statusFilter} onValueChange={setStatusFilter}>
          <SelectTrigger className="w-48">
            <SelectValue placeholder="Filtrar por status" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">Todas</SelectItem>
            <SelectItem value="ABERTA">Abertas</SelectItem>
            <SelectItem value="EM_ANDAMENTO">Em andamento</SelectItem>
            <SelectItem value="AGUARDANDO_PECAS">Aguardando Peças</SelectItem>
            <SelectItem value="CONCLUIDA">Concluídas</SelectItem>
            <SelectItem value="CANCELADA">Canceladas</SelectItem>
          </SelectContent>
        </Select>
      </div>

      <div className="rounded-md border">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Jetski</TableHead>
              <TableHead>Tipo</TableHead>
              <TableHead>Prioridade</TableHead>
              <TableHead>Descrição</TableHead>
              <TableHead>Abertura</TableHead>
              <TableHead>Conclusão</TableHead>
              <TableHead className="text-right">Valor Total</TableHead>
              <TableHead>Status</TableHead>
              <TableHead className="w-[50px]"></TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {isLoading ? (
              Array.from({ length: 5 }).map((_, i) => (
                <TableRow key={i}>
                  <TableCell><Skeleton className="h-4 w-24" /></TableCell>
                  <TableCell><Skeleton className="h-4 w-20" /></TableCell>
                  <TableCell><Skeleton className="h-4 w-16" /></TableCell>
                  <TableCell><Skeleton className="h-4 w-40" /></TableCell>
                  <TableCell><Skeleton className="h-4 w-24" /></TableCell>
                  <TableCell><Skeleton className="h-4 w-24" /></TableCell>
                  <TableCell><Skeleton className="h-4 w-20" /></TableCell>
                  <TableCell><Skeleton className="h-6 w-24" /></TableCell>
                  <TableCell><Skeleton className="h-8 w-8" /></TableCell>
                </TableRow>
              ))
            ) : manutencoes?.length === 0 ? (
              <TableRow>
                <TableCell colSpan={9} className="h-24 text-center">
                  <div className="flex flex-col items-center gap-2">
                    <Wrench className="h-8 w-8 text-muted-foreground" />
                    <p className="text-muted-foreground">Nenhuma OS encontrada</p>
                  </div>
                </TableCell>
              </TableRow>
            ) : (
              manutencoes?.map((manutencao) => (
                <TableRow key={manutencao.id}>
                  <TableCell className="font-medium">
                    {manutencao.jetski?.serie || '-'}
                  </TableCell>
                  <TableCell>
                    <Badge variant="outline">{tipoConfig[manutencao.tipo].label}</Badge>
                  </TableCell>
                  <TableCell>
                    <Badge variant={prioridadeConfig[manutencao.prioridade].variant}>
                      {prioridadeConfig[manutencao.prioridade].label}
                    </Badge>
                  </TableCell>
                  <TableCell className="max-w-[200px] truncate">
                    {manutencao.descricaoProblema}
                  </TableCell>
                  <TableCell>{formatDate(manutencao.dtAbertura)}</TableCell>
                  <TableCell>
                    {manutencao.dtConclusao ? formatDate(manutencao.dtConclusao) : '-'}
                  </TableCell>
                  <TableCell className="text-right font-medium">
                    {(manutencao.valorTotal && manutencao.valorTotal > 0)
                      ? `R$ ${manutencao.valorTotal.toFixed(2).replace('.', ',')}`
                      : '-'}
                  </TableCell>
                  <TableCell>
                    <Badge variant={statusConfig[manutencao.status].variant}>
                      {statusConfig[manutencao.status].label}
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
                        <DropdownMenuItem onClick={() => handleEdit(manutencao)}>
                          <Edit className="mr-2 h-4 w-4" />
                          Editar
                        </DropdownMenuItem>

                        {/* ABERTA -> Iniciar */}
                        {manutencao.status === 'ABERTA' && (
                          <DropdownMenuItem onClick={() => startMutation.mutate(manutencao.id)}>
                            <Play className="mr-2 h-4 w-4" />
                            Iniciar
                          </DropdownMenuItem>
                        )}

                        {/* EM_ANDAMENTO -> Aguardar Peças ou Concluir */}
                        {manutencao.status === 'EM_ANDAMENTO' && (
                          <>
                            <DropdownMenuItem onClick={() => waitForPartsMutation.mutate(manutencao.id)}>
                              <Clock className="mr-2 h-4 w-4" />
                              Aguardar Peças
                            </DropdownMenuItem>
                            <DropdownMenuItem onClick={() => handleConcluir(manutencao)}>
                              <CheckCircle className="mr-2 h-4 w-4" />
                              Concluir
                            </DropdownMenuItem>
                          </>
                        )}

                        {/* AGUARDANDO_PECAS -> Retomar */}
                        {manutencao.status === 'AGUARDANDO_PECAS' && (
                          <DropdownMenuItem onClick={() => resumeMutation.mutate(manutencao.id)}>
                            <Play className="mr-2 h-4 w-4" />
                            Retomar
                          </DropdownMenuItem>
                        )}

                        {/* CONCLUIDA -> Gerar Despesa */}
                        {manutencao.status === 'CONCLUIDA' && (
                          <DropdownMenuItem onClick={() => handleGerarDespesa(manutencao)}>
                            <DollarSign className="mr-2 h-4 w-4" />
                            Gerar Despesa
                          </DropdownMenuItem>
                        )}

                        {/* Cancelar - disponível para todos exceto já finalizados */}
                        {manutencao.status !== 'CONCLUIDA' && manutencao.status !== 'CANCELADA' && (
                          <DropdownMenuItem
                            onClick={() => cancelMutation.mutate(manutencao.id)}
                            className="text-destructive"
                          >
                            <XCircle className="mr-2 h-4 w-4" />
                            Cancelar
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
      </div>

      <ManutencaoFormDialog
        manutencao={editingManutencao}
        open={dialogOpen}
        onOpenChange={setDialogOpen}
      />

      <ConcluirDialog
        manutencao={manutencaoToConcluir}
        open={concluirDialogOpen}
        onOpenChange={setConcluirDialogOpen}
      />

      <GerarDespesaDialog
        manutencao={manutencaoToGerarDespesa}
        open={gerarDespesaDialogOpen}
        onOpenChange={setGerarDespesaDialogOpen}
      />
    </div>
  )
}
