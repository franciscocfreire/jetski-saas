'use client'

import { useState } from 'react'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { useQuery } from '@tanstack/react-query'
import { CalendarSearch, Eye, Search } from 'lucide-react'
import { useTenantStore } from '@/lib/store/tenant-store'
import { reservasService } from '@/lib/api/services'
import { formatCurrency } from '@/lib/utils'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
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

const STATUS_OPCOES = [
  ['PENDENTE', 'Pendente'], ['CONFIRMADA', 'Confirmada'], ['EM_ANDAMENTO', 'Em andamento'],
  ['CONCLUIDA', 'Concluída'], ['CANCELADA', 'Cancelada'], ['EXPIRADA', 'Expirada'],
  ['NO_SHOW', 'No-show'], ['RASCUNHO', 'Rascunho'],
] as const

const STATUS_LABEL = Object.fromEntries(STATUS_OPCOES)

const STATUS_TONE: Record<string, string> = {
  CONFIRMADA: 'bg-emerald-100 text-emerald-800 hover:bg-emerald-100',
  EM_ANDAMENTO: 'bg-blue-100 text-blue-800 hover:bg-blue-100',
  PENDENTE: 'bg-amber-100 text-amber-800 hover:bg-amber-100',
}

/**
 * Módulo Reservas: busca com filtros server-side → página de detalhe (ficha).
 * A operação (criar/editar) continua na Agenda/Balcão — aqui é localizar e ver.
 */
export default function ReservasPage() {
  const { currentTenant } = useTenantStore()
  const router = useRouter()
  const [busca, setBusca] = useState('')
  const [status, setStatus] = useState('')
  const [canal, setCanal] = useState('')
  const [de, setDe] = useState('')
  const [ate, setAte] = useState('')

  const { data: reservas, isLoading } = useQuery({
    queryKey: ['reservas-busca', currentTenant?.id, status, canal, de, ate],
    queryFn: () => reservasService.buscar({
      status: status || undefined,
      canal: (canal || undefined) as 'BALCAO' | 'PORTAL' | undefined,
      de: de ? `${de}T00:00:00` : undefined,
      ate: ate ? `${ate}T23:59:59` : undefined,
    }),
    enabled: !!currentTenant,
  })

  const linhas = (reservas ?? []).filter(
    (r) => !busca || r.clienteNome?.toLowerCase().includes(busca.toLowerCase())
  )

  const fmtInicio = (r: { dataInicio: string; dataFimPrevista: string }) =>
    `${new Date(r.dataInicio).toLocaleDateString('pt-BR')} ${new Date(r.dataInicio)
      .toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })}–${new Date(r.dataFimPrevista)
      .toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })}`

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-3">
        <CalendarSearch className="h-7 w-7 text-primary" />
        <div>
          <h1 className="text-2xl font-bold">Reservas</h1>
          <p className="text-sm text-muted-foreground">
            Localize qualquer reserva e abra a ficha completa (com PDF). Para operar, use a Agenda.
          </p>
        </div>
      </div>

      <div className="flex flex-wrap items-end gap-3 rounded-lg border p-4">
        <div className="min-w-[200px]">
          <Label className="text-xs">Cliente</Label>
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
        <div className="min-w-[160px]">
          <Label className="text-xs">Status</Label>
          <Select value={status || 'all'} onValueChange={(v) => setStatus(v === 'all' ? '' : v)}>
            <SelectTrigger><SelectValue placeholder="Todos" /></SelectTrigger>
            <SelectContent>
              <SelectItem value="all">Todos</SelectItem>
              {STATUS_OPCOES.map(([v, l]) => (
                <SelectItem key={v} value={v}>{l}</SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
        <div className="min-w-[130px]">
          <Label className="text-xs">Canal</Label>
          <Select value={canal || 'all'} onValueChange={(v) => setCanal(v === 'all' ? '' : v)}>
            <SelectTrigger><SelectValue placeholder="Todos" /></SelectTrigger>
            <SelectContent>
              <SelectItem value="all">Todos</SelectItem>
              <SelectItem value="PORTAL">Portal</SelectItem>
              <SelectItem value="BALCAO">Balcão</SelectItem>
            </SelectContent>
          </Select>
        </div>
        <div>
          <Label className="text-xs">De</Label>
          <Input type="date" value={de} onChange={(e) => setDe(e.target.value)} />
        </div>
        <div>
          <Label className="text-xs">Até</Label>
          <Input type="date" value={ate} onChange={(e) => setAte(e.target.value)} />
        </div>
      </div>

      <div className="overflow-x-auto rounded-md border">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead className="hidden lg:table-cell">Reserva</TableHead>
              <TableHead>Cliente</TableHead>
              <TableHead className="hidden md:table-cell">Modelo</TableHead>
              <TableHead>Início</TableHead>
              <TableHead>Status</TableHead>
              <TableHead className="hidden sm:table-cell">Canal</TableHead>
              <TableHead className="hidden md:table-cell">Valor</TableHead>
              <TableHead className="w-[90px]">Ações</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {isLoading ? (
              <TableRow>
                <TableCell colSpan={8}><Skeleton className="h-8 w-full" /></TableCell>
              </TableRow>
            ) : linhas.length === 0 ? (
              <TableRow>
                <TableCell colSpan={8} className="h-24 text-center text-muted-foreground">
                  Nenhuma reserva encontrada com esses filtros.
                </TableCell>
              </TableRow>
            ) : (
              linhas.map((r) => (
                <TableRow
                  key={r.id}
                  className="cursor-pointer"
                  onClick={() => router.push(`/dashboard/reservas/${r.id}`)}
                >
                  <TableCell className="hidden lg:table-cell">
                    <span className="font-mono text-xs uppercase text-muted-foreground">
                      #{r.id.slice(0, 8)}
                    </span>
                  </TableCell>
                  <TableCell className="font-medium">{r.clienteNome ?? '—'}</TableCell>
                  <TableCell className="hidden md:table-cell">{r.modeloNome ?? '—'}</TableCell>
                  <TableCell className="text-xs">{fmtInicio(r)}</TableCell>
                  <TableCell>
                    <Badge
                      variant="secondary"
                      className={STATUS_TONE[r.status] ?? ''}
                    >
                      {STATUS_LABEL[r.status] ?? r.status}
                    </Badge>
                  </TableCell>
                  <TableCell className="hidden sm:table-cell">
                    <Badge variant={r.canal === 'PORTAL' ? 'default' : 'outline'}>
                      {r.canal === 'PORTAL' ? 'Portal' : 'Balcão'}
                    </Badge>
                  </TableCell>
                  <TableCell className="hidden md:table-cell">
                    {r.valorTotal != null ? formatCurrency(r.valorTotal) : '—'}
                  </TableCell>
                  <TableCell onClick={(e) => e.stopPropagation()}>
                    <Button variant="ghost" size="sm" asChild>
                      <Link href={`/dashboard/reservas/${r.id}`}>
                        <Eye size={14} className="mr-1" /> Ver
                      </Link>
                    </Button>
                  </TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      </div>

      {(reservas?.length ?? 0) >= 200 && (
        <p className="text-xs text-muted-foreground">
          Mostrando as 200 reservas mais recentes — refine com os filtros de período.
        </p>
      )}
    </div>
  )
}
