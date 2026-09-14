'use client'

import { useState, type ReactNode } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import Link from 'next/link'
import {
  Plus,
  GraduationCap,
  Edit,
  MoreHorizontal,
  Handshake,
  Send,
  Link2,
  Copy,
  MessageCircle,
} from 'lucide-react'
import { toast } from 'sonner'
import { useTenantStore } from '@/lib/store/tenant-store'
import { usePermissions } from '@/lib/hooks/use-permissions'
import { useModoEmissao } from '@/lib/hooks/use-modo-emissao'
import { instrutoresService, emissaoDelegadaService } from '@/lib/api/services'
import type { Instrutor, InstrutorCreateRequest } from '@/lib/api/types'
import { AprovacaoInstrutorBadge } from '@/components/emissao/aprovacao-instrutor-badge'
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

const VAZIO: InstrutorCreateRequest = {
  nome: '',
  rg: '',
  orgaoEmissor: '',
  cpf: '',
  cha: '',
  dataEmissao: '',
}

function mensagemDeErro(e: unknown, padrao: string): string {
  const r = e as { response?: { data?: { message?: string } } }
  return r?.response?.data?.message ?? padrao
}

export default function InstrutoresPage() {
  const { currentTenant } = useTenantStore()
  // O cadastro do instrutor alimenta o Anexo 5-B-1 que vai à Capitania, então
  // escrever aqui é do GERENTE (rbac.rego). O OPERADOR enxerga a lista —
  // e até aqui a tela oferecia Editar/Novo a ele, que tomava 403 no Salvar.
  const { can } = usePermissions()
  const podeEscrever = can('instrutor:update') || can('instrutor:create')
  const queryClient = useQueryClient()
  const [open, setOpen] = useState(false)
  const [editing, setEditing] = useState<Instrutor | null>(null)
  const [form, setForm] = useState<InstrutorCreateRequest>(VAZIO)
  // Falha ao carregar a assinatura atual, por URL: a de um instrutor não esconde a de outro,
  // e um link novo (lista recarregada) tenta de novo sem reset manual.
  const [urlAssinaturaFalhou, setUrlAssinaturaFalhou] = useState<string | null>(null)
  const assinaturaAtualFalhou =
    !!editing?.assinaturaUrl && urlAssinaturaFalhou === editing.assinaturaUrl
  const setAssinaturaAtualFalhou = (falhou: boolean) =>
    setUrlAssinaturaFalhou(falhou ? editing?.assinaturaUrl ?? null : null)
  const [linkPara, setLinkPara] = useState<Instrutor | null>(null)

  const { data: instrutores, isLoading } = useQuery({
    queryKey: ['instrutores', currentTenant?.id],
    queryFn: () => instrutoresService.list({ includeInactive: true }),
    enabled: !!currentTenant,
  })

  // Modo de emissão (§8.M): operadora de parceria em vigor emite pela EAMA parceira,
  // qualquer que seja o plano. Nesse modo os instrutores próprios só assinam depois de
  // aprovados pela EAMA (NORMAM-212: a EAMA responde pelo instrutor — V070).
  const { delegada: emissaoDelegada, info: modo } = useModoEmissao()

  const invalidar = () => {
    queryClient.invalidateQueries({ queryKey: ['instrutores'] })
    queryClient.invalidateQueries({ queryKey: ['instrutores-operadora'] })
    queryClient.invalidateQueries({ queryKey: ['instrutores-parceiro'] })
  }

  const salvar = useMutation({
    mutationFn: () =>
      editing
        ? instrutoresService.update(editing.id, form)
        : instrutoresService.create(form),
    onSuccess: () => {
      invalidar()
      toast.success(editing ? 'Instrutor atualizado.' : 'Instrutor cadastrado.')
      setOpen(false)
    },
    onError: (e) => toast.error(mensagemDeErro(e, 'Falha ao salvar instrutor.')),
  })

  const toggleAtivo = useMutation({
    mutationFn: (i: Instrutor) =>
      i.ativo ? instrutoresService.deactivate(i.id) : instrutoresService.reactivate(i.id),
    onSuccess: invalidar,
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

  const dialogo = (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{editing ? 'Editar instrutor' : 'Novo instrutor'}</DialogTitle>
        </DialogHeader>
        {emissaoDelegada && (
          <p className="rounded-md bg-amber-50 p-2 text-xs text-amber-900">
            Na emissão delegada, a EAMA parceira responde pelo instrutor: ele só assina
            depois de aprovado por ela. Salvar envia o cadastro (ou a alteração) para a
            aprovação da EAMA.
          </p>
        )}
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

        {editing?.temAssinatura && (
          <div>
            <Label className="mb-1 block text-xs">Assinatura atual</Label>
            <div className="flex h-28 items-center justify-center rounded-md border bg-white p-2">
              {editing.assinaturaUrl && !assinaturaAtualFalhou ? (
                // eslint-disable-next-line @next/next/no-img-element -- URL pré-assinada do storage (15 min), fora do otimizador do Next
                <img
                  src={editing.assinaturaUrl}
                  alt={`Assinatura de ${editing.nome}`}
                  className="max-h-full max-w-full object-contain"
                  data-testid="instrutor-assinatura-atual"
                  onError={() => setAssinaturaAtualFalhou(true)}
                />
              ) : (
                <span className="text-xs text-muted-foreground">
                  Não foi possível carregar a imagem agora. Feche e abra o cadastro de novo.
                </span>
              )}
            </div>
          </div>
        )}

        <div>
          <Label className="mb-1 block text-xs">
            {editing?.temAssinatura
              ? 'Nova assinatura (opcional — assine abaixo só se quiser substituir a atual)'
              : 'Assinatura do instrutor'}
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
  )

  const dialogoLink = (
    <LinkAssinaturaDialog
      // key: trocar de instrutor zera o link gerado para o anterior.
      key={linkPara?.id ?? 'nenhum'}
      instrutor={linkPara}
      empresa={currentTenant?.razaoSocial ?? 'empresa'}
      emissaoDelegada={emissaoDelegada}
      onClose={() => setLinkPara(null)}
    />
  )

  if (emissaoDelegada) {
    return (
      <InstrutoresDelegadaView
        proprios={instrutores}
        carregandoProprios={isLoading}
        podeEscrever={podeEscrever}
        vinculoId={modo?.vinculoId ?? null}
        vinculoEmVigor={modo?.vinculoStatus === 'ATIVO' || modo?.vinculoStatus === 'BLOQUEADO'}
        nomeEama={modo?.emissoraNome ?? null}
        onNovo={novo}
        onEditar={editar}
        onAlternarAtivo={(i) => toggleAtivo.mutate(i)}
        onLinkAssinatura={setLinkPara}
        dialogo={
          <>
            {dialogo}
            {dialogoLink}
          </>
        }
      />
    )
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
        {podeEscrever && (
          <Button onClick={novo}>
            <Plus className="mr-2 h-4 w-4" /> Novo instrutor
          </Button>
        )}
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
                    {podeEscrever ? (
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
                          <DropdownMenuItem onClick={() => setLinkPara(i)}>
                            <Link2 className="mr-2 h-4 w-4" /> Link para assinar
                          </DropdownMenuItem>
                          <DropdownMenuItem onClick={() => toggleAtivo.mutate(i)}>
                            {i.ativo ? 'Desativar' : 'Reativar'}
                          </DropdownMenuItem>
                        </DropdownMenuContent>
                      </DropdownMenu>
                    ) : (
                      <span className="text-xs text-muted-foreground">somente leitura</span>
                    )}
                  </TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      </div>

      {dialogo}
      {dialogoLink}
    </div>
  )
}

/**
 * Link público para o instrutor assinar à distância (sem login). O link é gerado
 * só no clique — abrir o diálogo não pode invalidar um link que já foi enviado.
 */
function LinkAssinaturaDialog({
  instrutor,
  empresa,
  emissaoDelegada,
  onClose,
}: {
  instrutor: Instrutor | null
  empresa: string
  emissaoDelegada: boolean
  onClose: () => void
}) {
  const [link, setLink] = useState<{ url: string; expiraEm: string } | null>(null)

  const gerar = useMutation({
    mutationFn: () => instrutoresService.gerarLinkAssinatura(instrutor!.id),
    onSuccess: (data) => {
      setLink(data)
      toast.success('Link de assinatura gerado.')
    },
    onError: (e) => toast.error(mensagemDeErro(e, 'Não foi possível gerar o link.')),
  })

  const validade = link
    ? new Date(link.expiraEm).toLocaleString('pt-BR', {
        timeZone: 'America/Sao_Paulo',
        dateStyle: 'short',
        timeStyle: 'short',
      })
    : ''

  async function copiar() {
    if (!link) return
    try {
      await navigator.clipboard.writeText(link.url)
      toast.success('Link copiado.')
    } catch {
      toast.error('Não foi possível copiar. Selecione o link e copie manualmente.')
    }
  }

  const whatsappHref = link
    ? `https://wa.me/?text=${encodeURIComponent(
        `Olá ${instrutor?.nome ?? ''}, assine aqui para o Atestado de Demonstração da ${empresa}: ${link.url} (válido até ${validade})`
      )}`
    : '#'

  return (
    <Dialog open={!!instrutor} onOpenChange={(o) => !o && onClose()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Link para assinar</DialogTitle>
          <DialogDescription>
            Envie a {instrutor?.nome} um link para assinar pelo celular, sem precisar de login.
            A assinatura vai para o cadastro e para o Atestado de Demonstração (Anexo 5-B-1).
          </DialogDescription>
        </DialogHeader>

        {instrutor?.temAssinatura && (
          <p className="rounded-md bg-amber-50 p-2 text-xs text-amber-900">
            Este instrutor já tem assinatura cadastrada. A nova assinatura substituirá a atual
            {emissaoDelegada && ' e o cadastro volta para a aprovação da EAMA parceira'}.
          </p>
        )}

        {link ? (
          <div className="space-y-3">
            <div>
              <Label className="text-xs">Link</Label>
              <Input
                readOnly
                value={link.url}
                onFocus={(e) => e.currentTarget.select()}
                data-testid="instrutor-link-assinatura-url"
              />
              <p className="mt-1 text-xs text-muted-foreground">Válido até {validade}.</p>
            </div>
            <div className="flex flex-wrap gap-2">
              <Button variant="outline" onClick={copiar}>
                <Copy className="mr-2 h-4 w-4" /> Copiar
              </Button>
              <Button asChild className="bg-green-600 text-white hover:bg-green-700">
                <a href={whatsappHref} target="_blank" rel="noopener noreferrer">
                  <MessageCircle className="mr-2 h-4 w-4" /> Enviar pelo WhatsApp
                </a>
              </Button>
            </div>
          </div>
        ) : null}

        <p className="text-xs text-muted-foreground">
          O link é de uso único e vale por 7 dias. Gerar um novo link invalida o anterior.
        </p>

        <DialogFooter>
          <Button variant="outline" onClick={onClose}>
            Fechar
          </Button>
          <Button
            variant={link ? 'secondary' : 'default'}
            disabled={!instrutor || gerar.isPending}
            onClick={() => gerar.mutate()}
            data-testid="instrutor-link-assinatura-gerar"
          >
            <Link2 className="mr-2 h-4 w-4" />
            {gerar.isPending ? 'Gerando…' : link ? 'Gerar novo link' : 'Gerar link'}
          </Button>
        </DialogFooter>
        {link && (
          <p className="-mt-2 text-right text-xs text-amber-700">
            Atenção: gerar um novo link faz o link acima deixar de valer.
          </p>
        )}
      </DialogContent>
    </Dialog>
  )
}

/**
 * Visão da OPERADORA (emissão delegada). Assinam os documentos os instrutores que a
 * EAMA designou e os instrutores da própria operadora APROVADOS pela EAMA (V070):
 * pela NORMAM-212 a EAMA responde pelo instrutor, então é ela quem aprova ou remove.
 */
function InstrutoresDelegadaView({
  proprios,
  carregandoProprios,
  podeEscrever,
  vinculoId,
  vinculoEmVigor,
  nomeEama,
  onNovo,
  onEditar,
  onAlternarAtivo,
  onLinkAssinatura,
  dialogo,
}: {
  proprios?: Instrutor[]
  carregandoProprios: boolean
  podeEscrever: boolean
  vinculoId: string | null
  vinculoEmVigor: boolean
  nomeEama: string | null
  onNovo: () => void
  onEditar: (i: Instrutor) => void
  onAlternarAtivo: (i: Instrutor) => void
  onLinkAssinatura: (i: Instrutor) => void
  dialogo: ReactNode
}) {
  const queryClient = useQueryClient()
  const {
    data: parceiros,
    isLoading: carregandoParceiros,
    error: erroParceria,
  } = useQuery({
    queryKey: ['instrutores-parceiro'],
    queryFn: () => emissaoDelegadaService.instrutoresParceiro(),
    retry: false,
  })
  // O status da aprovação é da gestão (ADMIN/GERENTE); o OPERADOR só vê quem está disponível.
  const { data: pedidos } = useQuery({
    queryKey: ['instrutores-operadora', vinculoId],
    queryFn: () => emissaoDelegadaService.instrutoresOperadora(vinculoId!),
    enabled: !!vinculoId && podeEscrever,
    retry: false,
  })
  const pedidoPorInstrutor = new Map((pedidos ?? []).map((p) => [p.instrutorId, p]))

  const solicitar = useMutation({
    mutationFn: (instrutorId: string) => emissaoDelegadaService.solicitarAprovacao(instrutorId),
    onSuccess: () => {
      toast.success('Pedido enviado à EAMA parceira.')
      queryClient.invalidateQueries({ queryKey: ['instrutores-operadora'] })
    },
    onError: (e) => toast.error(mensagemDeErro(e, 'Não foi possível enviar o pedido à EAMA.')),
  })

  const semParceria = !!erroParceria
  const eama = nomeEama ?? 'EAMA parceira'

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-3">
          <GraduationCap className="h-7 w-7 text-primary" />
          <div>
            <h1 className="text-2xl font-bold">Instrutores</h1>
            <p className="text-sm text-muted-foreground">
              Atestado de Demonstração (Anexo 5-B-1, CHA-MTA-E).
            </p>
          </div>
        </div>
        {podeEscrever && (
          <Button onClick={onNovo}>
            <Plus className="mr-2 h-4 w-4" /> Novo instrutor
          </Button>
        )}
      </div>

      <div className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-primary/30 bg-primary/5 p-4">
        <div className="flex items-start gap-3">
          <Handshake className="mt-0.5 h-5 w-5 shrink-0 text-primary" />
          <div className="text-sm">
            <p className="font-medium">Sua emissão é delegada a {eama}.</p>
            <p className="text-muted-foreground">
              Pela NORMAM-212 a EAMA responde pelo instrutor. Assinam o Atestado de
              Demonstração os instrutores que ela designou e os <b>seus</b> instrutores
              que ela aprovou — e ela pode removê-los a qualquer momento.
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
        <h2 className="text-sm font-semibold">Disponíveis para suas emissões</h2>
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
            Ainda não há instrutores disponíveis: peça à EAMA para designar um instrutor dela
            ou aprovar um instrutor seu.
          </div>
        ) : (
          <div className="grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
            {(parceiros ?? []).map((p) => (
              <div
                key={p.id}
                data-testid="instrutores-disponivel"
                data-instrutor-id={p.id}
                data-origem={p.origem ?? 'EAMA'}
                className="flex items-center gap-3 rounded-lg border p-3"
              >
                <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-primary/10">
                  <GraduationCap className="h-4 w-4 text-primary" />
                </div>
                <div className="min-w-0">
                  <p className="truncate text-sm font-medium">{p.nome}</p>
                  <Badge variant="outline" className="mt-0.5 text-[10px]">
                    {p.origem === 'OPERADORA' ? 'Seu instrutor — aprovado pela EAMA' : `Instrutor de ${eama}`}
                  </Badge>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      <div className="space-y-2">
        <h2 className="text-sm font-semibold">Seus instrutores</h2>
        <p className="text-xs text-muted-foreground">
          Só assinam emissões depois da aprovação de {eama}. Cadastros novos e alterações vão
          automaticamente para a aprovação; para um instrutor ainda não enviado, rejeitado ou
          removido, use <b>Pedir aprovação</b>.
        </p>
        {carregandoProprios ? (
          <Skeleton className="h-12 w-full" />
        ) : (proprios ?? []).length === 0 ? (
          <div className="rounded-lg border border-dashed p-4 text-center text-sm text-muted-foreground">
            Nenhum instrutor próprio cadastrado.
          </div>
        ) : (
          <div className="rounded-md border">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Nome</TableHead>
                  <TableHead>CPF</TableHead>
                  <TableHead>Nº CHA</TableHead>
                  <TableHead>Aprovação da EAMA</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead className="text-right" />
                </TableRow>
              </TableHeader>
              <TableBody>
                {(proprios ?? []).map((i) => {
                  const pedido = pedidoPorInstrutor.get(i.id)
                  const podePedir =
                    podeEscrever && vinculoEmVigor && i.ativo &&
                    (!pedido || pedido.status === 'REJEITADO' || pedido.status === 'REMOVIDO')
                  return (
                    <TableRow
                      key={i.id}
                      data-testid="instrutores-proprio"
                      data-instrutor-id={i.id}
                      data-aprovacao={pedido?.status ?? 'NAO_ENVIADO'}
                    >
                      <TableCell className="font-medium">{i.nome}</TableCell>
                      <TableCell>{i.cpf || '-'}</TableCell>
                      <TableCell>{i.cha || '-'}</TableCell>
                      <TableCell>
                        {!podeEscrever ? (
                          <span className="text-xs text-muted-foreground">—</span>
                        ) : pedido ? (
                          <div className="space-y-0.5">
                            <AprovacaoInstrutorBadge status={pedido.status} />
                            {pedido.motivo && (
                              <p className="text-xs text-muted-foreground">Motivo: {pedido.motivo}</p>
                            )}
                          </div>
                        ) : (
                          <Badge variant="outline">Não enviado</Badge>
                        )}
                      </TableCell>
                      <TableCell>
                        <Badge variant={i.ativo ? 'default' : 'outline'}>
                          {i.ativo ? 'Ativo' : 'Inativo'}
                        </Badge>
                      </TableCell>
                      <TableCell className="space-x-1 text-right">
                        {podePedir && (
                          <Button
                            size="sm"
                            variant="outline"
                            data-testid="instrutores-proprio-solicitar"
                            onClick={() => solicitar.mutate(i.id)}
                            disabled={solicitar.isPending}
                          >
                            <Send className="mr-1 h-4 w-4" /> Pedir aprovação
                          </Button>
                        )}
                        {podeEscrever && (
                          <DropdownMenu>
                            <DropdownMenuTrigger asChild>
                              <Button variant="ghost" size="icon">
                                <MoreHorizontal className="h-4 w-4" />
                              </Button>
                            </DropdownMenuTrigger>
                            <DropdownMenuContent align="end">
                              <DropdownMenuItem onClick={() => onEditar(i)}>
                                <Edit className="mr-2 h-4 w-4" /> Editar
                              </DropdownMenuItem>
                              <DropdownMenuItem onClick={() => onLinkAssinatura(i)}>
                                <Link2 className="mr-2 h-4 w-4" /> Link para assinar
                              </DropdownMenuItem>
                              <DropdownMenuItem onClick={() => onAlternarAtivo(i)}>
                                {i.ativo ? 'Desativar' : 'Reativar'}
                              </DropdownMenuItem>
                            </DropdownMenuContent>
                          </DropdownMenu>
                        )}
                      </TableCell>
                    </TableRow>
                  )
                })}
              </TableBody>
            </Table>
          </div>
        )}
      </div>

      {dialogo}
    </div>
  )
}
