'use client'

import { useMemo, useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useRouter } from 'next/navigation'
import { toast } from 'sonner'
import { FileClock, PlayCircle, Plus, Search, Ban, Loader2 } from 'lucide-react'
import { useTenantStore } from '@/lib/store/tenant-store'
import { reservasService, clientesService, modelosService } from '@/lib/api/services'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import type { Reserva } from '@/lib/api/types'

const fmtData = (iso: string) =>
  new Date(iso).toLocaleString('pt-BR', {
    day: '2-digit',
    month: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })

/**
 * Atendimentos de balcão em preenchimento (reservas RASCUNHO) — nunca finalizados.
 * Não são reservas "reais": não cobram, não bloqueiam jetski, não entram na agenda.
 * Clicar retoma o atendimento de onde parou.
 */
export default function AtendimentosPage() {
  const { currentTenant } = useTenantStore()
  const router = useRouter()
  const qc = useQueryClient()
  const [q, setQ] = useState('')

  const cancelar = useMutation({
    mutationFn: (id: string) => reservasService.cancelar(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['reservas', currentTenant?.id] })
      toast.success('Atendimento cancelado.')
    },
    onError: (e: unknown) => {
      const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
      toast.error(msg ?? 'Falha ao cancelar.')
    },
  })

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

  const rascunhos = useMemo<Reserva[]>(() => {
    if (!reservas) return []
    const cMap = new Map((clientes ?? []).map((c) => [c.id, c]))
    const mMap = new Map((modelos ?? []).map((m) => [m.id, m]))
    const termo = q.trim().toLowerCase()
    return reservas
      .filter((r) => r.status === 'RASCUNHO')
      .map((r) => ({ ...r, cliente: cMap.get(r.clienteId), modelo: mMap.get(r.modeloId) }))
      .filter((r) => !termo || (r.cliente?.nome ?? '').toLowerCase().includes(termo))
      .sort((a, b) => (b.createdAt ?? '').localeCompare(a.createdAt ?? ''))
  }, [reservas, clientes, modelos, q])

  const retomar = (r: Reserva) => router.push(`/dashboard/balcao?reserva=${r.id}`)

  if (!currentTenant) {
    return (
      <div className="flex h-[50vh] items-center justify-center">
        <p className="text-muted-foreground">Selecione um tenant para continuar</p>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold">Atendimentos em aberto</h1>
          <p className="text-muted-foreground">
            Rascunhos de balcão ainda não finalizados — retome de onde parou
          </p>
        </div>
        <Button onClick={() => router.push('/dashboard/balcao')}>
          <Plus className="mr-2 h-4 w-4" /> Novo atendimento
        </Button>
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
      ) : rascunhos.length === 0 ? (
        <div className="rounded-xl border py-16 text-center">
          <FileClock className="mx-auto h-12 w-12 text-muted-foreground" />
          <p className="mt-4 text-muted-foreground">Nenhum atendimento em aberto</p>
        </div>
      ) : (
        <div className="divide-y rounded-xl border">
          {rascunhos.map((r) => (
            <div key={r.id} className="flex w-full items-center gap-3 px-4 py-3 hover:bg-accent/50">
              <button
                onClick={() => retomar(r)}
                className="flex min-w-0 flex-1 items-center gap-4 text-left"
              >
                <div className="w-14 shrink-0 text-sm tabular-nums text-muted-foreground">
                  {fmtData(r.createdAt ?? r.dataInicio)}
                </div>
                <div className="min-w-0 flex-1">
                  <p className="truncate font-medium">{r.cliente?.nome || 'Cliente não informado'}</p>
                  <p className="truncate text-sm text-muted-foreground">{r.modelo?.nome}</p>
                </div>
              </button>
              <Badge variant="secondary">Rascunho</Badge>
              <Button size="sm" variant="ghost" onClick={() => retomar(r)}>
                <PlayCircle className="mr-1 h-4 w-4" /> Retomar
              </Button>
              <Button
                size="sm"
                variant="ghost"
                className="text-red-600 hover:bg-red-50 hover:text-red-700 dark:hover:bg-red-950/40"
                disabled={cancelar.isPending && cancelar.variables === r.id}
                onClick={() => {
                  if (window.confirm(`Cancelar o atendimento de ${r.cliente?.nome ?? 'cliente'}?`))
                    cancelar.mutate(r.id)
                }}
              >
                {cancelar.isPending && cancelar.variables === r.id ? (
                  <Loader2 className="mr-1 h-4 w-4 animate-spin" />
                ) : (
                  <Ban className="mr-1 h-4 w-4" />
                )}
                Cancelar
              </Button>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
