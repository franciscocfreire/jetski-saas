'use client'

import { useState, Suspense } from 'react'
import { useSearchParams } from 'next/navigation'
import { useQuery } from '@tanstack/react-query'
import { Search, FileText, Download, Eye, Filter, ArrowLeft } from 'lucide-react'
import { useTenantStore } from '@/lib/store/tenant-store'
import { auditoriaService } from '@/lib/api/services'
import type { Auditoria, AuditoriaFilters } from '@/lib/api/types'
import { formatDateTime } from '@/lib/utils'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
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
import { Badge } from '@/components/ui/badge'
import { Skeleton } from '@/components/ui/skeleton'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'

// Badge variant by action type
const getAcaoBadgeVariant = (acao: string): 'default' | 'secondary' | 'destructive' | 'outline' => {
  switch (acao) {
    case 'CHECK_IN':
      return 'default'
    case 'CHECK_OUT':
      return 'secondary'
    default:
      return 'outline'
  }
}

// Format action for display
const formatAcao = (acao: string): string => {
  return acao.replace(/_/g, ' ')
}

// Loading fallback for Suspense
function AuditoriaLoading() {
  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <Skeleton className="h-9 w-32" />
          <Skeleton className="h-4 w-64 mt-2" />
        </div>
        <Skeleton className="h-10 w-32" />
      </div>
      <div className="flex gap-4">
        <Skeleton className="h-10 flex-1" />
        <Skeleton className="h-10 w-[180px]" />
        <Skeleton className="h-10 w-[180px]" />
      </div>
      <div className="rounded-md border">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Data/Hora</TableHead>
              <TableHead>Usuario</TableHead>
              <TableHead>Acao</TableHead>
              <TableHead>Entidade</TableHead>
              <TableHead>IP</TableHead>
              <TableHead className="w-[50px]"></TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {Array.from({ length: 5 }).map((_, i) => (
              <TableRow key={i}>
                <TableCell><Skeleton className="h-4 w-32" /></TableCell>
                <TableCell><Skeleton className="h-4 w-32" /></TableCell>
                <TableCell><Skeleton className="h-4 w-20" /></TableCell>
                <TableCell><Skeleton className="h-4 w-24" /></TableCell>
                <TableCell><Skeleton className="h-4 w-24" /></TableCell>
                <TableCell><Skeleton className="h-4 w-8" /></TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>
    </div>
  )
}

// Inner component that uses useSearchParams
function AuditoriaContent() {
  const { currentTenant } = useTenantStore()
  const searchParams = useSearchParams()

  // Read initial filters from URL query params
  const urlEntidade = searchParams.get('entidade')
  const urlEntidadeId = searchParams.get('entidadeId')

  const [search, setSearch] = useState('')
  const [acaoFilter, setAcaoFilter] = useState<string>('')
  const [entidadeFilter, setEntidadeFilter] = useState<string>(urlEntidade || '')
  const [entidadeIdFilter, setEntidadeIdFilter] = useState<string>(urlEntidadeId || '')
  const [page, setPage] = useState(0)
  const [selectedLog, setSelectedLog] = useState<Auditoria | null>(null)
  const [isExporting, setIsExporting] = useState(false)

  // Detect if viewing specific entity history
  const isEntityHistoryView = !!urlEntidadeId

  // Build filters
  const filters: AuditoriaFilters = {
    acao: acaoFilter || undefined,
    entidade: entidadeFilter || undefined,
    entidadeId: entidadeIdFilter || undefined,
  }

  // Fetch audit logs
  const { data, isLoading } = useQuery({
    queryKey: ['auditoria', currentTenant?.id, filters, page],
    queryFn: () => auditoriaService.list({ ...filters, page, size: 20 }),
    enabled: !!currentTenant,
  })

  // Filter by search term (client-side)
  const filteredLogs = data?.content?.filter(log =>
    log.usuarioNome?.toLowerCase().includes(search.toLowerCase()) ||
    log.acao?.toLowerCase().includes(search.toLowerCase()) ||
    log.entidade?.toLowerCase().includes(search.toLowerCase())
  )

  // Handle CSV export
  const handleExportCsv = async () => {
    setIsExporting(true)
    try {
      await auditoriaService.downloadCsv(filters)
    } catch (error) {
      console.error('Error exporting CSV:', error)
    } finally {
      setIsExporting(false)
    }
  }

  // Reset filters
  const handleResetFilters = () => {
    setAcaoFilter('')
    setEntidadeFilter('')
    setEntidadeIdFilter('')
    setSearch('')
    setPage(0)
    // Clear URL params
    window.history.replaceState({}, '', '/dashboard/auditoria')
  }

  // Go back to locacoes when viewing entity history
  const handleGoBack = () => {
    window.history.back()
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-4">
          {isEntityHistoryView && (
            <Button variant="ghost" size="icon" onClick={handleGoBack}>
              <ArrowLeft className="h-4 w-4" />
            </Button>
          )}
          <div>
            <h1 className="text-3xl font-bold">
              {isEntityHistoryView ? `Historico da ${entidadeFilter}` : 'Auditoria'}
            </h1>
            <p className="text-muted-foreground">
              {isEntityHistoryView
                ? `Todas as operacoes desta ${entidadeFilter?.toLowerCase()}`
                : 'Historico de operacoes do sistema'}
            </p>
          </div>
        </div>
        <Button
          variant="outline"
          onClick={handleExportCsv}
          disabled={isExporting || isLoading}
        >
          <Download className="mr-2 h-4 w-4" />
          {isExporting ? 'Exportando...' : 'Exportar CSV'}
        </Button>
      </div>

      {/* Filters */}
      <div className="flex flex-wrap gap-4">
        <div className="relative flex-1 min-w-[200px]">
          <Search className="absolute left-2.5 top-2.5 h-4 w-4 text-muted-foreground" />
          <Input
            placeholder="Buscar por usuario, acao..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="pl-8"
          />
        </div>

        <Select value={acaoFilter || 'ALL'} onValueChange={(v) => setAcaoFilter(v === 'ALL' ? '' : v)}>
          <SelectTrigger className="w-[180px]">
            <SelectValue placeholder="Todas as acoes" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="ALL">Todas as acoes</SelectItem>
            <SelectItem value="CHECK_IN">Check-in</SelectItem>
            <SelectItem value="CHECK_OUT">Check-out</SelectItem>
          </SelectContent>
        </Select>

        <Select value={entidadeFilter || 'ALL'} onValueChange={(v) => setEntidadeFilter(v === 'ALL' ? '' : v)}>
          <SelectTrigger className="w-[180px]">
            <SelectValue placeholder="Todas as entidades" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="ALL">Todas as entidades</SelectItem>
            <SelectItem value="LOCACAO">Locacao</SelectItem>
            <SelectItem value="JETSKI">Jetski</SelectItem>
            <SelectItem value="RESERVA">Reserva</SelectItem>
          </SelectContent>
        </Select>

        {(acaoFilter || entidadeFilter || entidadeIdFilter || search) && (
          <Button variant="ghost" onClick={handleResetFilters}>
            <Filter className="mr-2 h-4 w-4" />
            Limpar filtros
          </Button>
        )}
      </div>

      {/* Table */}
      <div className="rounded-md border">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Data/Hora</TableHead>
              <TableHead>Usuario</TableHead>
              <TableHead>Acao</TableHead>
              <TableHead>Entidade</TableHead>
              <TableHead>IP</TableHead>
              <TableHead className="w-[50px]"></TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {isLoading ? (
              Array.from({ length: 5 }).map((_, i) => (
                <TableRow key={i}>
                  <TableCell><Skeleton className="h-4 w-32" /></TableCell>
                  <TableCell><Skeleton className="h-4 w-32" /></TableCell>
                  <TableCell><Skeleton className="h-4 w-20" /></TableCell>
                  <TableCell><Skeleton className="h-4 w-24" /></TableCell>
                  <TableCell><Skeleton className="h-4 w-24" /></TableCell>
                  <TableCell><Skeleton className="h-4 w-8" /></TableCell>
                </TableRow>
              ))
            ) : filteredLogs?.length === 0 ? (
              <TableRow>
                <TableCell colSpan={6} className="h-24 text-center">
                  <div className="flex flex-col items-center gap-2">
                    <FileText className="h-8 w-8 text-muted-foreground" />
                    <p className="text-muted-foreground">Nenhum registro encontrado</p>
                  </div>
                </TableCell>
              </TableRow>
            ) : (
              filteredLogs?.map((log) => (
                <TableRow key={log.id}>
                  <TableCell className="whitespace-nowrap font-mono text-sm">
                    {formatDateTime(log.createdAt)}
                  </TableCell>
                  <TableCell>{log.usuarioNome}</TableCell>
                  <TableCell>
                    <Badge variant={getAcaoBadgeVariant(log.acao)}>
                      {formatAcao(log.acao)}
                    </Badge>
                  </TableCell>
                  <TableCell>{log.entidade}</TableCell>
                  <TableCell className="font-mono text-sm text-muted-foreground">
                    {log.ip || '-'}
                  </TableCell>
                  <TableCell>
                    <Button
                      variant="ghost"
                      size="icon"
                      onClick={() => setSelectedLog(log)}
                    >
                      <Eye className="h-4 w-4" />
                    </Button>
                  </TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      </div>

      {/* Pagination */}
      {data && data.totalPages > 1 && (
        <div className="flex items-center justify-between">
          <p className="text-sm text-muted-foreground">
            Pagina {data.number + 1} de {data.totalPages} ({data.totalElements} registros)
          </p>
          <div className="flex gap-2">
            <Button
              variant="outline"
              size="sm"
              onClick={() => setPage(p => Math.max(0, p - 1))}
              disabled={data.first}
            >
              Anterior
            </Button>
            <Button
              variant="outline"
              size="sm"
              onClick={() => setPage(p => p + 1)}
              disabled={data.last}
            >
              Proxima
            </Button>
          </div>
        </div>
      )}

      {/* Detail Dialog */}
      <Dialog open={!!selectedLog} onOpenChange={() => setSelectedLog(null)}>
        <DialogContent className="max-w-2xl">
          <DialogHeader>
            <DialogTitle>Detalhes do Log</DialogTitle>
          </DialogHeader>
          {selectedLog && (
            <div className="space-y-4">
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <p className="text-sm font-medium text-muted-foreground">Data/Hora</p>
                  <p>{formatDateTime(selectedLog.createdAt)}</p>
                </div>
                <div>
                  <p className="text-sm font-medium text-muted-foreground">Usuario</p>
                  <p>{selectedLog.usuarioNome}</p>
                </div>
                <div>
                  <p className="text-sm font-medium text-muted-foreground">Acao</p>
                  <Badge variant={getAcaoBadgeVariant(selectedLog.acao)}>
                    {formatAcao(selectedLog.acao)}
                  </Badge>
                </div>
                <div>
                  <p className="text-sm font-medium text-muted-foreground">Entidade</p>
                  <p>{selectedLog.entidade}</p>
                </div>
                <div>
                  <p className="text-sm font-medium text-muted-foreground">ID da Entidade</p>
                  <p className="font-mono text-sm">{selectedLog.entidadeId}</p>
                </div>
                <div>
                  <p className="text-sm font-medium text-muted-foreground">IP</p>
                  <p className="font-mono text-sm">{selectedLog.ip || '-'}</p>
                </div>
              </div>

              {selectedLog.traceId && (
                <div>
                  <p className="text-sm font-medium text-muted-foreground">Trace ID</p>
                  <p className="font-mono text-sm">{selectedLog.traceId}</p>
                </div>
              )}

              {selectedLog.dadosNovos && Object.keys(selectedLog.dadosNovos).length > 0 && (
                <div>
                  <p className="text-sm font-medium text-muted-foreground mb-2">Dados da Operacao</p>
                  <pre className="bg-muted p-3 rounded-md text-sm overflow-auto max-h-60">
                    {JSON.stringify(selectedLog.dadosNovos, null, 2)}
                  </pre>
                </div>
              )}

              {selectedLog.dadosAnteriores && Object.keys(selectedLog.dadosAnteriores).length > 0 && (
                <div>
                  <p className="text-sm font-medium text-muted-foreground mb-2">Dados Anteriores</p>
                  <pre className="bg-muted p-3 rounded-md text-sm overflow-auto max-h-60">
                    {JSON.stringify(selectedLog.dadosAnteriores, null, 2)}
                  </pre>
                </div>
              )}
            </div>
          )}
        </DialogContent>
      </Dialog>
    </div>
  )
}

// Main export with Suspense wrapper
export default function AuditoriaPage() {
  return (
    <Suspense fallback={<AuditoriaLoading />}>
      <AuditoriaContent />
    </Suspense>
  )
}
