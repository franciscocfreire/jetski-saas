'use client'

import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Plus, Search, MoreHorizontal, Wrench, Edit, CheckCircle } from 'lucide-react'
import { useTenantStore } from '@/lib/store/tenant-store'
import { manutencoesService, jetskisService } from '@/lib/api/services'
import type { Manutencao, ManutencaoCreateRequest, ManutencaoStatus, ManutencaoTipo, ManutencaoPrioridade } from '@/lib/api/types'
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

const statusConfig: Record<ManutencaoStatus, { label: string; variant: 'default' | 'success' | 'warning' | 'destructive' }> = {
  ABERTA: { label: 'Aberta', variant: 'warning' },
  EM_ANDAMENTO: { label: 'Em andamento', variant: 'default' },
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

  const [formData, setFormData] = useState<ManutencaoCreateRequest>({
    jetskiId: manutencao?.jetskiId || '',
    tipo: manutencao?.tipo || 'CORRETIVA',
    prioridade: manutencao?.prioridade || 'MEDIA',
    descricaoProblema: manutencao?.descricaoProblema || '',
    observacoes: manutencao?.observacoes || '',
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

export default function ManutencaoPage() {
  const { currentTenant } = useTenantStore()
  const queryClient = useQueryClient()

  const [search, setSearch] = useState('')
  const [statusFilter, setStatusFilter] = useState<string>('all')
  const [dialogOpen, setDialogOpen] = useState(false)
  const [editingManutencao, setEditingManutencao] = useState<Manutencao | undefined>()

  const { data: manutencoes, isLoading } = useQuery({
    queryKey: ['manutencoes', currentTenant?.id, statusFilter],
    queryFn: () =>
      manutencoesService.list({
        status: statusFilter !== 'all' ? statusFilter as ManutencaoStatus : undefined,
      }),
    enabled: !!currentTenant,
  })

  const concluirMutation = useMutation({
    mutationFn: (id: string) =>
      manutencoesService.update(id, { descricaoProblema: '' }),  // TODO: Add proper conclude endpoint
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['manutencoes'] })
      queryClient.invalidateQueries({ queryKey: ['jetskis'] })
    },
  })

  const handleEdit = (manutencao: Manutencao) => {
    setEditingManutencao(manutencao)
    setDialogOpen(true)
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
                  <TableCell><Skeleton className="h-4 w-40" /></TableCell>
                  <TableCell><Skeleton className="h-4 w-24" /></TableCell>
                  <TableCell><Skeleton className="h-4 w-20" /></TableCell>
                  <TableCell><Skeleton className="h-6 w-24" /></TableCell>
                  <TableCell><Skeleton className="h-8 w-8" /></TableCell>
                </TableRow>
              ))
            ) : manutencoes?.length === 0 ? (
              <TableRow>
                <TableCell colSpan={7} className="h-24 text-center">
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
                        {manutencao.status !== 'CONCLUIDA' && manutencao.status !== 'CANCELADA' && (
                          <DropdownMenuItem onClick={() => concluirMutation.mutate(manutencao.id)}>
                            <CheckCircle className="mr-2 h-4 w-4" />
                            Concluir
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
    </div>
  )
}
