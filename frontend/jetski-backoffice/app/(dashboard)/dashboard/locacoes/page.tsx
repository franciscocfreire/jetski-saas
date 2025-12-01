'use client'

import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  Plus,
  Search,
  MoreHorizontal,
  Anchor,
  Eye,
  LogOut,
  Clock,
  Ship,
  DollarSign,
  ChevronDown,
  ChevronUp,
  User,
  Timer,
} from 'lucide-react'
import { useTenantStore } from '@/lib/store/tenant-store'
import { locacoesService, jetskisService, clientesService, vendedoresService } from '@/lib/api/services'
import type { Locacao, LocacaoStatus, CheckInWalkInRequest, CheckOutRequest, ModalidadePreco } from '@/lib/api/types'
import { formatDateTime, formatDuration, formatCurrency } from '@/lib/utils'
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
import { Checkbox } from '@/components/ui/checkbox'
import {
  Collapsible,
  CollapsibleContent,
  CollapsibleTrigger,
} from '@/components/ui/collapsible'

const statusConfig: Record<LocacaoStatus, { label: string; variant: 'success' | 'default' | 'destructive' }> = {
  EM_CURSO: { label: 'Em curso', variant: 'success' },
  FINALIZADA: { label: 'Finalizada', variant: 'default' },
  CANCELADA: { label: 'Cancelada', variant: 'destructive' },
}

const modalidadeConfig: Record<ModalidadePreco, { label: string; description: string }> = {
  PRECO_FECHADO: { label: 'Preço Fechado', description: 'Preço por hora ou negociado' },
  DIARIA: { label: 'Diária', description: 'Valor de dia inteiro' },
  MEIA_DIARIA: { label: 'Meia Diária', description: 'Valor de meio período' },
}

const duracaoOptions = [
  { value: 30, label: '30 minutos' },
  { value: 60, label: '1 hora' },
  { value: 90, label: '1h 30min' },
  { value: 120, label: '2 horas' },
  { value: 180, label: '3 horas' },
  { value: 240, label: '4 horas' },
]

// Checklist items for check-in (saída)
const checkInChecklistItems = [
  { id: 'motor_ok', label: 'Motor OK' },
  { id: 'casco_ok', label: 'Casco OK' },
  { id: 'combustivel_ok', label: 'Combustível OK' },
  { id: 'equipamentos_ok', label: 'Equipamentos OK' },
]

// Checklist items for check-out (entrada)
const checkoutChecklistItems = [
  { id: 'motor_ok', label: 'Motor OK' },
  { id: 'casco_ok', label: 'Casco OK' },
  { id: 'limpeza_ok', label: 'Limpeza OK' },
  { id: 'combustivel_verificado', label: 'Combustível verificado' },
  { id: 'equipamentos_ok', label: 'Equipamentos OK' },
]

