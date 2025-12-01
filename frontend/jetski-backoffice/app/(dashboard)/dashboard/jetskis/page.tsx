'use client'

import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Plus, Search, MoreHorizontal, Ship, Edit, Power } from 'lucide-react'
import { useTenantStore } from '@/lib/store/tenant-store'
import { jetskisService, modelosService } from '@/lib/api/services'
import type { Jetski, JetskiStatus, JetskiCreateRequest } from '@/lib/api/types'
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

const statusConfig: Record<JetskiStatus, { label: string; variant: 'success' | 'default' | 'warning' | 'destructive' }> = {
  DISPONIVEL: { label: 'Disponível', variant: 'success' },
  LOCADO: { label: 'Locado', variant: 'default' },
  MANUTENCAO: { label: 'Manutenção', variant: 'warning' },
  INATIVO: { label: 'Inativo', variant: 'destructive' },
}

function JetskiFormDialog({
  jetski,
  open,
  onOpenChange,
}: {
  jetski?: Jetski
  open: boolean
  onOpenChange: (open: boolean) => void
}) {
  const queryClient = useQueryClient()
  const { currentTenant } = useTenantStore()

  const [formData, setFormData] = useState<JetskiCreateRequest>({
    modeloId: jetski?.modeloId || '',
    serie: jetski?.serie || '',
    ano: jetski?.ano || new Date().getFullYear(),
    horimetroAtual: jetski?.horimetroAtual || 0,
  })

  const { data: modelos } = useQuery({
    queryKey: ['modelos', currentTenant?.id],
    queryFn: () => modelosService.list(),
    enabled: !!currentTenant && open,
  })

  const createMutation = useMutation({
    mutationFn: (data: JetskiCreateRequest) => jetskisService.create(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['jetskis'] })
      onOpenChange(false)
    },
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: string; data: Partial<JetskiCreateRequest> }) =>
      jetskisService.update(id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['jetskis'] })
      onOpenChange(false)
    },
  })

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (jetski) {
      updateMutation.mutate({ id: jetski.id, data: formData })
    } else {
      createMutation.mutate(formData)
    }
  }

  const isLoading = createMutation.isPending || updateMutation.isPending

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-[425px]">
        <form onSubmit={handleSubmit}>
          <DialogHeader>
            <DialogTitle>{jetski ? 'Editar Jetski' : 'Novo Jetski'}</DialogTitle>
            <DialogDescription>
              {jetski ? 'Atualize os dados do jetski' : 'Cadastre um novo jetski na frota'}
            </DialogDescription>
          </DialogHeader>

          <div className="grid gap-4 py-4">
            <div className="grid gap-2">
              <Label htmlFor="modelo">Modelo</Label>
              <Select
                value={formData.modeloId}
                onValueChange={(value) => setFormData({ ...formData, modeloId: value })}
              >
                <SelectTrigger>
                  <SelectValue placeholder="Selecione um modelo" />
                </SelectTrigger>
                <SelectContent>
                  {modelos?.map((modelo) => (
                    <SelectItem key={modelo.id} value={modelo.id}>
                      {modelo.nome}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <div className="grid gap-2">
              <Label htmlFor="serie">Número de Série</Label>
              <Input
                id="serie"
                value={formData.serie}
                onChange={(e) => setFormData({ ...formData, serie: e.target.value })}
                placeholder="Ex: JET-001"
                required
              />
            </div>

            <div className="grid gap-2">
              <Label htmlFor="ano">Ano</Label>
              <Input
                id="ano"
                type="number"
                value={formData.ano || new Date().getFullYear()}
                onChange={(e) => setFormData({ ...formData, ano: Number(e.target.value) })}
                min={1900}
                max={new Date().getFullYear() + 1}
              />
            </div>

            <div className="grid gap-2">
              <Label htmlFor="horimetroAtual">Horímetro Atual</Label>
              <Input
                id="horimetroAtual"
                type="number"
                value={formData.horimetroAtual || 0}
                onChange={(e) => setFormData({ ...formData, horimetroAtual: Number(e.target.value) })}
                min={0}
                step={0.1}
              />
            </div>
          </div>

          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
              Cancelar
            </Button>
            <Button type="submit" disabled={isLoading}>
              {isLoading ? 'Salvando...' : jetski ? 'Salvar' : 'Criar'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

export default function JetskisPage() {
  const { currentTenant } = useTenantStore()
  const queryClient = useQueryClient()

  const [search, setSearch] = useState('')
  const [statusFilter, setStatusFilter] = useState<string>('all')
  const [dialogOpen, setDialogOpen] = useState(false)
  const [editingJetski, setEditingJetski] = useState<Jetski | undefined>()

  const { data: jetskis, isLoading } = useQuery({
    queryKey: ['jetskis', currentTenant?.id, statusFilter],
    queryFn: () =>
      jetskisService.list({
        status: statusFilter !== 'all' ? statusFilter as JetskiStatus : undefined,
      }),
    enabled: !!currentTenant,
  })

  const reactivateMutation = useMutation({
    mutationFn: (id: string) => jetskisService.reactivate(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['jetskis'] })
    },
  })

  const filteredJetskis = jetskis?.filter((jetski) =>
    jetski.serie?.toLowerCase().includes(search.toLowerCase())
  )

  const handleEdit = (jetski: Jetski) => {
    setEditingJetski(jetski)
    setDialogOpen(true)
  }

  const handleNew = () => {
    setEditingJetski(undefined)
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
          <h1 className="text-3xl font-bold">Jetskis</h1>
          <p className="text-muted-foreground">Gerencie a frota de jetskis</p>
        </div>
        <Button onClick={handleNew}>
          <Plus className="mr-2 h-4 w-4" />
          Novo Jetski
        </Button>
      </div>

      <div className="flex gap-4">
        <div className="relative flex-1">
          <Search className="absolute left-2.5 top-2.5 h-4 w-4 text-muted-foreground" />
          <Input
            placeholder="Buscar por número de série..."
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
            <SelectItem value="all">Todos</SelectItem>
            <SelectItem value="DISPONIVEL">Disponível</SelectItem>
            <SelectItem value="LOCADO">Locado</SelectItem>
            <SelectItem value="MANUTENCAO">Manutenção</SelectItem>
            <SelectItem value="INATIVO">Inativo</SelectItem>
          </SelectContent>
        </Select>
      </div>

      <div className="rounded-md border">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Série</TableHead>
              <TableHead>Modelo</TableHead>
              <TableHead>Ano</TableHead>
              <TableHead>Horímetro</TableHead>
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
                  <TableCell><Skeleton className="h-4 w-20" /></TableCell>
                  <TableCell><Skeleton className="h-4 w-16" /></TableCell>
                  <TableCell><Skeleton className="h-6 w-24" /></TableCell>
                  <TableCell><Skeleton className="h-8 w-8" /></TableCell>
                </TableRow>
              ))
            ) : filteredJetskis?.length === 0 ? (
              <TableRow>
                <TableCell colSpan={6} className="h-24 text-center">
                  <div className="flex flex-col items-center gap-2">
                    <Ship className="h-8 w-8 text-muted-foreground" />
                    <p className="text-muted-foreground">Nenhum jetski encontrado</p>
                  </div>
                </TableCell>
              </TableRow>
            ) : (
              filteredJetskis?.map((jetski) => (
                <TableRow key={jetski.id}>
                  <TableCell className="font-medium">{jetski.serie}</TableCell>
                  <TableCell>{jetski.modelo?.nome || '-'}</TableCell>
                  <TableCell>{jetski.ano || '-'}</TableCell>
                  <TableCell>{jetski.horimetroAtual}h</TableCell>
                  <TableCell>
                    <Badge variant={statusConfig[jetski.status].variant}>
                      {statusConfig[jetski.status].label}
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
                        <DropdownMenuItem onClick={() => handleEdit(jetski)}>
                          <Edit className="mr-2 h-4 w-4" />
                          Editar
                        </DropdownMenuItem>
                        {jetski.status === 'INATIVO' && (
                          <DropdownMenuItem
                            onClick={() => reactivateMutation.mutate(jetski.id)}
                          >
                            <Power className="mr-2 h-4 w-4" />
                            Reativar
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

      <JetskiFormDialog
        jetski={editingJetski}
        open={dialogOpen}
        onOpenChange={setDialogOpen}
      />
    </div>
  )
}
