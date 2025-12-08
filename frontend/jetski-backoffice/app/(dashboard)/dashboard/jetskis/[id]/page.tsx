'use client'

import { useParams, useRouter } from 'next/navigation'
import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { format } from 'date-fns'
import { ptBR } from 'date-fns/locale'
import {
  ArrowLeft,
  Ship,
  Clock,
  Calendar,
  Wrench,
  Settings,
  History,
  Edit,
  Power,
  PowerOff,
  AlertTriangle,
  CheckCircle,
  XCircle,
  Timer,
} from 'lucide-react'
import { useTenantStore } from '@/lib/store/tenant-store'
import { jetskisService, modelosService, manutencoesService, locacoesService } from '@/lib/api/services'
import type { Jetski, JetskiStatus, JetskiCreateRequest, Modelo, Manutencao, Locacao } from '@/lib/api/types'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { Skeleton } from '@/components/ui/skeleton'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'

const statusConfig: Record<JetskiStatus, { label: string; variant: 'success' | 'default' | 'warning' | 'destructive'; icon: React.ElementType }> = {
  DISPONIVEL: { label: 'Disponível', variant: 'success', icon: CheckCircle },
  LOCADO: { label: 'Locado', variant: 'default', icon: Timer },
  MANUTENCAO: { label: 'Manutenção', variant: 'warning', icon: Wrench },
  INATIVO: { label: 'Inativo', variant: 'destructive', icon: XCircle },
}

const manutencaoStatusConfig: Record<string, { label: string; variant: 'success' | 'default' | 'warning' | 'destructive' }> = {
  ABERTA: { label: 'Aberta', variant: 'warning' },
  EM_ANDAMENTO: { label: 'Em Andamento', variant: 'default' },
  AGUARDANDO_PECAS: { label: 'Aguardando Peças', variant: 'warning' },
  CONCLUIDA: { label: 'Concluída', variant: 'success' },
  CANCELADA: { label: 'Cancelada', variant: 'destructive' },
}

const locacaoStatusConfig: Record<string, { label: string; variant: 'success' | 'default' | 'warning' | 'destructive' }> = {
  EM_CURSO: { label: 'Em Curso', variant: 'default' },
  FINALIZADA: { label: 'Finalizada', variant: 'success' },
  CANCELADA: { label: 'Cancelada', variant: 'destructive' },
}

