'use client'

import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import Link from 'next/link'
import { Plus, Search, MoreHorizontal, Users, Edit, Phone, Mail, Send, FileText } from 'lucide-react'
import { toast } from 'sonner'
import { useTenantStore } from '@/lib/store/tenant-store'
import { clientesService, claimService } from '@/lib/api/services'
import type { Cliente, ClienteCreateRequest } from '@/lib/api/types'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { PhoneInput } from '@/components/ui/phone-input'
import { CONTA_BADGE, ORIGEM_BADGE } from '@/components/clientes/badges'
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
import { ClienteDetailSheet } from '@/components/clientes/cliente-detail-sheet'
import { WhatsAppLink } from '@/components/whatsapp-link'

/** Máscara visual de CPF (000.000.000-00) — envia só os dígitos formatados. */
function formatCpf(v: string): string {
  const d = v.replace(/\D/g, '').slice(0, 11)
  return d
    .replace(/(\d{3})(\d)/, '$1.$2')
    .replace(/(\d{3})\.(\d{3})(\d)/, '$1.$2.$3')
    .replace(/\.(\d{3})(\d{1,2})$/, '.$1-$2')
}

function extractErrorMessage(e: unknown): string | undefined {
  const data = (e as { response?: { data?: { message?: string; errors?: Record<string, string> } } })
    ?.response?.data
  if (data?.message) return data.message
  if (data?.errors) return Object.values(data.errors).join('; ')
  return undefined
}

/**
 * Cadastro de cliente fora do balcão = captura de LEAD (ex.: operador na praia):
 * rápido, mobile-first, cria pré-conta com dedupe por CPF e auto-convite ao
 * portal quando houver e-mail. A ficha completa (documentos NORMAM, endereço,
 * anexos) é preenchida depois, no balcão, quando o lead vier alugar.
 */
