'use client'

import { useState, useEffect } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  Users,
  Mail,
  UserPlus,
  MoreHorizontal,
  Shield,
  RefreshCw,
  X,
  Check,
  Clock,
  AlertTriangle,
  UserX,
  UserCheck,
} from 'lucide-react'

import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { Button } from '@/components/ui/button'
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
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { Skeleton } from '@/components/ui/skeleton'

import { membrosService, convitesService } from '@/lib/api/services'
import { useTenantStore } from '@/lib/store/tenant-store'
import {
  AVAILABLE_ROLES,
  type MemberSummary,
  type ConviteSummary,
  type InviteUserRequest,
} from '@/lib/api/types'
import { cn } from '@/lib/utils'

// ==========================================
// Role Badge Selector Component
// ==========================================
function RoleBadgeSelector({
  selected,
  onChange,
  disabled = false,
}: {
  selected: string[]
  onChange: (roles: string[]) => void
  disabled?: boolean
}) {
  const toggleRole = (role: string) => {
    if (disabled) return
    if (selected.includes(role)) {
      // Não permitir remover o último papel
      if (selected.length > 1) {
        onChange(selected.filter((r) => r !== role))
      }
    } else {
      onChange([...selected, role])
    }
  }

  return (
    <div className="flex flex-wrap gap-2">
      {AVAILABLE_ROLES.map((role) => {
        const isSelected = selected.includes(role.value)
        return (
          <Badge
            key={role.value}
            variant={isSelected ? 'default' : 'outline'}
            className={cn(
              'cursor-pointer transition-colors select-none',
              !disabled && 'hover:bg-primary/80',
              disabled && 'cursor-not-allowed opacity-50'
            )}
            onClick={() => toggleRole(role.value)}
            title={role.description}
          >
            {isSelected && <Check className="mr-1 h-3 w-3" />}
            {role.label}
          </Badge>
        )
      })}
    </div>
  )
}

