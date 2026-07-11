'use client'

import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowUpDown, Loader2 } from 'lucide-react'
import { Button } from '@/components/ui/button'
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle, DialogTrigger,
} from '@/components/ui/dialog'
import { useToast } from '@/hooks/use-toast'
import { platformService } from '@/lib/api/services/platform'
import type { TenantSummary } from '@/lib/api/types'

const brl = (v: number) => v.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })

/**
 * Troca de plano pelo super admin — o caminho de contratação pós-trial e
 * de upgrade/downgrade. Planos pagos passam a gerar fatura mensal.
 */
export function AlterarPlanoDialog({ tenant }: { tenant: TenantSummary }) {
  const { toast } = useToast()
  const queryClient = useQueryClient()
  const [open, setOpen] = useState(false)
  const [planoId, setPlanoId] = useState<string | null>(null)

  const { data: planos } = useQuery({
    queryKey: ['platform', 'planos'],
    queryFn: () => platformService.planos(),
    enabled: open,
  })

  const mudar = useMutation({
    mutationFn: () => platformService.mudarPlano(tenant.id, planoId!),
    onSuccess: () => {
      toast({ title: 'Plano alterado', description: `${tenant.razaoSocial} agora está no novo plano.` })
      queryClient.invalidateQueries({ queryKey: ['platform'] })
      setOpen(false)
    },
    onError: (e: unknown) => {
      const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
      toast({ title: 'Falha ao alterar plano', description: msg ?? 'Erro inesperado', variant: 'destructive' })
    },
  })

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button variant="outline" size="sm" title="Alterar plano (contratação/upgrade)">
          <ArrowUpDown className="mr-1 size-4" /> Plano
        </Button>
      </DialogTrigger>
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle>Alterar plano — {tenant.razaoSocial}</DialogTitle>
          <DialogDescription>
            Plano atual: <b>{tenant.plano ?? '—'}</b>. Planos pagos geram fatura mensal
            (PIX + conferência). A troca vale imediatamente.
          </DialogDescription>
        </DialogHeader>
        <div className="space-y-2">
          {(planos ?? []).map((p) => (
            <button
              key={p.id}
              type="button"
              onClick={() => setPlanoId(p.id)}
              className={`flex w-full items-center justify-between rounded-md border p-3 text-sm transition-colors ${
                planoId === p.id ? 'border-primary bg-primary/5' : 'hover:bg-muted/50'
              }`}
            >
              <span className="font-medium">{p.nome}</span>
              <span className="text-muted-foreground">
                {p.precoMensal > 0 ? `${brl(p.precoMensal)}/mês` : 'grátis'}
              </span>
            </button>
          ))}
        </div>
        <DialogFooter>
          <Button variant="ghost" onClick={() => setOpen(false)}>Cancelar</Button>
          <Button disabled={!planoId || mudar.isPending} onClick={() => mudar.mutate()}>
            {mudar.isPending && <Loader2 className="mr-1 size-4 animate-spin" />}
            Alterar plano
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
