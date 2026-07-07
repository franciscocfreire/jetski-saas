'use client'

import { useState } from 'react'
import { useParams, useRouter } from 'next/navigation'
import { useQuery } from '@tanstack/react-query'
import {
  ArrowLeft,
  CalendarRange,
  FileDown,
  FileSignature,
  FileText,
  Landmark,
  Loader2,
  Mail,
  Phone,
  User,
  Wallet,
} from 'lucide-react'
import { toast } from 'sonner'
import { documentosService, reservasService } from '@/lib/api/services'
import { abrirPdfPorLink } from '@/lib/pdf'
import { formatCurrency } from '@/lib/utils'
import { GruCicloTimeline, montarPassosCiclo } from '@/components/grus/gru-ciclo-timeline'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'

const STATUS_LABEL: Record<string, string> = {
  RASCUNHO: 'Rascunho', PENDENTE: 'Pendente', CONFIRMADA: 'Confirmada',
  EM_ANDAMENTO: 'Em andamento', CONCLUIDA: 'Concluída', CANCELADA: 'Cancelada',
  EXPIRADA: 'Expirada', NO_SHOW: 'No-show',
}

/**
 * Página de DETALHE da reserva (read-mostly): tudo que a loja precisa numa
 * consulta — e a Ficha em PDF (primeiro documento white-label do sistema).
 * Ações operacionais continuam na Agenda.
 */
