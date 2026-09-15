'use client'

import { useMemo, useState, Suspense } from 'react'
import { useSearchParams } from 'next/navigation'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import {
  FileText,
  FileDown,
  Search,
  Loader2,
  Mail,
  Copy,
  Check,
  Eye,
  ChevronDown,
  Landmark,
  User,
} from 'lucide-react'
import { toast } from 'sonner'
import { useTenantStore } from '@/lib/store/tenant-store'
import { documentosService, grusService, clientesService } from '@/lib/api/services'
import { abrirPdfPorLink } from '@/lib/pdf'
import { formatCurrency } from '@/lib/utils'
import { GruReservaSheet } from '@/components/grus/gru-reserva-sheet'
import type { Cliente, DocumentoEmitido, Gru } from '@/lib/api/types'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Checkbox } from '@/components/ui/checkbox'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { Skeleton } from '@/components/ui/skeleton'

type Destino = 'MARINHA' | 'CLIENTE'

/**
 * Uma linha da tela: o ciclo da GRU de uma reserva (via EMA) com o documento emitido,
 * ou um documento que não tem GRU (CHA própria) ou é uma reemissão anterior de uma
 * reserva que já aparece pela GRU.
 */
interface Linha {
  chave: string
  reservaId: string
  clienteId?: string
  clienteNome?: string
  gru?: Gru
  documentoId?: string
  emitidoEm?: string
  /** O documento tem ofício à Marinha (via EMA). CHA própria não tem. */
  marinhaAplicavel: boolean
  /** Reemissão anterior: a linha da GRU mostra o documento mais recente da reserva. */
  anterior?: boolean
  ordem: number
}

const BADGE_OK = 'bg-emerald-100 text-emerald-800 hover:bg-emerald-100'
const BADGE_ATENCAO = 'bg-amber-100 text-amber-800 hover:bg-amber-100'

export default function DocumentosPage() {
  return (
    <Suspense fallback={<div className="p-4 text-sm text-muted-foreground">Carregando…</div>}>
      <DocumentosConteudo />
    </Suspense>
  )
}

