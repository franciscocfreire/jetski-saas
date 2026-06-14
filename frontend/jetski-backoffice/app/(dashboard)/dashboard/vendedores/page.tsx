'use client'

import { useState, useEffect } from 'react'
import { useRouter } from 'next/navigation'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Plus, Search, MoreHorizontal, UserCircle, Edit, Eye, TrendingUp, Clock, CheckCircle } from 'lucide-react'
import { useTenantStore } from '@/lib/store/tenant-store'
import { vendedoresService } from '@/lib/api/services'
import type { Vendedor, VendedorCreateRequest, VendedorResumo, TipoChavePix } from '@/lib/api/types'
import { TIPOS_CHAVE_PIX } from '@/lib/api/types'
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
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'

function VendedorFormDialog({
  vendedor,
  open,
  onOpenChange,
}: {
  vendedor?: Vendedor
  open: boolean
  onOpenChange: (open: boolean) => void
}) {
  const queryClient = useQueryClient()

  const [formData, setFormData] = useState<VendedorCreateRequest>({
    nome: '',
    email: '',
    telefone: '',
    chavePix: '',
    tipoChavePix: undefined,
    comissaoPercentual: 10,
    diariaBase: 0,
  })

  // Reset form when vendedor or dialog changes
  useEffect(() => {
    if (open) {
      setFormData({
        nome: vendedor?.nome || '',
        email: vendedor?.email || '',
        telefone: vendedor?.telefone || '',
        chavePix: vendedor?.chavePix || '',
        tipoChavePix: vendedor?.tipoChavePix || undefined,
        comissaoPercentual: vendedor?.comissaoPercentual || 10,
        diariaBase: vendedor?.diariaBase || 0,
      })
    }
  }, [vendedor, open])

  const createMutation = useMutation({
    mutationFn: (data: VendedorCreateRequest) => vendedoresService.create(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['vendedores'] })
      onOpenChange(false)
    },
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: string; data: Partial<VendedorCreateRequest> }) =>
      vendedoresService.update(id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['vendedores'] })
      onOpenChange(false)
    },
  })

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (vendedor) {
      updateMutation.mutate({ id: vendedor.id, data: formData })
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
            <DialogTitle>{vendedor ? 'Editar Vendedor' : 'Novo Vendedor'}</DialogTitle>
            <DialogDescription>
              {vendedor ? 'Atualize os dados do vendedor' : 'Cadastre um novo vendedor/parceiro'}
            </DialogDescription>
          </DialogHeader>

          <div className="grid gap-4 py-4">
            <div className="grid gap-2">
              <Label htmlFor="nome">Nome *</Label>
              <Input
                id="nome"
                value={formData.nome}
                onChange={(e) => setFormData({ ...formData, nome: e.target.value })}
                placeholder="Nome completo"
                required
              />
            </div>

            <div className="grid gap-2">
              <Label htmlFor="email">Email</Label>
              <Input
                id="email"
                type="email"
                value={formData.email || ''}
                onChange={(e) => setFormData({ ...formData, email: e.target.value })}
                placeholder="email@exemplo.com"
              />
            </div>

            <div className="grid gap-2">
              <Label htmlFor="telefone">Telefone</Label>
              <Input
                id="telefone"
                value={formData.telefone || ''}
                onChange={(e) => setFormData({ ...formData, telefone: e.target.value })}
                placeholder="(11) 99999-9999"
              />
            </div>

            {/* PIX Fields */}
            <div className="grid gap-2">
              <Label htmlFor="tipoChavePix">Tipo de Chave PIX</Label>
              <Select
                value={formData.tipoChavePix || ''}
                onValueChange={(value) => setFormData({
                  ...formData,
                  tipoChavePix: value ? value as TipoChavePix : undefined
                })}
              >
                <SelectTrigger>
                  <SelectValue placeholder="Selecione o tipo" />
                </SelectTrigger>
                <SelectContent>
                  {TIPOS_CHAVE_PIX.map((tipo) => (
                    <SelectItem key={tipo.value} value={tipo.value}>
                      {tipo.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <div className="grid gap-2">
              <Label htmlFor="chavePix">Chave PIX</Label>
              <Input
                id="chavePix"
                value={formData.chavePix || ''}
                onChange={(e) => setFormData({ ...formData, chavePix: e.target.value })}
                placeholder={
                  formData.tipoChavePix === 'CPF' ? '000.000.000-00' :
                  formData.tipoChavePix === 'CNPJ' ? '00.000.000/0000-00' :
                  formData.tipoChavePix === 'EMAIL' ? 'email@exemplo.com' :
                  formData.tipoChavePix === 'TELEFONE' ? '+5511999999999' :
                  'Chave PIX'
                }
              />
              <p className="text-xs text-muted-foreground">
                Chave PIX para pagamento de comissões e diárias
              </p>
            </div>

            <div className="grid gap-2">
              <Label htmlFor="comissao">Comissão (%)</Label>
              <Input
                id="comissao"
                type="number"
                value={formData.comissaoPercentual}
                onChange={(e) => setFormData({ ...formData, comissaoPercentual: Number(e.target.value) })}
                min={0}
                max={100}
                step={0.5}
                required
              />
            </div>

            <div className="grid gap-2">
              <Label htmlFor="diariaBase">Diária Base (R$)</Label>
              <Input
                id="diariaBase"
                type="number"
                value={formData.diariaBase || ''}
                onChange={(e) => setFormData({ ...formData, diariaBase: e.target.value ? Number(e.target.value) : undefined })}
                min={0}
                step={0.01}
                placeholder="0,00"
              />
              <p className="text-xs text-muted-foreground">
                Valor da diária para registro de presença
              </p>
            </div>
          </div>

          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
              Cancelar
            </Button>
            <Button type="submit" disabled={isLoading}>
              {isLoading ? 'Salvando...' : vendedor ? 'Salvar' : 'Criar'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

export default function VendedoresPage() {
  const { currentTenant } = useTenantStore()
  const router = useRouter()

  const [search, setSearch] = useState('')
  const [dialogOpen, setDialogOpen] = useState(false)
  const [editingVendedor, setEditingVendedor] = useState<Vendedor | undefined>()

  // Fetch vendedores with commission summary
  const { data: vendedores, isLoading } = useQuery({
    queryKey: ['vendedores', 'resumo', currentTenant?.id],
    queryFn: () => vendedoresService.listWithSummary(),
    enabled: !!currentTenant,
  })

  const filteredVendedores = vendedores?.filter((vendedor) =>
    vendedor.nome?.toLowerCase().includes(search.toLowerCase())
  )

  const handleEdit = (vendedor: VendedorResumo) => {
    // Need to fetch full vendedor for edit
    vendedoresService.getById(vendedor.id).then((full) => {
      setEditingVendedor(full)
      setDialogOpen(true)
    })
  }

  const handleNew = () => {
    setEditingVendedor(undefined)
    setDialogOpen(true)
  }

  const handleViewDetails = (vendedorId: string) => {
    router.push(`/dashboard/vendedores/${vendedorId}`)
  }

  // Calculate totals for summary cards
  const totals = vendedores?.reduce(
    (acc, v) => ({
      pendentes: acc.pendentes + (v.totalPendentes || 0),
      aprovadas: acc.aprovadas + (v.totalAprovadas || 0),
      pagas: acc.pagas + (v.totalPagas || 0),
    }),
    { pendentes: 0, aprovadas: 0, pagas: 0 }
  ) || { pendentes: 0, aprovadas: 0, pagas: 0 }

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
          <h1 className="text-3xl font-bold">Vendedores</h1>
          <p className="text-muted-foreground">Gerencie os vendedores, parceiros e comissões</p>
        </div>
        <Button onClick={handleNew}>
          <Plus className="mr-2 h-4 w-4" />
          Novo Vendedor
        </Button>
      </div>

      {/* Summary Cards */}
      <div className="grid gap-4 md:grid-cols-3">
        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">Comissões Pendentes</CardTitle>
            <Clock className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold text-yellow-600">
              {formatCurrency(totals.pendentes)}
            </div>
            <p className="text-xs text-muted-foreground">
              Aguardando aprovação
            </p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">Comissões Aprovadas</CardTitle>
            <TrendingUp className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold text-blue-600">
              {formatCurrency(totals.aprovadas)}
            </div>
            <p className="text-xs text-muted-foreground">
              Aguardando pagamento
            </p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">Total Pago</CardTitle>
            <CheckCircle className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold text-green-600">
              {formatCurrency(totals.pagas)}
            </div>
            <p className="text-xs text-muted-foreground">
              Comissões pagas
            </p>
          </CardContent>
        </Card>
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
              <TableHead>Tipo</TableHead>
              <TableHead className="text-right">Diária</TableHead>
              <TableHead className="text-right">Pendentes</TableHead>
              <TableHead className="text-right">Aprovadas</TableHead>
              <TableHead className="text-right">Pagas</TableHead>
              <TableHead>Status</TableHead>
              <TableHead className="w-[50px]"></TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {isLoading ? (
              Array.from({ length: 5 }).map((_, i) => (
                <TableRow key={i}>
                  <TableCell><Skeleton className="h-4 w-32" /></TableCell>
                  <TableCell><Skeleton className="h-4 w-20" /></TableCell>
                  <TableCell><Skeleton className="h-4 w-20" /></TableCell>
                  <TableCell><Skeleton className="h-4 w-24" /></TableCell>
                  <TableCell><Skeleton className="h-4 w-24" /></TableCell>
                  <TableCell><Skeleton className="h-4 w-24" /></TableCell>
                  <TableCell><Skeleton className="h-6 w-16" /></TableCell>
                  <TableCell><Skeleton className="h-8 w-8" /></TableCell>
                </TableRow>
              ))
            ) : filteredVendedores?.length === 0 ? (
              <TableRow>
                <TableCell colSpan={8} className="h-24 text-center">
                  <div className="flex flex-col items-center gap-2">
                    <UserCircle className="h-8 w-8 text-muted-foreground" />
                    <p className="text-muted-foreground">Nenhum vendedor encontrado</p>
                  </div>
                </TableCell>
              </TableRow>
            ) : (
              filteredVendedores?.map((vendedor) => (
                <TableRow
                  key={vendedor.id}
                  className="cursor-pointer hover:bg-muted/50"
                  onClick={() => handleViewDetails(vendedor.id)}
                >
                  <TableCell className="font-medium">{vendedor.nome}</TableCell>
                  <TableCell>
                    <Badge variant={vendedor.tipo === 'INTERNO' ? 'default' : 'secondary'}>
                      {vendedor.tipo === 'INTERNO' ? 'Interno' : 'Parceiro'}
                    </Badge>
                  </TableCell>
                  <TableCell className="text-right">
                    <span className={vendedor.diariaBase > 0 ? 'text-purple-600 font-medium' : 'text-muted-foreground'}>
                      {vendedor.diariaBase > 0 ? formatCurrency(vendedor.diariaBase) : '-'}
                    </span>
                  </TableCell>
                  <TableCell className="text-right">
                    <span className={vendedor.totalPendentes > 0 ? 'text-yellow-600 font-medium' : 'text-muted-foreground'}>
                      {formatCurrency(vendedor.totalPendentes || 0)}
                    </span>
                  </TableCell>
                  <TableCell className="text-right">
                    <span className={vendedor.totalAprovadas > 0 ? 'text-blue-600 font-medium' : 'text-muted-foreground'}>
                      {formatCurrency(vendedor.totalAprovadas || 0)}
                    </span>
                  </TableCell>
                  <TableCell className="text-right">
                    <span className={vendedor.totalPagas > 0 ? 'text-green-600 font-medium' : 'text-muted-foreground'}>
                      {formatCurrency(vendedor.totalPagas || 0)}
                    </span>
                  </TableCell>
                  <TableCell>
                    <Badge variant={vendedor.ativo ? 'success' : 'destructive'}>
                      {vendedor.ativo ? 'Ativo' : 'Inativo'}
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
                        <DropdownMenuItem onClick={() => handleViewDetails(vendedor.id)}>
                          <Eye className="mr-2 h-4 w-4" />
                          Ver detalhes
                        </DropdownMenuItem>
                        <DropdownMenuItem onClick={() => handleEdit(vendedor)}>
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

      <VendedorFormDialog
        vendedor={editingVendedor}
        open={dialogOpen}
        onOpenChange={setDialogOpen}
      />
    </div>
  )
}
