'use client'

import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Plus, GraduationCap, Edit, MoreHorizontal } from 'lucide-react'
import { toast } from 'sonner'
import { useTenantStore } from '@/lib/store/tenant-store'
import { instrutoresService } from '@/lib/api/services'
import type { Instrutor, InstrutorCreateRequest } from '@/lib/api/types'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Badge } from '@/components/ui/badge'
import { Skeleton } from '@/components/ui/skeleton'
import { SignaturePad } from '@/components/signature-pad'
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

const VAZIO: InstrutorCreateRequest = {
  nome: '',
  rg: '',
  orgaoEmissor: '',
  cpf: '',
  cha: '',
  dataEmissao: '',
}

export default function InstrutoresPage() {
  const { currentTenant } = useTenantStore()
  const queryClient = useQueryClient()
  const [open, setOpen] = useState(false)
  const [editing, setEditing] = useState<Instrutor | null>(null)
  const [form, setForm] = useState<InstrutorCreateRequest>(VAZIO)

  const { data: instrutores, isLoading } = useQuery({
    queryKey: ['instrutores', currentTenant?.id],
    queryFn: () => instrutoresService.list({ includeInactive: true }),
    enabled: !!currentTenant,
  })

  const salvar = useMutation({
    mutationFn: () =>
      editing
        ? instrutoresService.update(editing.id, form)
        : instrutoresService.create(form),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['instrutores'] })
      toast.success(editing ? 'Instrutor atualizado.' : 'Instrutor cadastrado.')
      setOpen(false)
    },
    onError: () => toast.error('Falha ao salvar instrutor.'),
  })

  const toggleAtivo = useMutation({
    mutationFn: (i: Instrutor) =>
      i.ativo ? instrutoresService.deactivate(i.id) : instrutoresService.reactivate(i.id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['instrutores'] }),
    onError: () => toast.error('Falha ao alterar status.'),
  })

  function novo() {
    setEditing(null)
    setForm(VAZIO)
    setOpen(true)
  }

  function editar(i: Instrutor) {
    setEditing(i)
    setForm({
      nome: i.nome,
      rg: i.rg ?? '',
      orgaoEmissor: i.orgaoEmissor ?? '',
      cpf: i.cpf ?? '',
      cha: i.cha ?? '',
      dataEmissao: i.dataEmissao ?? '',
    })
    setOpen(true)
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <GraduationCap className="h-7 w-7 text-primary" />
          <div>
            <h1 className="text-2xl font-bold">Instrutores (EAMA)</h1>
            <p className="text-sm text-muted-foreground">
              Cadastro para o Atestado de Demonstração (Anexo 5-B-1, CHA-MTA-E).
            </p>
          </div>
        </div>
        <Button onClick={novo}>
          <Plus className="mr-2 h-4 w-4" /> Novo instrutor
        </Button>
      </div>

      <div className="rounded-md border">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Nome</TableHead>
              <TableHead>RG</TableHead>
              <TableHead>Órgão</TableHead>
              <TableHead>CPF</TableHead>
              <TableHead>Nº CHA</TableHead>
              <TableHead>Status</TableHead>
              <TableHead className="w-[50px]" />
            </TableRow>
          </TableHeader>
          <TableBody>
            {isLoading ? (
              <TableRow>
                <TableCell colSpan={7}>
                  <Skeleton className="h-8 w-full" />
                </TableCell>
              </TableRow>
            ) : (instrutores ?? []).length === 0 ? (
              <TableRow>
                <TableCell colSpan={7} className="h-24 text-center text-muted-foreground">
                  Nenhum instrutor cadastrado.
                </TableCell>
              </TableRow>
            ) : (
              (instrutores ?? []).map((i) => (
                <TableRow key={i.id}>
                  <TableCell className="font-medium">{i.nome}</TableCell>
                  <TableCell>{i.rg || '-'}</TableCell>
                  <TableCell>{i.orgaoEmissor || '-'}</TableCell>
                  <TableCell>{i.cpf || '-'}</TableCell>
                  <TableCell>{i.cha || '-'}</TableCell>
                  <TableCell>
                    <Badge variant={i.ativo ? 'default' : 'outline'}>
                      {i.ativo ? 'Ativo' : 'Inativo'}
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
                        <DropdownMenuItem onClick={() => editar(i)}>
                          <Edit className="mr-2 h-4 w-4" /> Editar
                        </DropdownMenuItem>
                        <DropdownMenuItem onClick={() => toggleAtivo.mutate(i)}>
                          {i.ativo ? 'Desativar' : 'Reativar'}
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

      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{editing ? 'Editar instrutor' : 'Novo instrutor'}</DialogTitle>
          </DialogHeader>
          <div className="grid gap-3 sm:grid-cols-2">
            <div className="sm:col-span-2">
              <Label className="text-xs">Nome *</Label>
              <Input value={form.nome} onChange={(e) => setForm({ ...form, nome: e.target.value })} />
            </div>
            <div>
              <Label className="text-xs">RG (identidade)</Label>
              <Input value={form.rg} onChange={(e) => setForm({ ...form, rg: e.target.value })} />
            </div>
            <div>
              <Label className="text-xs">Órgão emissor</Label>
              <Input value={form.orgaoEmissor} onChange={(e) => setForm({ ...form, orgaoEmissor: e.target.value })} placeholder="SSP/RJ" />
            </div>
            <div>
              <Label className="text-xs">CPF</Label>
              <Input value={form.cpf} onChange={(e) => setForm({ ...form, cpf: e.target.value })} />
            </div>
            <div>
              <Label className="text-xs">Nº da CHA</Label>
              <Input value={form.cha} onChange={(e) => setForm({ ...form, cha: e.target.value })} />
            </div>
            <div>
              <Label className="text-xs">Data de emissão (identidade)</Label>
              <Input
                type="date"
                value={form.dataEmissao}
                onChange={(e) => setForm({ ...form, dataEmissao: e.target.value })}
              />
            </div>
          </div>

          <div>
            <Label className="mb-1 block text-xs">
              Assinatura do instrutor {editing?.temAssinatura && '(já cadastrada — assine para substituir)'}
            </Label>
            <SignaturePad onChange={(dataUrl) => setForm((s) => ({ ...s, assinaturaBase64: dataUrl ?? undefined }))} />
          </div>

          <DialogFooter>
            <Button variant="outline" onClick={() => setOpen(false)}>
              Cancelar
            </Button>
            <Button disabled={!form.nome.trim() || salvar.isPending} onClick={() => salvar.mutate()}>
              {salvar.isPending ? 'Salvando…' : 'Salvar'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