function ClienteFormDialog({
  cliente,
  open,
  onOpenChange,
}: {
  cliente?: Cliente
  open: boolean
  onOpenChange: (open: boolean) => void
}) {
  const queryClient = useQueryClient()

  const [formData, setFormData] = useState<ClienteCreateRequest>({
    nome: cliente?.nome || '',
    email: cliente?.email || '',
    telefone: cliente?.telefone || '',
    documento: cliente?.documento || '',
    observacoes: cliente?.observacoes || '',
  })
  const [duplicado, setDuplicado] = useState<Cliente | null>(null)

  // Dedupe por CPF ao sair do campo (mesmo caminho do balcão)
  const verificarCpf = async (documento?: string) => {
    if (!documento || cliente) return
    const existente = await clientesService.buscarPorCpf(documento).catch(() => null)
    setDuplicado(existente ?? null)
  }

  const createMutation = useMutation({
    mutationFn: (data: ClienteCreateRequest) =>
      clientesService.criarPreConta(
        {
          nome: data.nome,
          documento: data.documento || undefined,
          email: data.email || undefined,
          telefone: data.telefone || undefined,
          observacoes: data.observacoes || undefined,
        },
        'LEAD'
      ),
    onSuccess: (criado, vars) => {
      queryClient.invalidateQueries({ queryKey: ['clientes'] })
      toast.success(
        vars.email
          ? `Lead ${criado.nome} registrado — convite do portal enviado por e-mail.`
          : `Lead ${criado.nome} registrado.`
      )
      onOpenChange(false)
    },
    onError: (e: unknown) => toast.error(extractErrorMessage(e) ?? 'Falha ao registrar o lead.'),
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: string; data: Partial<ClienteCreateRequest> }) =>
      clientesService.update(id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['clientes'] })
      toast.success('Cliente atualizado.')
      onOpenChange(false)
    },
    onError: (e: unknown) => toast.error(extractErrorMessage(e) ?? 'Falha ao salvar o cliente.'),
  })

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (cliente) {
      updateMutation.mutate({ id: cliente.id, data: formData })
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
            <DialogTitle>{cliente ? 'Editar Cliente' : 'Novo lead'}</DialogTitle>
            <DialogDescription>
              {cliente
                ? 'Atualize os dados do cliente'
                : 'Registro rápido de interessado — a ficha completa é feita no balcão, na hora do aluguel'}
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
              <Label htmlFor="telefone">Celular / WhatsApp</Label>
              <PhoneInput
                value={formData.telefone || ''}
                onChange={(v) => setFormData({ ...formData, telefone: v })}
              />
            </div>

            <div className="grid gap-2">
              <Label htmlFor="email">E-mail</Label>
              <Input
                id="email"
                type="email"
                value={formData.email || ''}
                onChange={(e) => setFormData({ ...formData, email: e.target.value })}
                placeholder="email@exemplo.com"
              />
              {!cliente && (
                <p className="text-xs text-muted-foreground">
                  Com e-mail, o cliente recebe automaticamente o convite do portal.
                </p>
              )}
            </div>

            <div className="grid gap-2">
              <Label htmlFor="documento">CPF</Label>
              <Input
                id="documento"
                inputMode="numeric"
                value={formData.documento || ''}
                onChange={(e) => {
                  setFormData({ ...formData, documento: formatCpf(e.target.value) })
                  if (duplicado) setDuplicado(null)
                }}
                onBlur={() => verificarCpf(formData.documento)}
                placeholder="000.000.000-00"
              />
              {duplicado && (
                <p className="text-xs font-medium text-amber-600 dark:text-amber-400">
                  Já existe um cliente com este CPF: {duplicado.nome}. Use a busca da lista para
                  encontrá-lo em vez de duplicar.
                </p>
              )}
            </div>

            <div className="grid gap-2">
              <Label htmlFor="observacoes">Observações</Label>
              <Textarea
                id="observacoes"
                rows={2}
                value={formData.observacoes || ''}
                onChange={(e) => setFormData({ ...formData, observacoes: e.target.value })}
                placeholder={cliente ? 'Observações gerais' : 'Ex.: abordado na praia, interessado no fim de semana'}
              />
            </div>
          </div>

          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
              Cancelar
            </Button>
            <Button type="submit" disabled={isLoading || (!cliente && !!duplicado)}>
              {isLoading ? 'Salvando...' : cliente ? 'Salvar' : 'Registrar lead'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

export default function ClientesPage() {
  const { currentTenant } = useTenantStore()

  const [search, setSearch] = useState('')
  const [dialogOpen, setDialogOpen] = useState(false)
  const [editingCliente, setEditingCliente] = useState<Cliente | undefined>()
  const [detailCliente, setDetailCliente] = useState<Cliente | null>(null)
  const [detailOpen, setDetailOpen] = useState(false)

  const { data: clientes, isLoading } = useQuery({
    queryKey: ['clientes', currentTenant?.id],
    queryFn: () => clientesService.list(),
    enabled: !!currentTenant,
  })

  const reenviarClaim = useMutation({
    mutationFn: (clienteId: string) => claimService.reenviar(clienteId, 'email'),
    onSuccess: () => toast.success('Link de ativação reenviado ao cliente.'),
    onError: (e: unknown) => {
      const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
      toast.error(msg ?? 'Falha ao reenviar ativação.')
    },
  })

  // Filter clients locally based on search
  const filteredClientes = clientes?.filter((cliente) =>
    cliente.nome?.toLowerCase().includes(search.toLowerCase()) ||
    cliente.email?.toLowerCase().includes(search.toLowerCase()) ||
    cliente.telefone?.includes(search)
  )

  const handleEdit = (cliente: Cliente) => {
    setEditingCliente(cliente)
    setDialogOpen(true)
  }

  const handleNew = () => {
    setEditingCliente(undefined)
    setDialogOpen(true)
  }

  const abrirDetalhe = (cliente: Cliente) => {
    setDetailCliente(cliente)
    setDetailOpen(true)
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
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-2xl font-bold sm:text-3xl">Clientes</h1>
          <p className="text-muted-foreground">
            Clique em um cliente para ver fotos, comprovantes e histórico de passeios
          </p>
        </div>
        <Button onClick={handleNew} className="w-full sm:w-auto">
          <Plus className="mr-2 h-4 w-4" />
          Novo Cliente
        </Button>
      </div>

      <div className="flex gap-4">
        <div className="relative flex-1">
          <Search className="absolute left-2.5 top-2.5 h-4 w-4 text-muted-foreground" />
          <Input
            placeholder="Buscar por nome, email ou telefone..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="pl-8"
          />
        </div>
      </div>

      <div className="rounded-md border overflow-x-auto">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Nome</TableHead>
              <TableHead className="hidden sm:table-cell">Email</TableHead>
              <TableHead>Telefone</TableHead>
              <TableHead className="hidden sm:table-cell">CPF</TableHead>
              <TableHead className="hidden sm:table-cell">Origem</TableHead>
              <TableHead>Conta</TableHead>
              <TableHead className="w-[50px]"></TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {isLoading ? (
              Array.from({ length: 5 }).map((_, i) => (
                <TableRow key={i}>
                  <TableCell><Skeleton className="h-4 w-32" /></TableCell>
                  <TableCell className="hidden sm:table-cell"><Skeleton className="h-4 w-40" /></TableCell>
                  <TableCell><Skeleton className="h-4 w-28" /></TableCell>
                  <TableCell className="hidden sm:table-cell"><Skeleton className="h-4 w-28" /></TableCell>
                  <TableCell className="hidden sm:table-cell"><Skeleton className="h-4 w-16" /></TableCell>
                  <TableCell><Skeleton className="h-4 w-20" /></TableCell>
                  <TableCell><Skeleton className="h-8 w-8" /></TableCell>
                </TableRow>
              ))
            ) : filteredClientes?.length === 0 ? (
              <TableRow>
                <TableCell colSpan={7} className="h-24 text-center">
                  <div className="flex flex-col items-center gap-2">
                    <Users className="h-8 w-8 text-muted-foreground" />
                    <p className="text-muted-foreground">Nenhum cliente encontrado</p>
                  </div>
                </TableCell>
              </TableRow>
            ) : (
              filteredClientes?.map((cliente) => (
                <TableRow
                  key={cliente.id}
                  className="cursor-pointer"
                  onClick={() => abrirDetalhe(cliente)}
                >
                  <TableCell className="font-medium">{cliente.nome}</TableCell>
                  <TableCell className="hidden sm:table-cell">
                    {cliente.email ? (
                      <div className="flex items-center gap-2">
                        <Mail className="h-4 w-4 text-muted-foreground" />
                        {cliente.email}
                      </div>
                    ) : (
                      '-'
                    )}
                  </TableCell>
                  <TableCell>
                    {cliente.telefone || cliente.whatsapp ? (
                      <div className="flex items-center gap-2">
                        <Phone className="h-4 w-4 text-muted-foreground" />
                        {cliente.telefone || cliente.whatsapp}
                        <WhatsAppLink
                          phone={cliente.telefone || cliente.whatsapp}
                          nome={cliente.nome}
                          className="min-h-[40px] min-w-[40px] justify-center p-2 sm:min-h-0 sm:min-w-0 sm:p-0"
                        />
                      </div>
                    ) : (
                      '-'
                    )}
                  </TableCell>
                  <TableCell className="hidden sm:table-cell">{cliente.documento || '-'}</TableCell>
                  <TableCell className="hidden sm:table-cell">
                    {cliente.origem ? (
                      <Badge variant={ORIGEM_BADGE[cliente.origem].variant}>
                        {ORIGEM_BADGE[cliente.origem].label}
                      </Badge>
                    ) : (
                      '-'
                    )}
                  </TableCell>
                  <TableCell>
                    {cliente.statusConta ? (
                      <Badge variant={CONTA_BADGE[cliente.statusConta].variant}>
                        {CONTA_BADGE[cliente.statusConta].label}
                      </Badge>
                    ) : (
                      '-'
                    )}
                  </TableCell>
                  <TableCell onClick={(e) => e.stopPropagation()}>
                    <DropdownMenu>
                      <DropdownMenuTrigger asChild>
                        <Button variant="ghost" size="icon">
                          <MoreHorizontal className="h-4 w-4" />
                        </Button>
                      </DropdownMenuTrigger>
                      <DropdownMenuContent align="end">
                        <DropdownMenuItem onClick={() => handleEdit(cliente)}>
                          <Edit className="mr-2 h-4 w-4" />
                          Editar
                        </DropdownMenuItem>
                        <DropdownMenuItem asChild>
                          <Link href={`/dashboard/documentos?clienteId=${cliente.id}`}>
                            <FileText className="mr-2 h-4 w-4" />
                            Ver documentos
                          </Link>
                        </DropdownMenuItem>
                        {(cliente.statusConta === 'PRE_CONTA' ||
                          cliente.statusConta === 'CONVIDADA') &&
                          cliente.email && (
                            <DropdownMenuItem
                              onClick={() => reenviarClaim.mutate(cliente.id)}
                              disabled={reenviarClaim.isPending}
                            >
                              <Send className="mr-2 h-4 w-4" />
                              Reenviar ativação
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

      <ClienteFormDialog
        cliente={editingCliente}
        open={dialogOpen}
        onOpenChange={setDialogOpen}
      />

      <ClienteDetailSheet
        cliente={detailCliente}
        open={detailOpen}
        onOpenChange={setDetailOpen}
      />
    </div>
  )
}