// Edit Dialog Component
function JetskiEditDialog({
  jetski,
  open,
  onOpenChange,
}: {
  jetski: Jetski
  open: boolean
  onOpenChange: (open: boolean) => void
}) {
  const queryClient = useQueryClient()
  const { currentTenant } = useTenantStore()

  const [formData, setFormData] = useState<JetskiCreateRequest>({
    modeloId: jetski.modeloId,
    serie: jetski.serie,
    ano: jetski.ano,
    horimetroAtual: jetski.horimetroAtual,
  })

  const { data: modelos } = useQuery({
    queryKey: ['modelos', currentTenant?.id],
    queryFn: () => modelosService.list(),
    enabled: !!currentTenant && open,
  })

  const updateMutation = useMutation({
    mutationFn: (data: Partial<JetskiCreateRequest>) => jetskisService.update(jetski.id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['jetski', jetski.id] })
      queryClient.invalidateQueries({ queryKey: ['jetskis'] })
      onOpenChange(false)
    },
  })

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    updateMutation.mutate(formData)
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-[500px]">
        <form onSubmit={handleSubmit}>
          <DialogHeader>
            <DialogTitle>Editar Jetski</DialogTitle>
            <DialogDescription>Atualize os dados do jetski {jetski.serie}</DialogDescription>
          </DialogHeader>

          <div className="grid gap-4 py-4">
            <div className="grid grid-cols-2 gap-4">
              <div className="grid gap-2">
                <Label htmlFor="modelo">Modelo</Label>
                <Select
                  value={formData.modeloId}
                  onValueChange={(value) => setFormData({ ...formData, modeloId: value })}
                >
                  <SelectTrigger>
                    <SelectValue placeholder="Selecione" />
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
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div className="grid gap-2">
                <Label htmlFor="ano">Ano de Fabricação</Label>
                <Input
                  id="ano"
                  type="number"
                  value={formData.ano || new Date().getFullYear()}
                  onChange={(e) => setFormData({ ...formData, ano: Number(e.target.value) })}
                  min={1990}
                  max={new Date().getFullYear() + 1}
                />
              </div>

              <div className="grid gap-2">
                <Label htmlFor="horimetroAtual">Horímetro (horas)</Label>
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
          </div>

          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
              Cancelar
            </Button>
            <Button type="submit" disabled={updateMutation.isPending}>
              {updateMutation.isPending ? 'Salvando...' : 'Salvar'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

// Status Change Dialog
function StatusChangeDialog({
  jetski,
  open,
  onOpenChange,
}: {
  jetski: Jetski
  open: boolean
  onOpenChange: (open: boolean) => void
}) {
  const queryClient = useQueryClient()
  const [newStatus, setNewStatus] = useState<JetskiStatus>(jetski.status)

  const statusMutation = useMutation({
    mutationFn: (status: JetskiStatus) => jetskisService.updateStatus(jetski.id, status),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['jetski', jetski.id] })
      queryClient.invalidateQueries({ queryKey: ['jetskis'] })
      onOpenChange(false)
    },
  })

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (newStatus !== jetski.status) {
      statusMutation.mutate(newStatus)
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-[400px]">
        <form onSubmit={handleSubmit}>
          <DialogHeader>
            <DialogTitle>Alterar Status</DialogTitle>
            <DialogDescription>Altere o status operacional do jetski {jetski.serie}</DialogDescription>
          </DialogHeader>

          <div className="py-6">
            <Label>Novo Status</Label>
            <Select value={newStatus} onValueChange={(v) => setNewStatus(v as JetskiStatus)}>
              <SelectTrigger className="mt-2">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="DISPONIVEL">
                  <div className="flex items-center gap-2">
                    <CheckCircle className="h-4 w-4 text-green-500" />
                    Disponível
                  </div>
                </SelectItem>
                <SelectItem value="MANUTENCAO">
                  <div className="flex items-center gap-2">
                    <Wrench className="h-4 w-4 text-yellow-500" />
                    Manutenção
                  </div>
                </SelectItem>
                <SelectItem value="LOCADO" disabled>
                  <div className="flex items-center gap-2">
                    <Timer className="h-4 w-4 text-blue-500" />
                    Locado (automático)
                  </div>
                </SelectItem>
              </SelectContent>
            </Select>

            {newStatus === 'MANUTENCAO' && (
              <p className="mt-2 text-sm text-muted-foreground">
                <AlertTriangle className="inline h-4 w-4 mr-1 text-yellow-500" />
                Jetskis em manutenção não podem ser reservados.
              </p>
            )}
          </div>

          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
              Cancelar
            </Button>
            <Button type="submit" disabled={statusMutation.isPending || newStatus === jetski.status}>
              {statusMutation.isPending ? 'Salvando...' : 'Confirmar'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

// Main Page Component
export default function JetskiDetailsPage() {
  const params = useParams()
  const router = useRouter()
  const { currentTenant } = useTenantStore()
  const queryClient = useQueryClient()
  const jetskiId = params.id as string

  const [editDialogOpen, setEditDialogOpen] = useState(false)
  const [statusDialogOpen, setStatusDialogOpen] = useState(false)

  // Fetch jetski details
  const { data: jetski, isLoading: jetskiLoading } = useQuery({
    queryKey: ['jetski', jetskiId],
    queryFn: () => jetskisService.getById(jetskiId),
    enabled: !!currentTenant && !!jetskiId,
  })

  // Fetch modelo details
  const { data: modelo } = useQuery({
    queryKey: ['modelo', jetski?.modeloId],
    queryFn: () => modelosService.getById(jetski!.modeloId),
    enabled: !!jetski?.modeloId,
  })

  // Fetch maintenance history
  const { data: manutencoes, isLoading: manutencoesLoading } = useQuery({
    queryKey: ['manutencoes', jetskiId],
    queryFn: () => manutencoesService.list({ jetskiId }),
    enabled: !!currentTenant && !!jetskiId,
  })

  // Fetch rental history
  const { data: locacoes, isLoading: locacoesLoading } = useQuery({
    queryKey: ['locacoes', jetskiId],
    queryFn: () => locacoesService.list({ jetskiId }),
    enabled: !!currentTenant && !!jetskiId,
  })

  // Deactivate mutation
  const deactivateMutation = useMutation({
    mutationFn: () => jetskisService.deactivate(jetskiId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['jetski', jetskiId] })
      queryClient.invalidateQueries({ queryKey: ['jetskis'] })
    },
  })

  // Reactivate mutation
  const reactivateMutation = useMutation({
    mutationFn: () => jetskisService.reactivate(jetskiId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['jetski', jetskiId] })
      queryClient.invalidateQueries({ queryKey: ['jetskis'] })
    },
  })

  if (!currentTenant) {
    return (
      <div className="flex h-[50vh] items-center justify-center">
        <p className="text-muted-foreground">Selecione um tenant para continuar</p>
      </div>
    )
  }

  if (jetskiLoading) {
    return (
      <div className="space-y-6">
        <Skeleton className="h-8 w-48" />
        <div className="grid gap-4 md:grid-cols-3">
          <Skeleton className="h-32" />
          <Skeleton className="h-32" />
          <Skeleton className="h-32" />
        </div>
        <Skeleton className="h-96" />
      </div>
    )
  }

  if (!jetski) {
    return (
      <div className="flex h-[50vh] flex-col items-center justify-center gap-4">
        <Ship className="h-12 w-12 text-muted-foreground" />
        <p className="text-muted-foreground">Jetski não encontrado</p>
        <Button variant="outline" onClick={() => router.push('/dashboard/jetskis')}>
          <ArrowLeft className="mr-2 h-4 w-4" />
          Voltar
        </Button>
      </div>
    )
  }

  const StatusIcon = statusConfig[jetski.status].icon

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-4">
          <Button variant="ghost" size="icon" onClick={() => router.push('/dashboard/jetskis')}>
            <ArrowLeft className="h-5 w-5" />
          </Button>
          <div>
            <h1 className="text-3xl font-bold flex items-center gap-3">
              {jetski.serie}
              <Badge variant={statusConfig[jetski.status].variant} className="text-sm">
                <StatusIcon className="mr-1 h-3 w-3" />
                {statusConfig[jetski.status].label}
              </Badge>
              {!jetski.ativo && (
                <Badge variant="destructive" className="text-sm">
                  <PowerOff className="mr-1 h-3 w-3" />
                  Desativado
                </Badge>
              )}
            </h1>
            <p className="text-muted-foreground">{modelo?.nome || 'Carregando modelo...'}</p>
          </div>
        </div>
        <div className="flex gap-2">
          <Button variant="outline" onClick={() => setStatusDialogOpen(true)} disabled={!jetski.ativo}>
            <Settings className="mr-2 h-4 w-4" />
            Alterar Status
          </Button>
          <Button variant="outline" onClick={() => setEditDialogOpen(true)}>
            <Edit className="mr-2 h-4 w-4" />
            Editar
          </Button>
          {jetski.ativo ? (
            <Button
              variant="destructive"
              onClick={() => deactivateMutation.mutate()}
              disabled={deactivateMutation.isPending}
            >
              <PowerOff className="mr-2 h-4 w-4" />
              Desativar
            </Button>
          ) : (
            <Button
              onClick={() => reactivateMutation.mutate()}
              disabled={reactivateMutation.isPending}
            >
              <Power className="mr-2 h-4 w-4" />
              Reativar
            </Button>
          )}
        </div>
      </div>

      {/* Stats Cards */}
      <div className="grid gap-4 md:grid-cols-4">
        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">Horímetro</CardTitle>
            <Clock className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">{jetski.horimetroAtual}h</div>
            <p className="text-xs text-muted-foreground">Horas de uso total</p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">Ano</CardTitle>
            <Calendar className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">{jetski.ano || '-'}</div>
            <p className="text-xs text-muted-foreground">Ano de fabricação</p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">Locações</CardTitle>
            <History className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">{locacoes?.length || 0}</div>
            <p className="text-xs text-muted-foreground">Total de locações</p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">Manutenções</CardTitle>
            <Wrench className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">{manutencoes?.length || 0}</div>
            <p className="text-xs text-muted-foreground">Ordens de serviço</p>
          </CardContent>
        </Card>
      </div>

      {/* Tabs */}
      <Tabs defaultValue="overview" className="space-y-4">
        <TabsList>
          <TabsTrigger value="overview">Visão Geral</TabsTrigger>
          <TabsTrigger value="locacoes">
            Locações ({locacoes?.length || 0})
          </TabsTrigger>
          <TabsTrigger value="manutencoes">
            Manutenções ({manutencoes?.length || 0})
          </TabsTrigger>
        </TabsList>

        {/* Overview Tab */}
        <TabsContent value="overview" className="space-y-4">
          <div className="grid gap-4 md:grid-cols-2">
            <Card>
              <CardHeader>
                <CardTitle>Informações do Jetski</CardTitle>
                <CardDescription>Dados cadastrais da embarcação</CardDescription>
              </CardHeader>
              <CardContent className="space-y-4">
                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <p className="text-sm text-muted-foreground">Número de Série</p>
                    <p className="font-medium">{jetski.serie}</p>
                  </div>
                  <div>
                    <p className="text-sm text-muted-foreground">Status</p>
                    <Badge variant={statusConfig[jetski.status].variant}>
                      {statusConfig[jetski.status].label}
                    </Badge>
                  </div>
                  <div>
                    <p className="text-sm text-muted-foreground">Ano de Fabricação</p>
                    <p className="font-medium">{jetski.ano || 'Não informado'}</p>
                  </div>
                  <div>
                    <p className="text-sm text-muted-foreground">Horímetro Atual</p>
                    <p className="font-medium">{jetski.horimetroAtual} horas</p>
                  </div>
                  <div>
                    <p className="text-sm text-muted-foreground">Cadastrado em</p>
                    <p className="font-medium">
                      {jetski.createdAt ? format(new Date(jetski.createdAt), "dd/MM/yyyy 'às' HH:mm", { locale: ptBR }) : '-'}
                    </p>
                  </div>
                  <div>
                    <p className="text-sm text-muted-foreground">Última Atualização</p>
                    <p className="font-medium">
                      {jetski.updatedAt ? format(new Date(jetski.updatedAt), "dd/MM/yyyy 'às' HH:mm", { locale: ptBR }) : '-'}
                    </p>
                  </div>
                </div>
              </CardContent>
            </Card>

            <Card>
              <CardHeader>
                <CardTitle>Modelo</CardTitle>
                <CardDescription>Informações do modelo da embarcação</CardDescription>
              </CardHeader>
              <CardContent className="space-y-4">
                {modelo ? (
                  <>
                    {modelo.fotoReferenciaUrl && (
                      <div className="aspect-video w-full overflow-hidden rounded-lg bg-muted">
                        <img
                          src={modelo.fotoReferenciaUrl}
                          alt={modelo.nome}
                          className="h-full w-full object-contain"
                        />
                      </div>
                    )}
                    <div className="grid grid-cols-2 gap-4">
                      <div>
                        <p className="text-sm text-muted-foreground">Nome</p>
                        <p className="font-medium">{modelo.nome}</p>
                      </div>
                      <div>
                        <p className="text-sm text-muted-foreground">Fabricante</p>
                        <p className="font-medium">{modelo.fabricante || '-'}</p>
                      </div>
                      <div>
                        <p className="text-sm text-muted-foreground">Capacidade</p>
                        <p className="font-medium">{modelo.capacidadePessoas} pessoas</p>
                      </div>
                      <div>
                        <p className="text-sm text-muted-foreground">Preço/Hora</p>
                        <p className="font-medium">
                          {new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(modelo.precoBaseHora)}
                        </p>
                      </div>
                    </div>
                  </>
                ) : (
                  <div className="flex h-32 items-center justify-center">
                    <p className="text-muted-foreground">Carregando modelo...</p>
                  </div>
                )}
              </CardContent>
            </Card>
          </div>
        </TabsContent>

        {/* Locacoes Tab */}
        <TabsContent value="locacoes" className="space-y-4">
          <Card>
            <CardHeader>
              <CardTitle>Histórico de Locações</CardTitle>
              <CardDescription>Todas as locações realizadas com este jetski</CardDescription>
            </CardHeader>
            <CardContent>
              {locacoesLoading ? (
                <div className="space-y-2">
                  {[...Array(3)].map((_, i) => (
                    <Skeleton key={i} className="h-12 w-full" />
                  ))}
                </div>
              ) : locacoes?.length === 0 ? (
                <div className="flex h-32 flex-col items-center justify-center gap-2">
                  <History className="h-8 w-8 text-muted-foreground" />
                  <p className="text-muted-foreground">Nenhuma locação registrada</p>
                </div>
              ) : (
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Data Check-in</TableHead>
                      <TableHead>Data Check-out</TableHead>
                      <TableHead>Cliente</TableHead>
                      <TableHead>Duração</TableHead>
                      <TableHead>Valor</TableHead>
                      <TableHead>Status</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {locacoes?.map((locacao) => (
                      <TableRow key={locacao.id}>
                        <TableCell>
                          {format(new Date(locacao.dataCheckIn), "dd/MM/yy HH:mm", { locale: ptBR })}
                        </TableCell>
                        <TableCell>
                          {locacao.dataCheckOut
                            ? format(new Date(locacao.dataCheckOut), "dd/MM/yy HH:mm", { locale: ptBR })
                            : '-'}
                        </TableCell>
                        <TableCell>{locacao.clienteNome || '-'}</TableCell>
                        <TableCell>
                          {locacao.minutosUsados ? `${Math.floor(locacao.minutosUsados / 60)}h ${locacao.minutosUsados % 60}min` : '-'}
                        </TableCell>
                        <TableCell>
                          {locacao.valorTotal
                            ? new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(locacao.valorTotal)
                            : '-'}
                        </TableCell>
                        <TableCell>
                          <Badge variant={locacaoStatusConfig[locacao.status]?.variant || 'default'}>
                            {locacaoStatusConfig[locacao.status]?.label || locacao.status}
                          </Badge>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              )}
            </CardContent>
          </Card>
        </TabsContent>

        {/* Manutencoes Tab */}
        <TabsContent value="manutencoes" className="space-y-4">
          <Card>
            <CardHeader>
              <CardTitle>Histórico de Manutenções</CardTitle>
              <CardDescription>Todas as ordens de serviço deste jetski</CardDescription>
            </CardHeader>
            <CardContent>
              {manutencoesLoading ? (
                <div className="space-y-2">
                  {[...Array(3)].map((_, i) => (
                    <Skeleton key={i} className="h-12 w-full" />
                  ))}
                </div>
              ) : manutencoes?.length === 0 ? (
                <div className="flex h-32 flex-col items-center justify-center gap-2">
                  <Wrench className="h-8 w-8 text-muted-foreground" />
                  <p className="text-muted-foreground">Nenhuma manutenção registrada</p>
                </div>
              ) : (
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Abertura</TableHead>
                      <TableHead>Tipo</TableHead>
                      <TableHead>Descrição</TableHead>
                      <TableHead>Horímetro</TableHead>
                      <TableHead>Valor</TableHead>
                      <TableHead>Status</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {manutencoes?.map((manutencao) => (
                      <TableRow key={manutencao.id}>
                        <TableCell>
                          {format(new Date(manutencao.dtAbertura), "dd/MM/yy", { locale: ptBR })}
                        </TableCell>
                        <TableCell className="capitalize">
                          {manutencao.tipo.toLowerCase().replace('_', ' ')}
                        </TableCell>
                        <TableCell className="max-w-[200px] truncate" title={manutencao.descricaoProblema}>
                          {manutencao.descricaoProblema}
                        </TableCell>
                        <TableCell>
                          {manutencao.horimetroAbertura ? `${manutencao.horimetroAbertura}h` : '-'}
                        </TableCell>
                        <TableCell>
                          {manutencao.valorTotal
                            ? new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(manutencao.valorTotal)
                            : '-'}
                        </TableCell>
                        <TableCell>
                          <Badge variant={manutencaoStatusConfig[manutencao.status]?.variant || 'default'}>
                            {manutencaoStatusConfig[manutencao.status]?.label || manutencao.status}
                          </Badge>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              )}
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>

      {/* Dialogs */}
      <JetskiEditDialog jetski={jetski} open={editDialogOpen} onOpenChange={setEditDialogOpen} />
      <StatusChangeDialog jetski={jetski} open={statusDialogOpen} onOpenChange={setStatusDialogOpen} />
    </div>
  )
}