function CheckInDialog({
  open,
  onOpenChange,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
}) {
  const queryClient = useQueryClient()
  const { currentTenant } = useTenantStore()

  const [formData, setFormData] = useState<CheckInWalkInRequest>({
    jetskiId: '',
    clienteId: undefined,
    vendedorId: undefined,
    horimetroInicio: 0,
    duracaoPrevista: 60,
    modalidadePreco: 'PRECO_FECHADO',
  })

  const [checklist, setChecklist] = useState<Record<string, boolean>>(() =>
    checkInChecklistItems.reduce((acc, item) => ({ ...acc, [item.id]: false }), {})
  )
  const [showAdvanced, setShowAdvanced] = useState(false)

  const { data: jetskis } = useQuery({
    queryKey: ['jetskis-disponiveis', currentTenant?.id],
    queryFn: () => jetskisService.list({ status: 'DISPONIVEL' }),
    enabled: !!currentTenant && open,
  })

  const { data: clientes } = useQuery({
    queryKey: ['clientes-select', currentTenant?.id],
    queryFn: () => clientesService.list(),
    enabled: !!currentTenant && open,
  })

  const { data: vendedores } = useQuery({
    queryKey: ['vendedores-select', currentTenant?.id],
    queryFn: () => vendedoresService.list(),
    enabled: !!currentTenant && open,
  })

  const checkInMutation = useMutation({
    mutationFn: (data: CheckInWalkInRequest) => locacoesService.checkInWalkIn(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['locacoes'] })
      queryClient.invalidateQueries({ queryKey: ['jetskis'] })
      onOpenChange(false)
      // Reset form
      setFormData({
        jetskiId: '',
        clienteId: undefined,
        vendedorId: undefined,
        horimetroInicio: 0,
        duracaoPrevista: 60,
        modalidadePreco: 'PRECO_FECHADO',
      })
      setChecklist(checkInChecklistItems.reduce((acc, item) => ({ ...acc, [item.id]: false }), {}))
    },
  })

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    const checkedItems = Object.entries(checklist)
      .filter(([, checked]) => checked)
      .map(([id]) => id)

    const request: CheckInWalkInRequest = {
      ...formData,
      clienteId: formData.clienteId || undefined,
      vendedorId: formData.vendedorId || undefined,
      checklistSaidaJson: JSON.stringify(checkedItems),
    }
    checkInMutation.mutate(request)
  }

  // Auto-fill horímetro when jetski is selected
  const handleJetskiChange = (jetskiId: string) => {
    const jetski = jetskis?.find(j => j.id === jetskiId)
    setFormData({
      ...formData,
      jetskiId,
      horimetroInicio: jetski?.horimetroAtual || 0,
    })
  }

  const toggleChecklistItem = (id: string) => {
    setChecklist(prev => ({ ...prev, [id]: !prev[id] }))
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-[550px] max-h-[90vh] overflow-y-auto">
        <form onSubmit={handleSubmit}>
          <DialogHeader>
            <DialogTitle>Novo Check-in (Walk-in)</DialogTitle>
            <DialogDescription>
              Inicie uma nova locação para um cliente sem reserva prévia
            </DialogDescription>
          </DialogHeader>

          <div className="grid gap-4 py-4">
            {/* Jetski Selection */}
            <div className="grid gap-2">
              <Label htmlFor="jetski">Jetski *</Label>
              <Select
                value={formData.jetskiId}
                onValueChange={handleJetskiChange}
              >
                <SelectTrigger>
                  <SelectValue placeholder="Selecione um jetski disponível" />
                </SelectTrigger>
                <SelectContent>
                  {jetskis?.filter(j => j.status === 'DISPONIVEL' && j.ativo).map((jetski) => (
                    <SelectItem key={jetski.id} value={jetski.id}>
                      {jetski.serie} - {jetski.modelo?.nome}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            {/* Duration and Hourimeter */}
            <div className="grid grid-cols-2 gap-4">
              <div className="grid gap-2">
                <Label htmlFor="duracao">Duração Prevista *</Label>
                <Select
                  value={formData.duracaoPrevista.toString()}
                  onValueChange={(value) => setFormData({ ...formData, duracaoPrevista: Number(value) })}
                >
                  <SelectTrigger>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {duracaoOptions.map((option) => (
                      <SelectItem key={option.value} value={option.value.toString()}>
                        {option.label}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>

              <div className="grid gap-2">
                <Label htmlFor="horimetro">Horímetro Inicial *</Label>
                <Input
                  id="horimetro"
                  type="number"
                  value={formData.horimetroInicio}
                  onChange={(e) => setFormData({ ...formData, horimetroInicio: Number(e.target.value) })}
                  min={0}
                  step={0.1}
                  required
                />
              </div>
            </div>

            {/* Cliente and Vendedor */}
            <div className="grid grid-cols-2 gap-4">
              <div className="grid gap-2">
                <Label htmlFor="cliente">Cliente</Label>
                <Select
                  value={formData.clienteId || '__none__'}
                  onValueChange={(value) => setFormData({ ...formData, clienteId: value === '__none__' ? undefined : value })}
                >
                  <SelectTrigger>
                    <SelectValue placeholder="Selecionar cliente" />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="__none__">Sem cliente (Walk-in)</SelectItem>
                    {clientes?.map((cliente) => (
                      <SelectItem key={cliente.id} value={cliente.id}>
                        {cliente.nome}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>

              <div className="grid gap-2">
                <Label htmlFor="vendedor">Vendedor</Label>
                <Select
                  value={formData.vendedorId || '__none__'}
                  onValueChange={(value) => setFormData({ ...formData, vendedorId: value === '__none__' ? undefined : value })}
                >
                  <SelectTrigger>
                    <SelectValue placeholder="Selecionar vendedor" />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="__none__">Sem vendedor</SelectItem>
                    {vendedores?.filter(v => v.ativo).map((vendedor) => (
                      <SelectItem key={vendedor.id} value={vendedor.id}>
                        {vendedor.nome}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
            </div>

            {/* Checklist de Saída */}
            <div className="grid gap-2">
              <Label>Checklist de Saída</Label>
              <div className="rounded-lg border p-3 space-y-2">
                {checkInChecklistItems.map((item) => (
                  <label
                    key={item.id}
                    className="flex items-center gap-3 cursor-pointer hover:bg-muted/50 p-2 rounded-md transition-colors"
                  >
                    <Checkbox
                      checked={checklist[item.id]}
                      onCheckedChange={() => toggleChecklistItem(item.id)}
                    />
                    <span className={checklist[item.id] ? 'text-foreground' : 'text-muted-foreground'}>
                      {item.label}
                    </span>
                  </label>
                ))}
              </div>
            </div>

            {/* Advanced Options (Collapsible) */}
            <Collapsible open={showAdvanced} onOpenChange={setShowAdvanced}>
              <CollapsibleTrigger asChild>
                <Button variant="ghost" type="button" className="w-full justify-between">
                  Opções Avançadas
                  {showAdvanced ? <ChevronUp className="h-4 w-4" /> : <ChevronDown className="h-4 w-4" />}
                </Button>
              </CollapsibleTrigger>
              <CollapsibleContent className="space-y-4 pt-2">
                {/* Modalidade de Preço */}
                <div className="grid gap-2">
                  <Label htmlFor="modalidade">Modalidade de Preço</Label>
                  <Select
                    value={formData.modalidadePreco}
                    onValueChange={(value: ModalidadePreco) => setFormData({ ...formData, modalidadePreco: value })}
                  >
                    <SelectTrigger>
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      {Object.entries(modalidadeConfig).map(([key, config]) => (
                        <SelectItem key={key} value={key}>
                          <div className="flex flex-col">
                            <span>{config.label}</span>
                            <span className="text-xs text-muted-foreground">{config.description}</span>
                          </div>
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>

                {/* Valor Negociado */}
                <div className="grid grid-cols-2 gap-4">
                  <div className="grid gap-2">
                    <Label htmlFor="valorNegociado">Valor Negociado (R$)</Label>
                    <Input
                      id="valorNegociado"
                      type="number"
                      value={formData.valorNegociado || ''}
                      onChange={(e) => setFormData({
                        ...formData,
                        valorNegociado: e.target.value ? Number(e.target.value) : undefined
                      })}
                      min={0}
                      step={10}
                      placeholder="Opcional"
                    />
                  </div>

                  <div className="grid gap-2">
                    <Label htmlFor="motivoDesconto">Motivo do Desconto</Label>
                    <Input
                      id="motivoDesconto"
                      value={formData.motivoDesconto || ''}
                      onChange={(e) => setFormData({ ...formData, motivoDesconto: e.target.value || undefined })}
                      placeholder="Ex: Cliente frequente"
                    />
                  </div>
                </div>

                {/* Observações */}
                <div className="grid gap-2">
                  <Label htmlFor="observacoes">Observações</Label>
                  <Input
                    id="observacoes"
                    value={formData.observacoes || ''}
                    onChange={(e) => setFormData({ ...formData, observacoes: e.target.value || undefined })}
                    placeholder="Observações adicionais"
                  />
                </div>
              </CollapsibleContent>
            </Collapsible>
          </div>

          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
              Cancelar
            </Button>
            <Button type="submit" disabled={checkInMutation.isPending || !formData.jetskiId}>
              {checkInMutation.isPending ? 'Iniciando...' : 'Iniciar Locação'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

function CheckOutDialog({
  locacao,
  open,
  onOpenChange,
}: {
  locacao: Locacao
  open: boolean
  onOpenChange: (open: boolean) => void
}) {
  const queryClient = useQueryClient()

  const [checklist, setChecklist] = useState<Record<string, boolean>>(() =>
    checkoutChecklistItems.reduce((acc, item) => ({ ...acc, [item.id]: false }), {})
  )
  const [horimetroFim, setHorimetroFim] = useState(locacao.horimetroInicio || 0)
  const [observacoes, setObservacoes] = useState('')
  const [skipPhotos, setSkipPhotos] = useState(false)

  const toggleChecklistItem = (id: string) => {
    setChecklist(prev => ({ ...prev, [id]: !prev[id] }))
  }

  const allChecked = Object.values(checklist).every(v => v)

  const checkOutMutation = useMutation({
    mutationFn: (data: CheckOutRequest) => locacoesService.checkOut(locacao.id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['locacoes'] })
      queryClient.invalidateQueries({ queryKey: ['jetskis'] })
      onOpenChange(false)
    },
  })

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    const checkedItems = Object.entries(checklist)
      .filter(([, checked]) => checked)
      .map(([id]) => id)

    const request: CheckOutRequest = {
      horimetroFim,
      checklistEntradaJson: JSON.stringify(checkedItems),
      observacoes: observacoes || undefined,
      skipPhotos,
    }
    checkOutMutation.mutate(request)
  }

  // Calculate estimated usage
  const horasUsadas = horimetroFim > locacao.horimetroInicio
    ? (horimetroFim - locacao.horimetroInicio).toFixed(1)
    : '0'

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-[500px]">
        <form onSubmit={handleSubmit}>
          <DialogHeader>
            <DialogTitle>Check-out</DialogTitle>
            <DialogDescription>
              Finalize a locação registrando o horímetro final e verificando os itens
            </DialogDescription>
          </DialogHeader>

          <div className="grid gap-4 py-4">
            {/* Rental Info Summary */}
            <div className="rounded-lg border p-4 space-y-2 bg-muted/30">
              <div className="flex justify-between">
                <span className="text-muted-foreground">Jetski:</span>
                <span className="font-medium">{locacao.jetskiSerie || locacao.jetskiModeloNome}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-muted-foreground">Cliente:</span>
                <span>{locacao.clienteNome || 'Walk-in'}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-muted-foreground">Check-in:</span>
                <span>{formatDateTime(locacao.dataCheckIn)}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-muted-foreground">Horímetro Inicial:</span>
                <span>{locacao.horimetroInicio}h</span>
              </div>
              {locacao.duracaoPrevista && (
                <div className="flex justify-between">
                  <span className="text-muted-foreground">Duração Prevista:</span>
                  <span>{formatDuration(locacao.duracaoPrevista)}</span>
                </div>
              )}
            </div>

            {/* Horímetro Final */}
            <div className="grid gap-2">
              <Label htmlFor="horimetroFim">Horímetro Final *</Label>
              <Input
                id="horimetroFim"
                type="number"
                value={horimetroFim}
                onChange={(e) => setHorimetroFim(Number(e.target.value))}
                min={locacao.horimetroInicio}
                step={0.1}
                required
              />
              {Number(horasUsadas) > 0 && (
                <p className="text-sm text-muted-foreground">
                  Tempo de uso: ~{horasUsadas}h ({Math.round(Number(horasUsadas) * 60)} minutos)
                </p>
              )}
            </div>

            {/* Checklist de Verificação */}
            <div className="grid gap-2">
              <Label>Checklist de Verificação *</Label>
              <div className="rounded-lg border p-3 space-y-2">
                {checkoutChecklistItems.map((item) => (
                  <label
                    key={item.id}
                    className="flex items-center gap-3 cursor-pointer hover:bg-muted/50 p-2 rounded-md transition-colors"
                  >
                    <Checkbox
                      checked={checklist[item.id]}
                      onCheckedChange={() => toggleChecklistItem(item.id)}
                    />
                    <span className={checklist[item.id] ? 'text-foreground' : 'text-muted-foreground'}>
                      {item.label}
                    </span>
                  </label>
                ))}
              </div>
              {!allChecked && (
                <p className="text-xs text-muted-foreground">
                  Marque todos os itens para continuar
                </p>
              )}
            </div>

            {/* Skip Photos Option */}
            <div className="flex items-center space-x-2">
              <Checkbox
                id="skipPhotos"
                checked={skipPhotos}
                onCheckedChange={(checked) => setSkipPhotos(checked === true)}
              />
              <Label htmlFor="skipPhotos" className="text-sm cursor-pointer">
                Pular validação de fotos (adicionar depois)
              </Label>
            </div>

            {/* Observações */}
            <div className="grid gap-2">
              <Label htmlFor="observacoes">Observações</Label>
              <Input
                id="observacoes"
                value={observacoes}
                onChange={(e) => setObservacoes(e.target.value)}
                placeholder="Alguma observação sobre a devolução?"
              />
            </div>
          </div>

          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
              Cancelar
            </Button>
            <Button type="submit" disabled={checkOutMutation.isPending || !allChecked}>
              {checkOutMutation.isPending ? 'Finalizando...' : 'Finalizar Locação'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

function LocacaoDetailDialog({
  locacao,
  open,
  onOpenChange,
}: {
  locacao: Locacao
  open: boolean
  onOpenChange: (open: boolean) => void
}) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-[600px]">
        <DialogHeader>
          <DialogTitle>Detalhes da Locação</DialogTitle>
          <DialogDescription>
            Informações completas da locação
          </DialogDescription>
        </DialogHeader>

        <div className="grid gap-4 py-4">
          {/* Status Badge */}
          <div className="flex items-center gap-2">
            <Badge variant={statusConfig[locacao.status].variant} className="text-sm">
              {statusConfig[locacao.status].label}
            </Badge>
            {locacao.modalidadePreco && (
              <Badge variant="outline">
                {modalidadeConfig[locacao.modalidadePreco]?.label || locacao.modalidadePreco}
              </Badge>
            )}
          </div>

          {/* Main Info */}
          <div className="grid grid-cols-2 gap-4">
            <div className="space-y-3">
              <div>
                <Label className="text-muted-foreground">Jetski</Label>
                <p className="font-medium flex items-center gap-2">
                  <Ship className="h-4 w-4" />
                  {locacao.jetskiSerie}
                  {locacao.jetskiModeloNome && (
                    <span className="text-muted-foreground">({locacao.jetskiModeloNome})</span>
                  )}
                </p>
              </div>

              <div>
                <Label className="text-muted-foreground">Cliente</Label>
                <p className="font-medium flex items-center gap-2">
                  <User className="h-4 w-4" />
                  {locacao.clienteNome || 'Walk-in (sem cliente)'}
                </p>
              </div>

              <div>
                <Label className="text-muted-foreground">Check-in</Label>
                <p>{formatDateTime(locacao.dataCheckIn)}</p>
              </div>

              {locacao.dataCheckOut && (
                <div>
                  <Label className="text-muted-foreground">Check-out</Label>
                  <p>{formatDateTime(locacao.dataCheckOut)}</p>
                </div>
              )}
            </div>

            <div className="space-y-3">
              <div>
                <Label className="text-muted-foreground">Horímetro</Label>
                <p className="flex items-center gap-2">
                  <Timer className="h-4 w-4" />
                  {locacao.horimetroInicio}h
                  {locacao.horimetroFim && (
                    <span>→ {locacao.horimetroFim}h</span>
                  )}
                </p>
              </div>

              {locacao.duracaoPrevista && (
                <div>
                  <Label className="text-muted-foreground">Duração Prevista</Label>
                  <p>{formatDuration(locacao.duracaoPrevista)}</p>
                </div>
              )}

              {locacao.minutosUsados && (
                <div>
                  <Label className="text-muted-foreground">Tempo Usado</Label>
                  <p className="flex items-center gap-2">
                    <Clock className="h-4 w-4" />
                    {formatDuration(locacao.minutosUsados)}
                    {locacao.minutosFaturaveis && locacao.minutosFaturaveis !== locacao.minutosUsados && (
                      <span className="text-muted-foreground">
                        (Faturável: {formatDuration(locacao.minutosFaturaveis)})
                      </span>
                    )}
                  </p>
                </div>
              )}
            </div>
          </div>

          {/* Values Section */}
          {(locacao.valorBase || locacao.valorTotal) && (
            <div className="rounded-lg border p-4 space-y-2 bg-muted/30">
              <h4 className="font-medium flex items-center gap-2">
                <DollarSign className="h-4 w-4" />
                Valores
              </h4>
              {locacao.valorBase && (
                <div className="flex justify-between">
                  <span className="text-muted-foreground">Valor Base:</span>
                  <span>{formatCurrency(locacao.valorBase)}</span>
                </div>
              )}
              {locacao.valorItensOpcionais && locacao.valorItensOpcionais > 0 && (
                <div className="flex justify-between">
                  <span className="text-muted-foreground">Itens Opcionais:</span>
                  <span>{formatCurrency(locacao.valorItensOpcionais)}</span>
                </div>
              )}
              {locacao.valorNegociado && (
                <div className="flex justify-between text-green-600">
                  <span>Valor Negociado:</span>
                  <span>{formatCurrency(locacao.valorNegociado)}</span>
                </div>
              )}
              {locacao.valorTotal && (
                <div className="flex justify-between font-bold border-t pt-2 mt-2">
                  <span>Total:</span>
                  <span>{formatCurrency(locacao.valorTotal)}</span>
                </div>
              )}
              {locacao.motivoDesconto && (
                <p className="text-sm text-muted-foreground italic">
                  Motivo desconto: {locacao.motivoDesconto}
                </p>
              )}
            </div>
          )}

          {/* Observações */}
          {locacao.observacoes && (
            <div>
              <Label className="text-muted-foreground">Observações</Label>
              <p className="text-sm">{locacao.observacoes}</p>
            </div>
          )}
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            Fechar
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

export default function LocacoesPage() {
  const { currentTenant } = useTenantStore()

  const [search, setSearch] = useState('')
  const [statusFilter, setStatusFilter] = useState<string>('all')
  const [checkInOpen, setCheckInOpen] = useState(false)
  const [checkOutLocacao, setCheckOutLocacao] = useState<Locacao | null>(null)
  const [detailLocacao, setDetailLocacao] = useState<Locacao | null>(null)

  const { data: locacoes, isLoading } = useQuery({
    queryKey: ['locacoes', currentTenant?.id, statusFilter],
    queryFn: () =>
      locacoesService.list({
        status: statusFilter !== 'all' ? statusFilter as LocacaoStatus : undefined,
      }),
    enabled: !!currentTenant,
  })

  // Filter by search (jetski serie or client name)
  const filteredLocacoes = locacoes?.filter((locacao) =>
    locacao.jetskiSerie?.toLowerCase().includes(search.toLowerCase()) ||
    locacao.clienteNome?.toLowerCase().includes(search.toLowerCase())
  )

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
          <h1 className="text-3xl font-bold">Locações</h1>
          <p className="text-muted-foreground">Gerencie as locações de jetskis</p>
        </div>
        <Button onClick={() => setCheckInOpen(true)}>
          <Plus className="mr-2 h-4 w-4" />
          Novo Check-in
        </Button>
      </div>

      <div className="flex gap-4">
        <div className="relative flex-1">
          <Search className="absolute left-2.5 top-2.5 h-4 w-4 text-muted-foreground" />
          <Input
            placeholder="Buscar por jetski ou cliente..."
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
            <SelectItem value="EM_CURSO">Em curso</SelectItem>
            <SelectItem value="FINALIZADA">Finalizadas</SelectItem>
            <SelectItem value="CANCELADA">Canceladas</SelectItem>
          </SelectContent>
        </Select>
      </div>

      <div className="rounded-md border">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Jetski</TableHead>
              <TableHead>Cliente</TableHead>
              <TableHead>Check-in</TableHead>
              <TableHead>Duração</TableHead>
              <TableHead>Valor</TableHead>
              <TableHead>Status</TableHead>
              <TableHead className="w-[50px]"></TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {isLoading ? (
              Array.from({ length: 5 }).map((_, i) => (
                <TableRow key={i}>
                  <TableCell><Skeleton className="h-4 w-24" /></TableCell>
                  <TableCell><Skeleton className="h-4 w-32" /></TableCell>
                  <TableCell><Skeleton className="h-4 w-28" /></TableCell>
                  <TableCell><Skeleton className="h-4 w-16" /></TableCell>
                  <TableCell><Skeleton className="h-4 w-20" /></TableCell>
                  <TableCell><Skeleton className="h-6 w-24" /></TableCell>
                  <TableCell><Skeleton className="h-8 w-8" /></TableCell>
                </TableRow>
              ))
            ) : filteredLocacoes?.length === 0 ? (
              <TableRow>
                <TableCell colSpan={7} className="h-24 text-center">
                  <div className="flex flex-col items-center gap-2">
                    <Anchor className="h-8 w-8 text-muted-foreground" />
                    <p className="text-muted-foreground">Nenhuma locação encontrada</p>
                  </div>
                </TableCell>
              </TableRow>
            ) : (
              filteredLocacoes?.map((locacao) => (
                <TableRow key={locacao.id}>
                  <TableCell>
                    <div className="flex items-center gap-2">
                      <Ship className="h-4 w-4 text-muted-foreground" />
                      <div>
                        <span className="font-medium">{locacao.jetskiSerie}</span>
                        {locacao.jetskiModeloNome && (
                          <p className="text-xs text-muted-foreground">{locacao.jetskiModeloNome}</p>
                        )}
                      </div>
                    </div>
                  </TableCell>
                  <TableCell>{locacao.clienteNome || 'Walk-in'}</TableCell>
                  <TableCell>{formatDateTime(locacao.dataCheckIn)}</TableCell>
                  <TableCell>
                    <div className="flex items-center gap-1">
                      <Clock className="h-4 w-4 text-muted-foreground" />
                      {locacao.minutosUsados
                        ? formatDuration(locacao.minutosUsados)
                        : locacao.duracaoPrevista
                          ? `~${formatDuration(locacao.duracaoPrevista)}`
                          : 'Em uso'}
                    </div>
                  </TableCell>
                  <TableCell>
                    {locacao.valorTotal ? formatCurrency(locacao.valorTotal) : '-'}
                  </TableCell>
                  <TableCell>
                    <Badge variant={statusConfig[locacao.status].variant}>
                      {statusConfig[locacao.status].label}
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
                        <DropdownMenuItem onClick={() => setDetailLocacao(locacao)}>
                          <Eye className="mr-2 h-4 w-4" />
                          Ver detalhes
                        </DropdownMenuItem>
                        {locacao.status === 'EM_CURSO' && (
                          <DropdownMenuItem onClick={() => setCheckOutLocacao(locacao)}>
                            <LogOut className="mr-2 h-4 w-4" />
                            Fazer Check-out
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

      <CheckInDialog open={checkInOpen} onOpenChange={setCheckInOpen} />

      {checkOutLocacao && (
        <CheckOutDialog
          locacao={checkOutLocacao}
          open={!!checkOutLocacao}
          onOpenChange={(open) => !open && setCheckOutLocacao(null)}
        />
      )}

      {detailLocacao && (
        <LocacaoDetailDialog
          locacao={detailLocacao}
          open={!!detailLocacao}
          onOpenChange={(open) => !open && setDetailLocacao(null)}
        />
      )}
    </div>
  )
}
