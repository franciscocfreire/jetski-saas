'use client'

import { useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { ClipboardList, Search } from 'lucide-react'
import { useTenantStore } from '@/lib/store/tenant-store'
import { reservasService, clientesService, modelosService, jetskisService } from '@/lib/api/services'
import { Badge } from '@/components/ui/badge'
import { Input } from '@/components/ui/input'
import { ReservaDetailSheet } from '@/components/agenda/reserva-detail-sheet'
import type { Reserva, ReservaStatus } from '@/lib/api/types'

const TERMINAIS: ReservaStatus[] = ['CANCELADA', 'EXPIRADA', 'FINALIZADA']

const statusBadge: Record<ReservaStatus, 'success' | 'warning' | 'secondary'> = {
  PENDENTE: 'warning',
  CONFIRMADA: 'success',
  CANCELADA: 'secondary',
  FINALIZADA: 'secondary',
  EXPIRADA: 'secondary',
}

const fmtData = (iso: string) =>
  new Date(iso).toLocaleString('pt-BR', {
    day: '2-digit',
    month: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })

export default function PendenciasPage() {
  const { currentTenant } = useTenantStore()
  const [q, setQ] = useState('')
  const [detail, setDetail] = useState<Reserva | null>(null)
  const [sheetOpen, setSheetOpen] = useState(false)

  const { data: reservas, isLoading } = useQuery({
    queryKey: ['reservas', currentTenant?.id],
    queryFn: () => reservasService.list(),
    enabled: !!currentTenant,
  })
  const { data: clientes } = useQuery({
    queryKey: ['clientes', currentTenant?.id],
    queryFn: () => clientesService.list(),
    enabled: !!currentTenant,
  })
  const { data: modelos } = useQuery({
    queryKey: ['modelos', currentTenant?.id],
    queryFn: () => modelosService.list(),
    enabled: !!currentTenant,
  })
  const { data: jetskis } = useQuery({
    queryKey: ['jetskis', currentTenant?.id],
    queryFn: () => jetskisService.list(),
    enabled: !!currentTenant,
  })

  const pendentes = useMemo<Reserva[]>(() => {
    if (!reservas) return []
    const cMap = new Map((clientes ?? []).map((c) => [c.id, c]))
    const mMap = new Map((modelos ?? []).map((m) => [m.id, m]))
    const jMap = new Map((jetskis ?? []).map((j) => [j.id, j]))
    const termo = q.trim().toLowerCase()
    return reservas
      .filter((r) => !TERMINAIS.includes(r.status))
      .map((r) => ({
        ...r,
        cliente: cMap.get(r.clienteId),
        modelo: mMap.get(r.modeloId),
        jetski: r.jetskiId ? jMap.get(r.jetskiId) : undefined,
      }))
      .filter((r) => !termo || (r.cliente?.nome ?? '').toLowerCase().includes(termo))
      .sort((a, b) => a.dataInicio.localeCompare(b.dataInicio))
  }, [reservas, clientes, modelos, jetskis, q])

  const abrir = (r: Reserva) => {
    setDetail(r)
    setSheetOpen(true)
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
      <div>
        <h1 className="text-3xl font-bold">Pendências</h1>
        <p className="text-muted-foreground">
          Reservas que ainda precisam de ação (termos, GRU, documentos)
        </p>
      </div>

      <div className="relative max-w-sm">
        <Search className="absolute left-2.5 top-2.5 h-4 w-4 text-muted-foreground" />
        <Input
          className="pl-8"
          placeholder="Buscar por cliente…"
          value={q}
          onChange={(e) => setQ(e.target.value)}
        />
      </div>

      {isLoading ? (
        <p className="py-10 text-center text-muted-foreground">Carregando…</p>
      ) : pendentes.length === 0 ? (
        <div className="rounded-xl border py-16 text-center">
          <ClipboardList className="mx-auto h-12 w-12 text-muted-foreground" />
          <p className="mt-4 text-muted-foreground">Nenhuma reserva pendente 🎉</p>
        </div>
      ) : (
        <div className="divide-y rounded-xl border">
          {pendentes.map((r) => (
            <button
              key={r.id}
              onClick={() => abrir(r)}
              className="flex w-full items-center gap-4 px-4 py-3 text-left hover:bg-accent/50"
            >
              <div className="w-20 shrink-0 text-sm tabular-nums text-muted-foreground">
                {fmtData(r.dataInicio)}
              </div>
              <div className="min-w-0 flex-1">
                <p className="truncate font-medium">{r.cliente?.nome || 'Cliente não informado'}</p>
                <p className="truncate text-sm text-muted-foreground">{r.modelo?.nome}</p>
              </div>
              <div className="flex shrink-0 items-center gap-2">
                <Badge variant={r.cliente?.origem === 'PORTAL' ? 'default' : 'secondary'}>
                  {r.cliente?.origem === 'PORTAL' ? 'Online' : 'Balcão'}
                </Badge>
                <Badge variant={statusBadge[r.status]}>{r.status}</Badge>
              </div>
            </button>
          ))}
        </div>
      )}

      <ReservaDetailSheet reserva={detail} open={sheetOpen} onOpenChange={setSheetOpen} />
    </div>
  )
}
