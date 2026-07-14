'use client'

import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import Link from 'next/link'
import { Plus, GraduationCap, Edit, MoreHorizontal, Handshake } from 'lucide-react'
import { toast } from 'sonner'
import { useTenantStore } from '@/lib/store/tenant-store'
import { instrutoresService, emissaoDelegadaService } from '@/lib/api/services'
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

  // Emissão delegada (V048): quem assina o 5-B-1 é instrutor da EAMA parceira.
  // A página vira visão informativa — os da EAMA em destaque, os próprios
  // aparecem desativados (não são usados na emissão delegada).
  const emissaoDelegada =
    !!currentTenant?.modulos && !currentTenant.modulos.includes('EMISSAO_PROPRIA')

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

  if (emissaoDelegada) {
    return <InstrutoresDelegadaView proprios={instrutores} carregandoProprios={isLoading} />
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

/**
 * Visão da OPERADORA (emissão delegada): os instrutores que assinam seus
 * documentos são da EAMA parceira — aqui ela vê quem está disponível
 * (respeitando a designação da EAMA) e os instrutores próprios aparecem
 * desativados, sem ações (não são usados na emissão delegada).
 */
function InstrutoresDelegadaView({
  proprios,
  carregandoProprios,
}: {
  proprios?: Instrutor[]
  carregandoProprios: boolean
}) {
  const {
    data: parceiros,
    isLoading: carregandoParceiros,
    error: erroParceria,
  } = useQuery({
    queryKey: ['instrutores-parceiro'],
    queryFn: () => emissaoDelegadaService.instrutoresParceiro(),
    retry: false,
  })
  const { data: vinculos } = useQuery({
    queryKey: ['vinculos-emissao'],
    queryFn: () => emissaoDelegadaService.listVinculos(),
    retry: false,
  })

  const semParceria = !!erroParceria
  const nomeEama = (vinculos ?? []).find(
    (v) => v.papel === 'OPERADORA' && (v.status === 'ATIVO' || v.status === 'BLOQUEADO')
  )?.parceiroNome

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-3">
        <GraduationCap className="h-7 w-7 text-primary" />
        <div>
          <h1 className="text-2xl font-bold">Instrutores</h1>
          <p className="text-sm text-muted-foreground">
            Atestado de Demonstração (Anexo 5-B-1, CHA-MTA-E).
          </p>
        </div>
      </div>

      <div className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-primary/30 bg-primary/5 p-4">
        <div className="flex items-start gap-3">
          <Handshake className="mt-0.5 h-5 w-5 shrink-0 text-primary" />
          <div className="text-sm">
            <p className="font-medium">Sua emissão é delegada a uma EAMA parceira.</p>
            <p className="text-muted-foreground">
              Quem assina o Atestado de Demonstração é sempre um instrutor <b>da EAMA</b>,
              designado por ela — o cadastro e os dados (CPF, CHA, assinatura) ficam com a
              emissora e entram direto no documento.
            </p>
          </div>
        </div>
        <Button asChild variant="outline" size="sm">
          <Link href="/dashboard/emissao-delegada">
            <Handshake className="mr-1 h-4 w-4" /> Ver parceria
          </Link>
        </Button>
      </div>

      <div className="space-y-2">
        <h2 className="text-sm font-semibold">
          Disponíveis para suas emissões
          {nomeEama ? <span className="text-muted-foreground"> — instrutores de {nomeEama}</span> : null}
        </h2>
        {carregandoParceiros ? (
          <Skeleton className="h-16 w-full" />
        ) : semParceria ? (
          <div className="rounded-lg border border-dashed p-6 text-center text-sm text-muted-foreground">
            Não há parceria de emissão ativa com uma EAMA.{' '}
            <Link href="/dashboard/emissao-delegada" className="font-medium text-primary hover:underline">
              Convide uma EAMA parceira →
            </Link>
          </div>
        ) : (parceiros ?? []).length === 0 ? (
          <div className="rounded-lg border border-dashed p-6 text-center text-sm text-muted-foreground">
            A EAMA parceira ainda não tem instrutores disponíveis para você — peça a ela
            para cadastrar ou designar instrutores para a parceria.
          </div>
        ) : (
          <div className="grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
            {(parceiros ?? []).map((p) => (
              <div key={p.id} className="flex items-center gap-3 rounded-lg border p-3">
                <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-primary/10">
                  <GraduationCap className="h-4 w-4 text-primary" />
                </div>
                <div className="min-w-0">
                  <p className="truncate text-sm font-medium">{p.nome}</p>
                  <Badge variant="outline" className="mt-0.5 text-[10px]">
                    Instrutor de {nomeEama ?? 'EAMA parceira'}
                  </Badge>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {(carregandoProprios || (proprios ?? []).length > 0) && (
        <div className="space-y-2">
          <h2 className="text-sm font-semibold text-muted-foreground">
            Seus instrutores — desativados na emissão delegada
          </h2>
          <p className="text-xs text-muted-foreground">
            Cadastros da sua empresa não assinam documentos enquanto a emissão for
            delegada; voltam a valer se o seu plano incluir emissão própria.
          </p>
          {carregandoProprios ? (
            <Skeleton className="h-12 w-full" />
          ) : (
            <div className="rounded-md border opacity-60">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Nome</TableHead>
                    <TableHead>CPF</TableHead>
                    <TableHead>Nº CHA</TableHead>
                    <TableHead>Status</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {(proprios ?? []).map((i) => (
                    <TableRow key={i.id} className="text-muted-foreground">
                      <TableCell className="font-medium line-through decoration-muted-foreground/40">
                        {i.nome}
                      </TableCell>
                      <TableCell>{i.cpf || '-'}</TableCell>
                      <TableCell>{i.cha || '-'}</TableCell>
                      <TableCell>
                        <Badge variant="outline">Não utilizado</Badge>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
          )}
        </div>
      )}
    </div>
  )
}
