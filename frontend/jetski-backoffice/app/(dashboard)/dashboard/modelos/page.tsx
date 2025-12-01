'use client'

import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Plus, Search, MoreHorizontal, FileText, Edit, DollarSign } from 'lucide-react'
import { useTenantStore } from '@/lib/store/tenant-store'
import { modelosService, type ModeloCreateRequest } from '@/lib/api/services/modelos'
import type { Modelo } from '@/lib/api/types'
import { formatCurrency } from '@/lib/utils'
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
import { Label } from '@/components/ui/label'
import { Skeleton } from '@/components/ui/skeleton'

function ModeloFormDialog({
  modelo,
  open,
  onOpenChange,
}: {
  modelo?: Modelo
  open: boolean
  onOpenChange: (open: boolean) => void
}) {
  const queryClient = useQueryClient()

  const [formData, setFormData] = useState<ModeloCreateRequest>({
    nome: modelo?.nome || '',
    fabricante: modelo?.fabricante || '',
    potenciaHp: modelo?.potenciaHp || 90,
    capacidadePessoas: modelo?.capacidadePessoas || 2,
    precoBaseHora: modelo?.precoBaseHora || 150,
    toleranciaMin: modelo?.toleranciaMin || 5,
    taxaHoraExtra: modelo?.taxaHoraExtra || 50,
    incluiCombustivel: modelo?.incluiCombustivel || false,
    caucao: modelo?.caucao || 300,
  })

  const createMutation = useMutation({
    mutationFn: (data: ModeloCreateRequest) => modelosService.create(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['modelos'] })
      onOpenChange(false)
    },
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: string; data: Partial<ModeloCreateRequest> }) =>
      modelosService.update(id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['modelos'] })
      onOpenChange(false)
    },
  })

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (modelo) {
      updateMutation.mutate({ id: modelo.id, data: formData })
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
            <DialogTitle>{modelo ? 'Editar Modelo' : 'Novo Modelo'}</DialogTitle>
            <DialogDescription>
              {modelo ? 'Atualize os dados do modelo' : 'Cadastre um novo modelo de jetski'}
            </DialogDescription>
          </DialogHeader>

          <div className="grid gap-4 py-4">
            <div className="grid grid-cols-2 gap-4">
              <div className="grid gap-2">
                <Label htmlFor="nome">Nome *</Label>
                <Input
                  id="nome"
                  value={formData.nome}
                  onChange={(e) => setFormData({ ...formData, nome: e.target.value })}
                  placeholder="Ex: Sea-Doo GTI 130"
                  required
                />
              </div>

              <div className="grid gap-2">
                <Label htmlFor="fabricante">Fabricante</Label>
                <Input
                  id="fabricante"
                  value={formData.fabricante || ''}
                  onChange={(e) => setFormData({ ...formData, fabricante: e.target.value })}
                  placeholder="Ex: Sea-Doo"
                />
              </div>
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div className="grid gap-2">
                <Label htmlFor="potenciaHp">Potência (HP)</Label>
                <Input
                  id="potenciaHp"
                  type="number"
                  value={formData.potenciaHp || 90}
                  onChange={(e) => setFormData({ ...formData, potenciaHp: Number(e.target.value) })}
                  min={0}
                />
              </div>

              <div className="grid gap-2">
                <Label htmlFor="capacidadePessoas">Capacidade (pessoas) *</Label>
                <Input
                  id="capacidadePessoas"
                  type="number"
                  value={formData.capacidadePessoas}
                  onChange={(e) => setFormData({ ...formData, capacidadePessoas: Number(e.target.value) })}
                  min={1}
                  max={4}
                  required
                />
              </div>
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div className="grid gap-2">
                <Label htmlFor="precoBase">Preço/Hora (R$) *</Label>
                <Input
                  id="precoBase"
                  type="number"
                  value={formData.precoBaseHora}
                  onChange={(e) => setFormData({ ...formData, precoBaseHora: Number(e.target.value) })}
                  min={0}
                  step={10}
                  required
                />
              </div>

              <div className="grid gap-2">
                <Label htmlFor="taxaHoraExtra">Taxa Hora Extra (R$)</Label>
                <Input
                  id="taxaHoraExtra"
                  type="number"
                  value={formData.taxaHoraExtra || 0}
                  onChange={(e) => setFormData({ ...formData, taxaHoraExtra: Number(e.target.value) })}
                  min={0}
                  step={10}
                />
              </div>
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div className="grid gap-2">
                <Label htmlFor="toleranciaMin">Tolerância (min)</Label>
                <Input
                  id="toleranciaMin"
                  type="number"
                  value={formData.toleranciaMin || 5}
                  onChange={(e) => setFormData({ ...formData, toleranciaMin: Number(e.target.value) })}
                  min={0}
                />
              </div>

              <div className="grid gap-2">
                <Label htmlFor="caucao">Caução (R$)</Label>
                <Input
                  id="caucao"
                  type="number"
                  value={formData.caucao || 0}
                  onChange={(e) => setFormData({ ...formData, caucao: Number(e.target.value) })}
                  min={0}
                  step={50}
                />
              </div>
            </div>
          </div>

          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
              Cancelar
            </Button>
            <Button type="submit" disabled={isLoading}>
              {isLoading ? 'Salvando...' : modelo ? 'Salvar' : 'Criar'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

export default function ModelosPage() {
  const { currentTenant } = useTenantStore()

  const [search, setSearch] = useState('')
  const [dialogOpen, setDialogOpen] = useState(false)
  const [editingModelo, setEditingModelo] = useState<Modelo | undefined>()

  const { data: modelos, isLoading } = useQuery({
    queryKey: ['modelos', currentTenant?.id],
    queryFn: () => modelosService.list(),
    enabled: !!currentTenant,
  })

  const filteredModelos = modelos?.filter((modelo) =>
    modelo.nome?.toLowerCase().includes(search.toLowerCase())
  )

  const handleEdit = (modelo: Modelo) => {
    setEditingModelo(modelo)
    setDialogOpen(true)
  }

  const handleNew = () => {
    setEditingModelo(undefined)
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
          <h1 className="text-3xl font-bold">Modelos</h1>
          <p className="text-muted-foreground">Gerencie os modelos e preços de jetskis</p>
        </div>
        <Button onClick={handleNew}>
          <Plus className="mr-2 h-4 w-4" />
          Novo Modelo
        </Button>
      </div>

      <div className="flex gap-4">
        <div className="relative flex-1">
          <Search className="absolute left-2.5 top-2.5 h-4 w-4 text-muted-foreground" />
          <Input
            placeholder="Buscar por nome..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="pl-8"
          />
        </div>
      </div>

      <div className="rounded-md border">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Nome</TableHead>
              <TableHead>Fabricante</TableHead>
              <TableHead>Preço/Hora</TableHead>
              <TableHead>Hora Extra</TableHead>
              <TableHead>Capacidade</TableHead>
              <TableHead>Status</TableHead>
              <TableHead className="w-[50px]"></TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {isLoading ? (
              Array.from({ length: 5 }).map((_, i) => (
                <TableRow key={i}>
                  <TableCell><Skeleton className="h-4 w-32" /></TableCell>
                  <TableCell><Skeleton className="h-4 w-40" /></TableCell>
                  <TableCell><Skeleton className="h-4 w-24" /></TableCell>
                  <TableCell><Skeleton className="h-4 w-24" /></TableCell>
                  <TableCell><Skeleton className="h-4 w-16" /></TableCell>
                  <TableCell><Skeleton className="h-6 w-16" /></TableCell>
                  <TableCell><Skeleton className="h-8 w-8" /></TableCell>
                </TableRow>
              ))
            ) : filteredModelos?.length === 0 ? (
              <TableRow>
                <TableCell colSpan={7} className="h-24 text-center">
                  <div className="flex flex-col items-center gap-2">
                    <FileText className="h-8 w-8 text-muted-foreground" />
                    <p className="text-muted-foreground">Nenhum modelo encontrado</p>
                  </div>
                </TableCell>
              </TableRow>
            ) : (
              filteredModelos?.map((modelo) => (
                <TableRow key={modelo.id}>
                  <TableCell className="font-medium">{modelo.nome}</TableCell>
                  <TableCell>
                    {modelo.fabricante || '-'}
                  </TableCell>
                  <TableCell>
                    <div className="flex items-center gap-1">
                      <DollarSign className="h-4 w-4 text-muted-foreground" />
                      {formatCurrency(modelo.precoBaseHora)}
                    </div>
                  </TableCell>
                  <TableCell>
                    {modelo.taxaHoraExtra ? formatCurrency(modelo.taxaHoraExtra) : '-'}
                  </TableCell>
                  <TableCell>{modelo.capacidadePessoas} pessoas</TableCell>
                  <TableCell>
                    <Badge variant={modelo.ativo ? 'success' : 'destructive'}>
                      {modelo.ativo ? 'Ativo' : 'Inativo'}
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
                        <DropdownMenuItem onClick={() => handleEdit(modelo)}>
                          <Edit className="mr-2 h-4 w-4" />
                          Editar
                        </DropdownMenuItem>
                      </DropdownMenuContent>
                    </DropdownMenu>
                  </TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      </div>

      <ModeloFormDialog
        modelo={editingModelo}
        open={dialogOpen}
        onOpenChange={setDialogOpen}
      />
    </div>
  )
}
