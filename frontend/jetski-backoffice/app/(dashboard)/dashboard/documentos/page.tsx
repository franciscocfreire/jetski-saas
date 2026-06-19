'use client'

import { useState, Suspense } from 'react'
import { useSearchParams } from 'next/navigation'
import { useQuery } from '@tanstack/react-query'
import { FileText, FileDown, Search, Loader2 } from 'lucide-react'
import { useTenantStore } from '@/lib/store/tenant-store'
import { documentosService, clientesService } from '@/lib/api/services'
import type { Cliente } from '@/lib/api/types'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { Skeleton } from '@/components/ui/skeleton'

export default function DocumentosPage() {
  return (
    <Suspense fallback={<div className="p-4 text-sm text-muted-foreground">Carregando…</div>}>
      <DocumentosConteudo />
    </Suspense>
  )
}

function DocumentosConteudo() {
  const { currentTenant } = useTenantStore()
  const searchParams = useSearchParams()
  const [clienteId, setClienteId] = useState<string>(searchParams.get('clienteId') ?? '')
  const [busca, setBusca] = useState('')

  const { data: clientes } = useQuery({
    queryKey: ['clientes', currentTenant?.id],
    queryFn: () => clientesService.list(),
    enabled: !!currentTenant,
  })

  const { data: documentos, isLoading } = useQuery({
    queryKey: ['documentos', currentTenant?.id, clienteId],
    queryFn: () => documentosService.list(clienteId || undefined),
    enabled: !!currentTenant,
  })

  const clientesFiltrados = (clientes ?? []).filter((c: Cliente) =>
    c.nome?.toLowerCase().includes(busca.toLowerCase())
  )

  function fmt(d?: string) {
    if (!d) return '-'
    const dt = new Date(d)
    return dt.toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' })
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-3">
        <FileText className="h-7 w-7 text-primary" />
        <div>
          <h1 className="text-2xl font-bold">Documentos emitidos</h1>
          <p className="text-sm text-muted-foreground">
            PDFs consolidados das reservas (NORMAM-212). Filtre por cliente e baixe.
          </p>
        </div>
      </div>

      <div className="flex flex-wrap items-end gap-3 rounded-lg border p-4">
        <div className="min-w-[160px]">
          <Label className="text-xs">Filtrar clientes</Label>
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
        <div className="min-w-[240px]">
          <Label className="text-xs">Cliente</Label>
          <Select value={clienteId || 'all'} onValueChange={(v) => setClienteId(v === 'all' ? '' : v)}>
            <SelectTrigger>
              <SelectValue placeholder="Todos os clientes" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">Todos os clientes</SelectItem>
              {clientesFiltrados.map((c) => (
                <SelectItem key={c.id} value={c.id}>
                  {c.nome}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
      </div>

      <div className="rounded-md border">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Cliente</TableHead>
              <TableHead>Emitido em</TableHead>
              <TableHead>Reserva</TableHead>
              <TableHead>Hash</TableHead>
              <TableHead className="w-[120px]">Documento</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {isLoading ? (
              <TableRow>
                <TableCell colSpan={5}>
                  <Skeleton className="h-8 w-full" />
                </TableCell>
              </TableRow>
            ) : (documentos ?? []).length === 0 ? (
              <TableRow>
                <TableCell colSpan={5} className="h-24 text-center text-muted-foreground">
                  Nenhum documento emitido{clienteId ? ' para este cliente' : ''}.
                </TableCell>
              </TableRow>
            ) : (
              (documentos ?? []).map((d) => (
                <TableRow key={d.id}>
                  <TableCell className="font-medium">{d.clienteNome ?? '-'}</TableCell>
                  <TableCell>{fmt(d.emitidoEm)}</TableCell>
                  <TableCell className="font-mono text-xs">{d.reservaId.slice(0, 8)}</TableCell>
                  <TableCell className="font-mono text-xs text-muted-foreground">
                    {d.hashSha256 ? d.hashSha256.slice(0, 10) + '…' : '-'}
                  </TableCell>
                  <TableCell>
                    {d.downloadUrl ? (
                      <a href={d.downloadUrl} target="_blank" rel="noopener noreferrer">
                        <Button type="button" variant="outline" size="sm">
                          <FileDown size={14} className="mr-1" /> Abrir
                        </Button>
                      </a>
                    ) : (
                      <span className="text-xs text-muted-foreground">indisponível</span>
                    )}
                  </TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      </div>

      {isLoading && (
        <p className="flex items-center gap-2 text-sm text-muted-foreground">
          <Loader2 className="h-4 w-4 animate-spin" /> Carregando…
        </p>
      )}
    </div>
  )
}
