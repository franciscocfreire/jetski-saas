'use client'

import { useMemo, useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Ship, Anchor } from 'lucide-react'
import { useTenantStore } from '@/lib/store/tenant-store'
import { reservasService, clientesService, modelosService } from '@/lib/api/services'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { EmbarqueDialog } from '@/components/fila/embarque-dialog'
import type { Reserva } from '@/lib/api/types'

const horaDe = (iso: string) =>
  new Date(iso).toLocaleString('pt-BR', {
    day: '2-digit',
    month: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })

export default function FilaPage() {
  const { currentTenant } = useTenantStore()
  const qc = useQueryClient()
  const [alvo, setAlvo] = useState<Reserva | null>(null)
  const [dialogOpen, setDialogOpen] = useState(false)

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

  // Fila = atendimento concluído (documentos emitidos) aguardando embarque.
  const porModelo = useMemo(() => {
    if (!reservas) return [] as { modeloId: string; modeloNome: string; itens: Reserva[] }[]
    const cMap = new Map((clientes ?? []).map((c) => [c.id, c]))
    const mMap = new Map((modelos ?? []).map((m) => [m.id, m]))
    const naFila = reservas
      .filter((r) => !!r.documentoEmitidoEm && (r.status === 'PENDENTE' || r.status === 'CONFIRMADA'))
      .map((r) => ({ ...r, cliente: cMap.get(r.clienteId), modelo: mMap.get(r.modeloId) }))
      .sort((a, b) => a.dataInicio.localeCompare(b.dataInicio))

    const grupos = new Map<string, Reserva[]>()
    for (const r of naFila) {
      const arr = grupos.get(r.modeloId) ?? []
      arr.push(r)
      grupos.set(r.modeloId, arr)
    }
    return [...grupos.entries()].map(([modeloId, itens]) => ({
      modeloId,
      modeloNome: mMap.get(modeloId)?.nome ?? 'Modelo',
      itens,
    }))
  }, [reservas, clientes, modelos])

  const embarcar = (r: Reserva) => {
    setAlvo(r)
    setDialogOpen(true)
  }

  if (!currentTenant) {
    return (
      <div className="flex h-[50vh] items-center justify-center">
        <p className="text-muted-foreground">Selecione um tenant para continuar</p>
      </div>
    )
  }

  const vazia = porModelo.length === 0

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-3xl font-bold">Fila de espera</h1>
        <p className="text-muted-foreground">
          Atendimentos concluídos aguardando o jetski do modelo ficar livre. O jetski é alocado no embarque.
        </p>
      </div>

      {isLoading ? (
        <p className="py-10 text-center text-muted-foreground">Carregando…</p>
      ) : vazia ? (
        <div className="rounded-xl border py-16 text-center">
          <Ship className="mx-auto h-12 w-12 text-muted-foreground" />
          <p className="mt-4 text-muted-foreground">Ninguém na fila 🎉</p>
        </div>
      ) : (
        <div className="space-y-6">
          {porModelo.map((g) => (
            <div key={g.modeloId} className="rounded-xl border">
              <div className="flex items-center justify-between border-b px-4 py-2">
                <h3 className="font-semibold">{g.modeloNome}</h3>
                <Badge variant="secondary">{g.itens.length} na fila</Badge>
              </div>
              <div className="divide-y">
                {g.itens.map((r, i) => (
                  <div key={r.id} className="flex items-center gap-4 px-4 py-3">
                    <div className="w-7 shrink-0 text-center text-lg font-semibold tabular-nums text-muted-foreground">
                      {i + 1}
                    </div>
                    <div className="w-24 shrink-0 text-sm tabular-nums text-muted-foreground">
                      {horaDe(r.dataInicio)}
                    </div>
                    <div className="min-w-0 flex-1">
                      <p className="truncate font-medium">{r.cliente?.nome || 'Cliente'}</p>
                    </div>
                    <Badge variant={r.status === 'CONFIRMADA' ? 'success' : 'warning'}>
                      {r.status === 'CONFIRMADA' ? 'Pronto' : 'Doc. pendente'}
                    </Badge>
                    <Button type="button" size="sm" onClick={() => embarcar(r)}>
                      <Anchor className="mr-1 h-4 w-4" /> Embarcar
                    </Button>
                  </div>
                ))}
              </div>
            </div>
          ))}
        </div>
      )}

      <EmbarqueDialog
        reservaId={alvo?.id ?? ''}
        modeloId={alvo?.modeloId}
        open={dialogOpen}
        onOpenChange={setDialogOpen}
        onEmbarcado={() => qc.invalidateQueries({ queryKey: ['reservas', currentTenant?.id] })}
      />
    </div>
  )
}
