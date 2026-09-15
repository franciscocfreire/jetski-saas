'use client'

import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useRouter } from 'next/navigation'
import { Plus, Search, MoreHorizontal, FileText, Edit, DollarSign, Eye, Globe } from 'lucide-react'
import { useTenantStore } from '@/lib/store/tenant-store'
import { modelosService } from '@/lib/api/services/modelos'
import { ModeloFormDialog } from '@/components/modelos/modelo-form-dialog'
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
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { Skeleton } from '@/components/ui/skeleton'

export default function ModelosPage() {
  const { currentTenant } = useTenantStore()
  const router = useRouter()

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
              <TableHead>Marketplace</TableHead>
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
                  <TableCell><Skeleton className="h-6 w-16" /></TableCell>
                  <TableCell><Skeleton className="h-8 w-8" /></TableCell>
                </TableRow>
              ))
            ) : filteredModelos?.length === 0 ? (
              <TableRow>
                <TableCell colSpan={8} className="h-24 text-center">
                  <div className="flex flex-col items-center gap-2">
                    <FileText className="h-8 w-8 text-muted-foreground" />
                    <p className="text-muted-foreground">Nenhum modelo encontrado</p>
                  </div>
                </TableCell>
              </TableRow>
            ) : (
              filteredModelos?.map((modelo) => (
                <TableRow
                  key={modelo.id}
                  className="cursor-pointer hover:bg-muted/50"
                  onClick={() => router.push(`/dashboard/modelos/${modelo.id}`)}
                >
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
                    {modelo.exibirNoMarketplace !== false ? (
                      <Badge variant="outline" className="gap-1">
                        <Globe className="h-3 w-3" />
                        Visível
                      </Badge>
                    ) : (
                      <Badge variant="secondary">Oculto</Badge>
                    )}
                  </TableCell>
                  <TableCell>
                    <Badge variant={modelo.ativo ? 'success' : 'destructive'}>
                      {modelo.ativo ? 'Ativo' : 'Inativo'}
                    </Badge>
                  </TableCell>
                  <TableCell onClick={(e) => e.stopPropagation()}>
                    <DropdownMenu>
                      <DropdownMenuTrigger asChild>
                        <Button variant="ghost" size="icon">
                          <MoreHorizontal className="h-4 w-4" />
                        </Button>
                      </DropdownMenuTrigger>
                      <DropdownMenuContent align="end">
                        <DropdownMenuItem onClick={() => router.push(`/dashboard/modelos/${modelo.id}`)}>
                          <Eye className="mr-2 h-4 w-4" />
                          Ver Detalhes
                        </DropdownMenuItem>
                        <DropdownMenuItem onClick={() => handleEdit(modelo)}>
                          <Edit className="mr-2 h-4 w-4" />
                          Editar Rápido
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