export default function ReservaDetalhePage() {
  const { id } = useParams<{ id: string }>()
  const router = useRouter()
  const [gerandoPdf, setGerandoPdf] = useState(false)

  const { data: f, isLoading, isError, refetch } = useQuery({
    queryKey: ['reserva-ficha', id],
    queryFn: () => reservasService.ficha(id),
  })

  async function gerarPdf() {
    try {
      setGerandoPdf(true)
      await abrirPdfPorLink(() => reservasService.fichaDownloadLink(id))
    } catch (e) {
      console.error('[reserva] ficha pdf falhou', e)
      toast.error('Não foi possível gerar a ficha em PDF.')
    } finally {
      setGerandoPdf(false)
    }
  }

  function fmt(d?: string) {
    if (!d) return '—'
    return new Date(d).toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' })
  }

  if (isLoading) {
    return (
      <div className="space-y-4">
        <Skeleton className="h-9 w-64" />
        <div className="grid gap-4 lg:grid-cols-2">
          {[1, 2, 3, 4].map((i) => <Skeleton key={i} className="h-44 w-full" />)}
        </div>
      </div>
    )
  }

  if (isError || !f) {
    return (
      <Card>
        <CardContent className="flex flex-col items-center gap-3 py-12 text-center">
          <p className="text-sm text-muted-foreground">Não foi possível carregar a reserva.</p>
          <Button variant="outline" onClick={() => refetch()}>Tentar novamente</Button>
        </CardContent>
      </Card>
    )
  }

  const r = f.reserva

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-3">
          <Button variant="ghost" size="icon" onClick={() => router.back()} title="Voltar">
            <ArrowLeft className="h-5 w-5" />
          </Button>
          <div>
            <h1 className="flex flex-wrap items-center gap-2 text-2xl font-bold">
              Reserva <span className="font-mono uppercase">#{r.id.slice(0, 8)}</span>
              <Badge variant="secondary">{STATUS_LABEL[r.status] ?? r.status}</Badge>
              <Badge variant={r.canal === 'PORTAL' ? 'default' : 'outline'}>
                {r.canal === 'PORTAL' ? 'Portal' : 'Balcão'}
              </Badge>
            </h1>
            <p className="text-sm text-muted-foreground">
              Criada em {fmt(r.createdAt)} — visão de consulta; para operar, use a Agenda.
            </p>
          </div>
        </div>
        <Button onClick={gerarPdf} disabled={gerandoPdf}>
          {gerandoPdf ? (
            <Loader2 className="mr-2 h-4 w-4 animate-spin" />
          ) : (
            <FileDown className="mr-2 h-4 w-4" />
          )}
          Gerar PDF
        </Button>
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        {/* Cliente */}
        <Card>
          <CardHeader className="pb-2">
            <CardTitle className="flex items-center gap-2 text-base">
              <User size={16} /> Cliente
            </CardTitle>
          </CardHeader>
          <CardContent className="space-y-1.5">
            <p className="font-semibold">{f.cliente?.nome ?? '—'}</p>
            <p className="text-sm text-muted-foreground">CPF {f.cliente?.documentoMascarado ?? '—'}</p>
            {(f.cliente?.whatsapp || f.cliente?.telefone) && (
              <p className="flex items-center gap-1.5 text-sm text-muted-foreground">
                <Phone size={13} /> {f.cliente.whatsapp || f.cliente.telefone}
              </p>
            )}
            {f.cliente?.email && (
              <p className="flex items-center gap-1.5 text-sm text-muted-foreground">
                <Mail size={13} /> {f.cliente.email}
              </p>
            )}
          </CardContent>
        </Card>

        {/* Passeio */}
        <Card>
          <CardHeader className="pb-2">
            <CardTitle className="flex items-center gap-2 text-base">
              <CalendarRange size={16} /> Passeio
            </CardTitle>
          </CardHeader>
          <CardContent className="space-y-1.5">
            <p className="font-semibold">
              {f.passeio?.modeloNome ?? 'Modelo —'}
              {f.passeio?.jetskiSerie ? ` · ${f.passeio.jetskiSerie}` : ''}
            </p>
            <p className="text-sm text-muted-foreground">
              {new Date(r.dataInicio).toLocaleDateString('pt-BR', {
                weekday: 'short', day: '2-digit', month: '2-digit', year: 'numeric',
              })}{' '}
              · {new Date(r.dataInicio).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })}
              –{new Date(r.dataFimPrevista).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })}
            </p>
            {r.valorTotal != null && (
              <p className="text-sm text-muted-foreground">
                Valor: <b>{formatCurrency(r.valorTotal)}</b>
                {r.valorSinal != null ? ` · sinal ${formatCurrency(r.valorSinal)}` : ''}
              </p>
            )}
            {r.observacoes && (
              <p className="rounded-md bg-muted px-2 py-1.5 text-xs text-muted-foreground">
                {r.observacoes}
              </p>
            )}
          </CardContent>
        </Card>

        {/* Financeiro */}
        <Card className="lg:col-span-2">
          <CardHeader className="pb-2">
            <CardTitle className="flex items-center gap-2 text-base">
              <Wallet size={16} /> Financeiro (extrato da reserva)
            </CardTitle>
          </CardHeader>
          <CardContent>
            {!f.extrato || f.extrato.lancamentos.length === 0 ? (
              <p className="text-sm text-muted-foreground">Sem lançamentos registrados.</p>
            ) : (
              <>
                <div className="overflow-x-auto">
                  <Table>
                    <TableHeader>
                      <TableRow>
                        <TableHead>Data</TableHead>
                        <TableHead>Tipo</TableHead>
                        <TableHead>Forma</TableHead>
                        <TableHead className="text-right">Valor</TableHead>
                        <TableHead className="hidden sm:table-cell">Observação</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {f.extrato.lancamentos.map((l) => (
                        <TableRow key={l.id}>
                          <TableCell className="text-xs">{fmt(l.createdAt)}</TableCell>
                          <TableCell>
                            <Badge variant={l.tipo === 'ESTORNO' ? 'destructive' : 'secondary'}>
                              {l.tipo}
                            </Badge>
                          </TableCell>
                          <TableCell className="text-xs">{l.forma ?? '—'}</TableCell>
                          <TableCell className="text-right font-medium">
                            {formatCurrency(l.valor)}
                          </TableCell>
                          <TableCell className="hidden text-xs text-muted-foreground sm:table-cell">
                            {l.observacao ?? '—'}
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </div>
                <div className="mt-3 flex flex-wrap justify-end gap-4 text-sm">
                  <span>Cobranças: <b>{formatCurrency(f.extrato.totalCobrancas)}</b></span>
                  <span>Pagamentos: <b>{formatCurrency(f.extrato.totalPagamentos)}</b></span>
                  {f.extrato.totalEstornos > 0 && (
                    <span>Estornos: <b>{formatCurrency(f.extrato.totalEstornos)}</b></span>
                  )}
                  <span className="rounded-md bg-muted px-2 py-0.5">
                    Saldo: <b>{formatCurrency(f.extrato.saldo)}</b>
                  </span>
                </div>
              </>
            )}
          </CardContent>
        </Card>

        {/* Habilitação & GRU */}
        <Card>
          <CardHeader className="pb-2">
            <CardTitle className="flex items-center gap-2 text-base">
              <Landmark size={16} /> Habilitação & GRU
            </CardTitle>
          </CardHeader>
          <CardContent>
            {!f.habilitacao ? (
              <p className="text-sm text-muted-foreground">Habilitação ainda não registrada.</p>
            ) : f.ciclo ? (
              <GruCicloTimeline passos={montarPassosCiclo(f.ciclo, formatCurrency)} />
            ) : (
              <p className="text-sm">
                {f.habilitacao.via === 'CHA' ? 'CHA do condutor' : f.habilitacao.via}
                {f.habilitacao.chaCategoria ? ` · ${f.habilitacao.chaCategoria}` : ''}
                {f.habilitacao.chaNumero ? ` · nº ${f.habilitacao.chaNumero}` : ''}
                {f.habilitacao.resolvida && (
                  <Badge className="ml-2 bg-emerald-100 text-emerald-800 hover:bg-emerald-100">
                    Resolvida
                  </Badge>
                )}
              </p>
            )}
          </CardContent>
        </Card>

        {/* Termos & Documentos */}
        <Card>
          <CardHeader className="pb-2">
            <CardTitle className="flex items-center gap-2 text-base">
              <FileSignature size={16} /> Termos & Documentos
            </CardTitle>
          </CardHeader>
          <CardContent className="space-y-3">
            {f.aceite ? (
              <p className="text-sm">
                Termo assinado em <b>{fmt(f.aceite.aceitoEm)}</b>{' '}
                <span className="text-muted-foreground">
                  ({f.aceite.metodo === 'PAPEL' ? 'papel' : 'assinatura digital'})
                </span>
              </p>
            ) : (
              <p className="text-sm text-muted-foreground">Termo ainda não assinado.</p>
            )}
            {f.documentos.length === 0 ? (
              <p className="text-sm text-muted-foreground">Nenhum documento emitido.</p>
            ) : (
              <ul className="space-y-1.5">
                {f.documentos.map((d) => (
                  <li key={d.id} className="flex items-center gap-2 text-sm">
                    <FileText size={14} className="shrink-0 text-muted-foreground" />
                    Emitido em {fmt(d.emitidoEm)}
                    <button
                      type="button"
                      className="text-primary hover:underline"
                      onClick={() =>
                        abrirPdfPorLink(() => documentosService.downloadLink(d.id)).catch(() =>
                          toast.error('Não foi possível abrir o documento.'))
                      }
                    >
                      abrir
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  )
}