function DocumentosConteudo() {
  const { currentTenant } = useTenantStore()
  const queryClient = useQueryClient()
  const searchParams = useSearchParams()
  const [clienteId, setClienteId] = useState<string>(searchParams.get('clienteId') ?? '')
  const [busca, setBusca] = useState('')
  const [soNaoEnviadas, setSoNaoEnviadas] = useState(false)
  const [copiada, setCopiada] = useState<string | null>(null)
  const [baixandoId, setBaixandoId] = useState<string | null>(null)
  const [reenviando, setReenviando] = useState<string | null>(null)
  const [detalhe, setDetalhe] = useState<Gru | null>(null)
  const [sheetAberto, setSheetAberto] = useState(false)

  const { data: clientes } = useQuery({
    queryKey: ['clientes', currentTenant?.id],
    queryFn: () => clientesService.list(),
    enabled: !!currentTenant,
  })

  const { data: documentos, isLoading: carregandoDocs } = useQuery({
    queryKey: ['documentos', currentTenant?.id],
    queryFn: () => documentosService.list(),
    enabled: !!currentTenant,
  })

  const { data: grus, isLoading: carregandoGrus } = useQuery({
    queryKey: ['grus', currentTenant?.id],
    queryFn: () => grusService.list(),
    enabled: !!currentTenant,
  })

  const carregando = carregandoDocs || carregandoGrus

  const linhas = useMemo(() => montarLinhas(grus ?? [], documentos ?? []), [grus, documentos])

  const visiveis = linhas
    .filter((l) => !clienteId || l.clienteId === clienteId)
    .filter((l) => !busca || l.clienteNome?.toLowerCase().includes(busca.toLowerCase()))
    .filter((l) => !soNaoEnviadas || (!!l.gru && !!l.documentoId && !l.gru.marinhaEnviadaEm))

  function abrirDetalhe(g: Gru) {
    setDetalhe(g)
    setSheetAberto(true)
  }

  async function baixar(id: string) {
    try {
      setBaixandoId(id)
      // Abre a aba no clique e aponta p/ uma URL https real (iOS renderiza PDF nativo).
      await abrirPdfPorLink(() => documentosService.downloadLink(id))
    } catch (e) {
      console.error('[documentos] download falhou', e)
      toast.error('Não foi possível abrir o documento.')
    } finally {
      setBaixandoId(null)
    }
  }

  async function reenviar(documentoId: string, destino: Destino) {
    try {
      setReenviando(`${documentoId}:${destino}`)
      const r = await documentosService.reenviar(documentoId, destino)
      if (destino === 'MARINHA') {
        if (r.enviadoMarinha) toast.success('Ofício reenviado à Marinha.')
        else
          toast.error(
            'Ofício não enviado à Marinha. Confira o SMTP da EAMA e o e-mail da Capitania em Configurações.'
          )
      } else if (r.enviadoCliente) {
        toast.success('Documentos reenviados ao cliente.')
      } else {
        toast.error('Não foi possível enviar ao cliente. Confira o e-mail cadastrado do cliente.')
      }
      queryClient.invalidateQueries({ queryKey: ['grus'] })
      queryClient.invalidateQueries({ queryKey: ['documentos'] })
    } catch (e) {
      console.error('[documentos] reenviar falhou', e)
      toast.error('Falha ao reenviar.')
    } finally {
      setReenviando(null)
    }
  }

  function copiarGru(numero: string) {
    navigator.clipboard.writeText(numero)
    setCopiada(numero)
    setTimeout(() => setCopiada(null), 1500)
  }

  const clientesFiltro = (clientes ?? []).filter((c: Cliente) => !!c.nome)

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-3">
        <FileText className="h-7 w-7 text-primary" />
        <div>
          <h1 className="text-2xl font-bold">Documentos e GRUs</h1>
          <p className="text-sm text-muted-foreground">
            Documentos emitidos das reservas (NORMAM-212) e o ciclo de cada GRU: pagamento, emissão,
            envio à Marinha e devolutiva.
          </p>
        </div>
      </div>

      <div className="flex flex-wrap items-end gap-4 rounded-lg border p-4">
        <div className="min-w-[200px]">
          <Label className="text-xs">Buscar cliente</Label>
          <div className="relative">
            <Search className="absolute left-2 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
            <Input
              className="pl-8"
              placeholder="Nome do cliente…"
              value={busca}
              onChange={(e) => setBusca(e.target.value)}
            />
          </div>
        </div>
        <div className="min-w-[220px]">
          <Label className="text-xs">Cliente</Label>
          <Select value={clienteId || 'all'} onValueChange={(v) => setClienteId(v === 'all' ? '' : v)}>
            <SelectTrigger>
              <SelectValue placeholder="Todos os clientes" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">Todos os clientes</SelectItem>
              {clientesFiltro.map((c) => (
                <SelectItem key={c.id} value={c.id}>
                  {c.nome}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
        <label className="flex items-center gap-2 pb-2 text-sm">
          <Checkbox checked={soNaoEnviadas} onCheckedChange={(v) => setSoNaoEnviadas(v === true)} />
          Só não enviadas à Marinha
        </label>
      </div>

      <div className="overflow-x-auto rounded-md border">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Cliente</TableHead>
              <TableHead className="hidden lg:table-cell">Reserva</TableHead>
              <TableHead>GRU</TableHead>
              <TableHead className="hidden md:table-cell">Valor</TableHead>
              <TableHead className="hidden md:table-cell">Emitido em</TableHead>
              <TableHead>Paga</TableHead>
              <TableHead>Marinha</TableHead>
              <TableHead className="hidden sm:table-cell">Confirmada</TableHead>
              <TableHead className="w-[250px]">Ações</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {carregando ? (
              <TableRow>
                <TableCell colSpan={9}>
                  <Skeleton className="h-8 w-full" />
                </TableCell>
              </TableRow>
            ) : visiveis.length === 0 ? (
              <TableRow>
                <TableCell colSpan={9} className="h-24 text-center text-muted-foreground">
                  {soNaoEnviadas
                    ? 'Nenhum documento aguardando envio à Marinha.'
                    : `Nenhum documento ou GRU${clienteId ? ' para este cliente' : ''}.`}
                </TableCell>
              </TableRow>
            ) : (
              visiveis.map((l) => {
                const g = l.gru
                return (
                  <TableRow
                    key={l.chave}
                    className={g ? 'cursor-pointer' : undefined}
                    onClick={g ? () => abrirDetalhe(g) : undefined}
                  >
                    <TableCell className="font-medium">{l.clienteNome ?? '-'}</TableCell>
                    <TableCell className="hidden lg:table-cell">
                      <span className="font-mono text-xs uppercase text-muted-foreground">
                        #{l.reservaId.slice(0, 8)}
                      </span>
                    </TableCell>
                    <TableCell onClick={(e) => e.stopPropagation()}>
                      {g ? (
                        <button
                          type="button"
                          className="flex items-center gap-1 font-mono text-xs hover:text-primary"
                          title="Copiar número da GRU"
                          onClick={() => copiarGru(g.gruNumero)}
                        >
                          {copiada === g.gruNumero ? (
                            <Check size={12} className="text-emerald-600" />
                          ) : (
                            <Copy size={12} />
                          )}
                          {g.gruNumero}
                        </button>
                      ) : l.anterior ? (
                        <Badge variant="secondary">Reemissão anterior</Badge>
                      ) : (
                        <Badge variant="secondary">Sem GRU (CHA)</Badge>
                      )}
                    </TableCell>
                    <TableCell className="hidden md:table-cell">
                      {g?.gruValor != null ? formatCurrency(g.gruValor) : '-'}
                    </TableCell>
                    <TableCell className="hidden md:table-cell">
                      {l.emitidoEm ? (
                        fmt(l.emitidoEm)
                      ) : (
                        <Badge variant="secondary">Pendente</Badge>
                      )}
                    </TableCell>
                    <TableCell>
                      {g ? (
                        g.gruPago ? (
                          <Badge className={BADGE_OK}>
                            Paga{g.gruPagoEm ? ` ${fmtData(g.gruPagoEm)}` : ''}
                          </Badge>
                        ) : (
                          <Badge variant="secondary">Não paga</Badge>
                        )
                      ) : (
                        <span className="text-muted-foreground">-</span>
                      )}
                    </TableCell>
                    <TableCell>
                      {!l.marinhaAplicavel ? (
                        <Badge variant="secondary">Não se aplica</Badge>
                      ) : g ? (
                        g.marinhaEnviadaEm ? (
                          <Badge className={BADGE_OK}>Enviado {fmt(g.marinhaEnviadaEm)}</Badge>
                        ) : (
                          <Badge
                            className={BADGE_ATENCAO}
                            title="Sem registro de envio — emissões antigas não registravam; use Reenviar → À Marinha."
                          >
                            Não enviado
                          </Badge>
                        )
                      ) : (
                        <span className="text-muted-foreground">-</span>
                      )}
                    </TableCell>
                    <TableCell className="hidden sm:table-cell">
                      {g ? (
                        g.marinhaConfirmadaEm ? (
                          <Badge className={BADGE_OK}>{fmtData(g.marinhaConfirmadaEm)}</Badge>
                        ) : (
                          <Badge variant="secondary">Aguardando</Badge>
                        )
                      ) : (
                        <span className="text-muted-foreground">-</span>
                      )}
                    </TableCell>
                    <TableCell onClick={(e) => e.stopPropagation()}>
                      <div className="flex flex-wrap items-center gap-1">
                        {g && (
                          <Button
                            type="button"
                            variant="ghost"
                            size="sm"
                            title="Ver a reserva e o ciclo da GRU"
                            onClick={() => abrirDetalhe(g)}
                          >
                            <Eye size={14} className="mr-1" />
                            Ver
                          </Button>
                        )}
                        <Button
                          type="button"
                          variant="ghost"
                          size="sm"
                          title={l.documentoId ? 'Abrir o PDF emitido' : 'Documentação ainda não emitida'}
                          disabled={!l.documentoId || baixandoId === l.documentoId}
                          onClick={() => l.documentoId && baixar(l.documentoId)}
                        >
                          {!!l.documentoId && baixandoId === l.documentoId ? (
                            <Loader2 size={14} className="mr-1 animate-spin" />
                          ) : (
                            <FileDown size={14} className="mr-1" />
                          )}
                          Abrir
                        </Button>
                        <DropdownMenu>
                          <DropdownMenuTrigger asChild>
                            <Button
                              type="button"
                              variant="ghost"
                              size="sm"
                              title={l.documentoId ? 'Reenviar por e-mail' : 'Documentação ainda não emitida'}
                              disabled={!l.documentoId || reenviando?.startsWith(`${l.documentoId}:`)}
                            >
                              {!!l.documentoId && reenviando?.startsWith(`${l.documentoId}:`) ? (
                                <Loader2 size={14} className="mr-1 animate-spin" />
                              ) : (
                                <Mail size={14} className="mr-1" />
                              )}
                              Reenviar
                              <ChevronDown size={14} className="ml-1" />
                            </Button>
                          </DropdownMenuTrigger>
                          <DropdownMenuContent align="end">
                            <DropdownMenuItem
                              disabled={!l.marinhaAplicavel}
                              onSelect={() => l.documentoId && reenviar(l.documentoId, 'MARINHA')}
                            >
                              <Landmark size={14} className="mr-2" />
                              À Marinha
                              {!l.marinhaAplicavel && (
                                <span className="ml-2 text-xs text-muted-foreground">(CHA própria)</span>
                              )}
                            </DropdownMenuItem>
                            <DropdownMenuItem
                              onSelect={() => l.documentoId && reenviar(l.documentoId, 'CLIENTE')}
                            >
                              <User size={14} className="mr-2" />
                              Ao cliente
                            </DropdownMenuItem>
                          </DropdownMenuContent>
                        </DropdownMenu>
                      </div>
                    </TableCell>
                  </TableRow>
                )
              })
            )}
          </TableBody>
        </Table>
      </div>

      <GruReservaSheet gru={detalhe} open={sheetAberto} onOpenChange={setSheetAberto} />
    </div>
  )
}

/**
 * Junta as duas fontes: cada GRU (via EMA) vira uma linha com o documento mais recente
 * da reserva; documentos que nenhuma GRU mostra entram como linha própria — CHA própria
 * (sem ofício à Marinha) ou reemissão anterior de uma reserva que já tem GRU.
 */
function montarLinhas(grus: Gru[], documentos: DocumentoEmitido[]): Linha[] {
  const reservasComGru = new Set(grus.map((g) => g.reservaId))
  const documentosDasGrus = new Set(grus.map((g) => g.documentoId).filter(Boolean) as string[])

  const deGrus: Linha[] = grus.map((g) => ({
    chave: `gru-${g.reservaId}`,
    reservaId: g.reservaId,
    clienteId: g.clienteId,
    clienteNome: g.clienteNome,
    gru: g,
    documentoId: g.documentoId,
    emitidoEm: g.documentoEmitidoEm,
    marinhaAplicavel: true,
    ordem: tempo(g.documentoEmitidoEm ?? g.gruGeradaEm),
  }))

  const deDocumentos: Linha[] = documentos
    .filter((d) => !documentosDasGrus.has(d.id))
    .map((d) => ({
      chave: `doc-${d.id}`,
      reservaId: d.reservaId,
      clienteId: d.clienteId,
      clienteNome: d.clienteNome,
      documentoId: d.id,
      emitidoEm: d.emitidoEm,
      marinhaAplicavel: reservasComGru.has(d.reservaId),
      anterior: reservasComGru.has(d.reservaId),
      ordem: tempo(d.emitidoEm),
    }))

  return [...deGrus, ...deDocumentos].sort((a, b) => b.ordem - a.ordem)
}

function tempo(d?: string) {
  return d ? new Date(d).getTime() : 0
}

function fmt(d?: string) {
  if (!d) return '-'
  return new Date(d).toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' })
}

function fmtData(d?: string) {
  if (!d) return '-'
  return new Date(d).toLocaleDateString('pt-BR')
}
