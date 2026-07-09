'use client'

import { useEffect, useState } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { toast } from 'sonner'
import { Anchor, Loader2 } from 'lucide-react'
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
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
import { useTenantStore } from '@/lib/store/tenant-store'
import { jetskisService, reservasService, locacoesService } from '@/lib/api/services'

// Cada membro carrega o PRÓPRIO modelo: o grupo pode ser cross-modelo
// (ex.: um Spark + um Ultra saindo juntos) — o loop de check-in é agnóstico.
type Membro = { reservaId: string; nome: string; modeloId: string; modeloNome: string }

/**
 * Embarque de um grupo (andam juntos) em 1 clique: aloca um jetski distinto para
 * cada membro — do modelo daquele membro — e faz o check-in de todos.
 * Idempotente por membro (confirma se PENDENTE; pula quem já embarcou).
 */
export function EmbarqueGrupoDialog({
  membros,
  open,
  onOpenChange,
  onEmbarcado,
}: {
  membros: Membro[]
  open: boolean
  onOpenChange: (v: boolean) => void
  onEmbarcado?: () => void
}) {
  const { currentTenant } = useTenantStore()
  const [aloc, setAloc] = useState<Record<string, { jetskiId: string; horimetro: string }>>({})

  const { data: jetskis } = useQuery({
    queryKey: ['jetskis-disponiveis', currentTenant?.id],
    queryFn: () => jetskisService.list({ status: 'DISPONIVEL' }),
    enabled: open && !!currentTenant,
  })
  const disponiveis = jetskis ?? []

  // Auto-aloca jets distintos aos membros ao abrir — cada um do SEU modelo.
  useEffect(() => {
    if (!open) {
      setAloc({})
      return
    }
    if (disponiveis.length === 0) return
    setAloc((prev) => {
      const usados = new Set(Object.values(prev).map((a) => a.jetskiId).filter(Boolean))
      const next = { ...prev }
      for (const m of membros) {
        if (next[m.reservaId]?.jetskiId) continue
        const j = disponiveis.find((x) => x.modeloId === m.modeloId && !usados.has(x.id))
        if (j) {
          usados.add(j.id)
          next[m.reservaId] = { jetskiId: j.id, horimetro: String(j.horimetroAtual ?? '') }
        }
      }
      return next
    })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, disponiveis.length])

  const setMembro = (rid: string, patch: Partial<{ jetskiId: string; horimetro: string }>) =>
    setAloc((a) => ({ ...a, [rid]: { ...a[rid], ...patch } }))

  const escolhidos = membros.map((m) => aloc[m.reservaId]?.jetskiId).filter(Boolean)
  const distintos = new Set(escolhidos).size === escolhidos.length
  const completo = membros.every((m) => aloc[m.reservaId]?.jetskiId && aloc[m.reservaId]?.horimetro)

  // Frota insuficiente é avaliada POR MODELO (grupo pode ser cross-modelo):
  // aponta qual modelo falta e para quem, ex. "Sem Kawasaki Ultra 310 livre para Regis".
  const faltas: string[] = []
  {
    const porModelo = new Map<string, Membro[]>()
    for (const m of membros) porModelo.set(m.modeloId, [...(porModelo.get(m.modeloId) ?? []), m])
    for (const [mid, ms] of porModelo) {
      const disp = disponiveis.filter((j) => j.modeloId === mid).length
      if (disp < ms.length) {
        // Os primeiros `disp` membros do modelo conseguem jet; os demais ficam sem.
        const semJet = ms.slice(disp)
        faltas.push(
          `Sem ${ms[0].modeloNome} livre para ${semJet
            .map((x) => x.nome.split(' ')[0])
            .join(', ')} (${disp} disponível(is), ${ms.length} no grupo).`
        )
      }
    }
  }

  const embarcar = useMutation({
    mutationFn: async () => {
      for (const m of membros) {
        const a = aloc[m.reservaId]
        const r = await reservasService.getById(m.reservaId)
        if (r.status === 'FINALIZADA') continue
        if (r.status === 'CANCELADA' || r.status === 'EXPIRADA')
          throw new Error(`Reserva de ${m.nome} ${r.status.toLowerCase()} — não é possível embarcar.`)
        if (r.status === 'PENDENTE') await reservasService.confirmar(m.reservaId)
        if (!r.jetskiId) await reservasService.alocarJetski(m.reservaId, a.jetskiId)
        await locacoesService.checkInFromReserva({
          reservaId: m.reservaId,
          horimetroInicio: Number(a.horimetro),
        })
      }
    },
    onSuccess: () => {
      toast.success('Grupo embarcado — locações criadas.')
      onEmbarcado?.()
      onOpenChange(false)
    },
    onError: (e: unknown) => {
      const msg =
        (e as { response?: { data?: { message?: string } } })?.response?.data?.message ??
        (e as Error)?.message
      toast.error(msg ?? 'Falha no embarque do grupo.')
    },
  })

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Embarcar grupo ({membros.length} jets)</DialogTitle>
        </DialogHeader>
        {faltas.length > 0 && (
          <div className="space-y-1 rounded-md border border-amber-200 bg-amber-50 p-2 text-xs text-amber-800 dark:border-amber-900 dark:bg-amber-950/40 dark:text-amber-200">
            {faltas.map((f) => (
              <p key={f}>{f}</p>
            ))}
            <p>Aguarde liberar mais ou embarque parte do grupo.</p>
          </div>
        )}
        <div className="space-y-3">
          {membros.map((m) => {
            const a = aloc[m.reservaId] ?? { jetskiId: '', horimetro: '' }
            const usadosPorOutros = new Set(
              membros.filter((x) => x.reservaId !== m.reservaId).map((x) => aloc[x.reservaId]?.jetskiId)
            )
            // Só jets do modelo DESTE membro (grupo pode misturar modelos).
            const opcoes = disponiveis.filter(
              (j) => j.modeloId === m.modeloId && !usadosPorOutros.has(j.id)
            )
            return (
              <div key={m.reservaId} className="rounded-md border p-3">
                <p className="mb-2 text-sm font-medium">
                  {m.nome} <span className="font-normal text-muted-foreground">— {m.modeloNome}</span>
                </p>
                <div className="grid grid-cols-2 gap-2">
                  <div>
                    <Label className="text-xs">Jetski</Label>
                    <Select
                      value={a.jetskiId}
                      onValueChange={(v) => {
                        const j = disponiveis.find((x) => x.id === v)
                        setMembro(m.reservaId, {
                          jetskiId: v,
                          horimetro: a.horimetro || String(j?.horimetroAtual ?? ''),
                        })
                      }}
                    >
                      <SelectTrigger>
                        <SelectValue placeholder="Selecione" />
                      </SelectTrigger>
                      <SelectContent>
                        {opcoes.map((j) => (
                          <SelectItem key={j.id} value={j.id}>
                            {j.serie} (horímetro {j.horimetroAtual})
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  </div>
                  <div>
                    <Label className="text-xs">Horímetro</Label>
                    <Input
                      type="number"
                      step="0.1"
                      value={a.horimetro}
                      onChange={(e) => setMembro(m.reservaId, { horimetro: e.target.value })}
                    />
                  </div>
                </div>
              </div>
            )
          })}
        </div>
        {!distintos && (
          <p className="text-xs text-red-600">Cada membro precisa de um jetski diferente.</p>
        )}
        <DialogFooter>
          <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
            Cancelar
          </Button>
          <Button
            type="button"
            disabled={!completo || !distintos || embarcar.isPending}
            onClick={() => embarcar.mutate()}
          >
            {embarcar.isPending ? (
              <Loader2 className="mr-2 h-4 w-4 animate-spin" />
            ) : (
              <Anchor className="mr-2 h-4 w-4" />
            )}
            Embarcar grupo
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