// ==========================================
// Invite User Dialog
// ==========================================
function InviteUserDialog({
  open,
  onOpenChange,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
}) {
  const queryClient = useQueryClient()
  const [formData, setFormData] = useState<InviteUserRequest>({
    email: '',
    nome: '',
    papeis: ['OPERADOR'],
  })

  const inviteMutation = useMutation({
    mutationFn: (data: InviteUserRequest) => membrosService.invite(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['members'] })
      queryClient.invalidateQueries({ queryKey: ['invitations'] })
      onOpenChange(false)
      setFormData({ email: '', nome: '', papeis: ['OPERADOR'] })
    },
  })

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (formData.papeis.length === 0) {
      return // At least one role required
    }
    inviteMutation.mutate(formData)
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-[500px]">
        <form onSubmit={handleSubmit}>
          <DialogHeader>
            <DialogTitle>Convidar Usuário</DialogTitle>
            <DialogDescription>
              Envie um convite por email para adicionar um novo membro ao tenant.
            </DialogDescription>
          </DialogHeader>

          <div className="grid gap-4 py-4">
            <div className="grid gap-2">
              <Label htmlFor="nome">Nome *</Label>
              <Input
                id="nome"
                value={formData.nome}
                onChange={(e) => setFormData({ ...formData, nome: e.target.value })}
                placeholder="João Silva"
                required
              />
            </div>

            <div className="grid gap-2">
              <Label htmlFor="email">Email *</Label>
              <Input
                id="email"
                type="email"
                value={formData.email}
                onChange={(e) => setFormData({ ...formData, email: e.target.value })}
                placeholder="joao@empresa.com"
                required
              />
            </div>

            <div className="grid gap-2">
              <Label>Papéis *</Label>
              <p className="text-sm text-muted-foreground">
                Clique nos papéis para selecionar/deselecionar
              </p>
              <RoleBadgeSelector
                selected={formData.papeis}
                onChange={(papeis) => setFormData({ ...formData, papeis })}
              />
            </div>
          </div>

          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
              Cancelar
            </Button>
            <Button
              type="submit"
              disabled={inviteMutation.isPending || formData.papeis.length === 0}
            >
              {inviteMutation.isPending ? 'Enviando...' : 'Enviar Convite'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

// ==========================================
// Edit Roles Dialog
// ==========================================
function EditRolesDialog({
  member,
  open,
  onOpenChange,
}: {
  member: MemberSummary | null
  open: boolean
  onOpenChange: (open: boolean) => void
}) {
  const queryClient = useQueryClient()
  const [selectedRoles, setSelectedRoles] = useState<string[]>([])

  // Update selected roles when member changes
  useEffect(() => {
    if (member) {
      setSelectedRoles([...member.papeis])
    }
  }, [member])

  const updateRolesMutation = useMutation({
    mutationFn: ({ usuarioId, papeis }: { usuarioId: string; papeis: string[] }) =>
      membrosService.updateRoles(usuarioId, { papeis }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['members'] })
      onOpenChange(false)
    },
  })

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (!member || selectedRoles.length === 0) return
    updateRolesMutation.mutate({ usuarioId: member.usuarioId, papeis: selectedRoles })
  }

  // Reset selected roles when dialog opens
  const handleOpenChange = (isOpen: boolean) => {
    if (isOpen && member) {
      setSelectedRoles([...member.papeis])
    }
    onOpenChange(isOpen)
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent className="sm:max-w-[500px]">
        <form onSubmit={handleSubmit}>
          <DialogHeader>
            <DialogTitle>Editar Papéis</DialogTitle>
            <DialogDescription>
              Altere os papéis de {member?.nome || 'usuário'}
            </DialogDescription>
          </DialogHeader>

          <div className="grid gap-4 py-4">
            <div className="grid gap-2">
              <Label>Email</Label>
              <p className="text-sm text-muted-foreground">{member?.email}</p>
            </div>

            <div className="grid gap-2">
              <Label>Papéis</Label>
              <p className="text-sm text-muted-foreground">
                Clique nos papéis para selecionar/deselecionar
              </p>
              <RoleBadgeSelector
                selected={selectedRoles}
                onChange={setSelectedRoles}
              />
            </div>
          </div>

          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
              Cancelar
            </Button>
            <Button
              type="submit"
              disabled={updateRolesMutation.isPending || selectedRoles.length === 0}
            >
              {updateRolesMutation.isPending ? 'Salvando...' : 'Salvar'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

// ==========================================
// Main Page Component
// ==========================================
export default function UsuariosPage() {
  const { currentTenant } = useTenantStore()
  const queryClient = useQueryClient()

  // State
  const [activeTab, setActiveTab] = useState('membros')
  const [inviteDialogOpen, setInviteDialogOpen] = useState(false)
  const [editRolesDialogOpen, setEditRolesDialogOpen] = useState(false)
  const [selectedMember, setSelectedMember] = useState<MemberSummary | null>(null)
  const [showInactive, setShowInactive] = useState(false)

  // Queries
  const { data: membersData, isLoading: loadingMembers } = useQuery({
    queryKey: ['members', currentTenant?.id, showInactive],
    queryFn: () => membrosService.list(showInactive),
    enabled: !!currentTenant,
  })

  const { data: invitations, isLoading: loadingInvitations } = useQuery({
    queryKey: ['invitations', currentTenant?.id],
    queryFn: () => convitesService.listPending(),
    enabled: !!currentTenant && activeTab === 'convites',
  })

  // Mutations
  const deactivateMutation = useMutation({
    mutationFn: (usuarioId: string) => membrosService.deactivate(usuarioId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['members'] })
    },
  })

  const reactivateMutation = useMutation({
    mutationFn: (usuarioId: string) => membrosService.reactivate(usuarioId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['members'] })
    },
  })

  const resendInvitationMutation = useMutation({
    mutationFn: (conviteId: string) => convitesService.resend(conviteId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['invitations'] })
    },
  })

  const cancelInvitationMutation = useMutation({
    mutationFn: (conviteId: string) => convitesService.cancel(conviteId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['invitations'] })
    },
  })

  // Handlers
  const handleEditRoles = (member: MemberSummary) => {
    setSelectedMember(member)
    setEditRolesDialogOpen(true)
  }

  const handleDeactivate = (member: MemberSummary) => {
    if (confirm(`Deseja desativar ${member.nome}?`)) {
      deactivateMutation.mutate(member.usuarioId)
    }
  }

  const handleReactivate = (member: MemberSummary) => {
    reactivateMutation.mutate(member.usuarioId)
  }

  const handleResendInvitation = (convite: ConviteSummary) => {
    resendInvitationMutation.mutate(convite.id)
  }

  const handleCancelInvitation = (convite: ConviteSummary) => {
    if (confirm(`Deseja cancelar o convite para ${convite.email}?`)) {
      cancelInvitationMutation.mutate(convite.id)
    }
  }

  // Helper to format relative time
  const formatRelativeTime = (dateStr: string) => {
    const date = new Date(dateStr)
    const now = new Date()
    const diffMs = date.getTime() - now.getTime()
    const diffHours = Math.round(diffMs / (1000 * 60 * 60))

    if (diffHours < 0) {
      return 'Expirado'
    } else if (diffHours < 24) {
      return `${diffHours}h restantes`
    } else {
      const diffDays = Math.round(diffHours / 24)
      return `${diffDays}d restantes`
    }
  }

  // Get role label
  const getRoleLabel = (roleValue: string) => {
    const role = AVAILABLE_ROLES.find((r) => r.value === roleValue)
    return role?.label || roleValue
  }

  // No tenant selected
  if (!currentTenant) {
    return (
      <div className="flex h-[50vh] items-center justify-center">
        <div className="text-center">
          <AlertTriangle className="mx-auto h-12 w-12 text-yellow-500" />
          <h2 className="mt-4 text-xl font-semibold">Nenhum tenant selecionado</h2>
          <p className="mt-2 text-muted-foreground">
            Selecione um tenant no menu lateral para continuar.
          </p>
        </div>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold">Usuários</h1>
          <p className="text-muted-foreground">
            Gerencie os membros e permissões do {currentTenant.razaoSocial}
          </p>
        </div>
        <Button onClick={() => setInviteDialogOpen(true)}>
          <UserPlus className="mr-2 h-4 w-4" />
          Convidar Usuário
        </Button>
      </div>

      {/* Plan Limit Info */}
      {membersData?.planLimit && (
        <div className="rounded-lg border p-4 bg-muted/50">
          <div className="flex items-center justify-between">
            <p className="text-sm">
              <strong>{membersData.planLimit.currentActive}</strong> de{' '}
              <strong>{membersData.planLimit.maxUsuarios}</strong> usuários ativos
              {membersData.planLimit.available > 0 && (
                <span className="text-muted-foreground">
                  {' '}
                  ({membersData.planLimit.available} disponíveis)
                </span>
              )}
            </p>
            {membersData.planLimit.limitReached && (
              <Badge variant="destructive">Limite atingido</Badge>
            )}
          </div>
        </div>
      )}

      {/* Tabs */}
      <Tabs value={activeTab} onValueChange={setActiveTab}>
        <TabsList>
          <TabsTrigger value="membros">
            <Users className="mr-2 h-4 w-4" />
            Membros ({membersData?.activeCount || 0})
          </TabsTrigger>
          <TabsTrigger value="convites">
            <Mail className="mr-2 h-4 w-4" />
            Convites Pendentes ({invitations?.length || 0})
          </TabsTrigger>
        </TabsList>

        {/* Members Tab */}
        <TabsContent value="membros" className="space-y-4">
          {/* Filters */}
          <div className="flex items-center gap-4">
            <label className="flex items-center gap-2 text-sm">
              <input
                type="checkbox"
                checked={showInactive}
                onChange={(e) => setShowInactive(e.target.checked)}
                className="rounded"
              />
              Mostrar inativos
            </label>
          </div>

          {/* Members Table */}
          <div className="rounded-md border">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Nome</TableHead>
                  <TableHead>Email</TableHead>
                  <TableHead>Papéis</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead className="w-[70px]"></TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {loadingMembers ? (
                  Array.from({ length: 5 }).map((_, i) => (
                    <TableRow key={i}>
                      <TableCell><Skeleton className="h-4 w-32" /></TableCell>
                      <TableCell><Skeleton className="h-4 w-48" /></TableCell>
                      <TableCell><Skeleton className="h-4 w-24" /></TableCell>
                      <TableCell><Skeleton className="h-4 w-16" /></TableCell>
                      <TableCell><Skeleton className="h-4 w-8" /></TableCell>
                    </TableRow>
                  ))
                ) : membersData?.members.length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={5} className="h-24 text-center">
                      <div className="flex flex-col items-center gap-2">
                        <Users className="h-8 w-8 text-muted-foreground" />
                        <p className="text-muted-foreground">Nenhum membro encontrado</p>
                      </div>
                    </TableCell>
                  </TableRow>
                ) : (
                  membersData?.members.map((member) => (
                    <TableRow key={member.usuarioId} className={!member.ativo ? 'opacity-60' : ''}>
                      <TableCell className="font-medium">{member.nome}</TableCell>
                      <TableCell>{member.email}</TableCell>
                      <TableCell>
                        <div className="flex flex-wrap gap-1">
                          {member.papeis.slice(0, 2).map((papel) => (
                            <Badge key={papel} variant="secondary" className="text-xs">
                              {getRoleLabel(papel)}
                            </Badge>
                          ))}
                          {member.papeis.length > 2 && (
                            <Badge variant="outline" className="text-xs">
                              +{member.papeis.length - 2}
                            </Badge>
                          )}
                        </div>
                      </TableCell>
                      <TableCell>
                        <Badge variant={member.ativo ? 'default' : 'secondary'}>
                          {member.ativo ? 'Ativo' : 'Inativo'}
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
                            <DropdownMenuItem
                              onClick={() => handleEditRoles(member)}
                              disabled={!member.ativo}
                            >
                              <Shield className="mr-2 h-4 w-4" />
                              Editar Papéis
                            </DropdownMenuItem>
                            <DropdownMenuSeparator />
                            {member.ativo ? (
                              <DropdownMenuItem
                                onClick={() => handleDeactivate(member)}
                                className="text-destructive"
                              >
                                <UserX className="mr-2 h-4 w-4" />
                                Desativar
                              </DropdownMenuItem>
                            ) : (
                              <DropdownMenuItem
                                onClick={() => handleReactivate(member)}
                                disabled={membersData?.planLimit.limitReached}
                              >
                                <UserCheck className="mr-2 h-4 w-4" />
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
        </TabsContent>

        {/* Invitations Tab */}
        <TabsContent value="convites" className="space-y-4">
          <div className="rounded-md border">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Nome</TableHead>
                  <TableHead>Email</TableHead>
                  <TableHead>Papéis</TableHead>
                  <TableHead>Expira em</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead className="w-[70px]"></TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {loadingInvitations ? (
                  Array.from({ length: 3 }).map((_, i) => (
                    <TableRow key={i}>
                      <TableCell><Skeleton className="h-4 w-32" /></TableCell>
                      <TableCell><Skeleton className="h-4 w-48" /></TableCell>
                      <TableCell><Skeleton className="h-4 w-24" /></TableCell>
                      <TableCell><Skeleton className="h-4 w-20" /></TableCell>
                      <TableCell><Skeleton className="h-4 w-16" /></TableCell>
                      <TableCell><Skeleton className="h-4 w-8" /></TableCell>
                    </TableRow>
                  ))
                ) : invitations?.length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={6} className="h-24 text-center">
                      <div className="flex flex-col items-center gap-2">
                        <Mail className="h-8 w-8 text-muted-foreground" />
                        <p className="text-muted-foreground">Nenhum convite pendente</p>
                      </div>
                    </TableCell>
                  </TableRow>
                ) : (
                  invitations?.map((convite) => (
                    <TableRow key={convite.id}>
                      <TableCell className="font-medium">{convite.nome}</TableCell>
                      <TableCell>{convite.email}</TableCell>
                      <TableCell>
                        <div className="flex flex-wrap gap-1">
                          {convite.papeis.slice(0, 2).map((papel) => (
                            <Badge key={papel} variant="secondary" className="text-xs">
                              {getRoleLabel(papel)}
                            </Badge>
                          ))}
                          {convite.papeis.length > 2 && (
                            <Badge variant="outline" className="text-xs">
                              +{convite.papeis.length - 2}
                            </Badge>
                          )}
                        </div>
                      </TableCell>
                      <TableCell>
                        <div className="flex items-center gap-1 text-sm">
                          <Clock className="h-3 w-3" />
                          {formatRelativeTime(convite.expiresAt)}
                        </div>
                      </TableCell>
                      <TableCell>
                        <Badge
                          variant={convite.status === 'EXPIRED' ? 'destructive' : 'secondary'}
                        >
                          {convite.status === 'EXPIRED' ? 'Expirado' : 'Pendente'}
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
                            <DropdownMenuItem
                              onClick={() => handleResendInvitation(convite)}
                              disabled={resendInvitationMutation.isPending}
                            >
                              <RefreshCw className="mr-2 h-4 w-4" />
                              Reenviar
                            </DropdownMenuItem>
                            <DropdownMenuSeparator />
                            <DropdownMenuItem
                              onClick={() => handleCancelInvitation(convite)}
                              className="text-destructive"
                              disabled={cancelInvitationMutation.isPending}
                            >
                              <X className="mr-2 h-4 w-4" />
                              Cancelar
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
        </TabsContent>
      </Tabs>

      {/* Dialogs */}
      <InviteUserDialog open={inviteDialogOpen} onOpenChange={setInviteDialogOpen} />
      <EditRolesDialog
        member={selectedMember}
        open={editRolesDialogOpen}
        onOpenChange={setEditRolesDialogOpen}
      />
    </div>
  )
}
