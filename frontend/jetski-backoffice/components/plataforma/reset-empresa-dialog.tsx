'use client'

import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Eraser, Loader2, TriangleAlert } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@/components/ui/dialog'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { useToast } from '@/hooks/use-toast'
import { platformService, type ResetNivel } from '@/lib/api/services/platform'
import type { TenantSummary } from '@/lib/api/types'

const NIVEIS: { value: ResetNivel; label: string; hint: string }[] = [
  {
    value: 'OPERACIONAL',
    label: 'Operacional',
    hint: 'Reservas, locações, clientes, fotos, documentos e financeiro. Mantém frota, equipe e configurações.',
  },
  {
    value: 'FROTA',
    label: 'Operacional + Frota',
    hint: 'Anterior + modelos, jetskis, instrutores, itens e políticas.',
  },
  {
    value: 'TOTAL',
    label: 'Total',
    hint: 'Anterior + vendedores, convites e equipe (mantém só os administradores). Volta ao estado de empresa recém-aprovada.',
  },
]

/**
 * Zona de perigo (super admin): reset da empresa por nível, com preview das
 * contagens e confirmação pelo slug digitado. Créditos, metering, assinatura
 * e auditoria NUNCA são apagados (o backend garante).
 */
export function ResetEmpresaDialog({ tenant }: { tenant: TenantSummary }) {
  const { toast } = useToast()
  const queryClient = useQueryClient()
  const [open, setOpen] = useState(false)
  const [nivel, setNivel] = useState<ResetNivel>('OPERACIONAL')
  const [confirmacao, setConfirmacao] = useState('')

  const preview = useQuery({
    queryKey: ['platform', 'reset-preview', tenant.id, nivel],
    queryFn: () => platformService.resetPreview(tenant.id, nivel),
    enabled: open,
  })

  const reset = useMutation({
    mutationFn: () => platformService.resetEmpresa(tenant.id, nivel, confirmacao.trim()),
    onSuccess: (r) => {
      toast({
        title: 'Reset executado',
        description: `${tenant.razaoSocial}: ${r.totalLinhas} registros apagados (nível ${r.nivel}).`,
      })
      queryClient.invalidateQueries({ queryKey: ['platform'] })
      setOpen(false)
      setConfirmacao('')
    },
    onError: (e: unknown) => {
      const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
      toast({ title: 'Falha no reset', description: msg ?? 'Erro inesperado', variant: 'destructive' })
    },
  })

  const totalPreview = Object.values(preview.data ?? {}).reduce((a, b) => a + b, 0)
  const confirmado = confirmacao.trim() === tenant.slug

  return (
    <Dialog open={open} onOpenChange={(o) => { setOpen(o); if (!o) setConfirmacao('') }}>
      <DialogTrigger asChild>
        <Button variant="outline" size="sm" className="text-destructive hover:text-destructive">
          <Eraser className="mr-1 size-4" /> Resetar
        </Button>
      </DialogTrigger>
      <DialogContent className="max-w-lg">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <TriangleAlert className="size-5 text-destructive" /> Resetar {tenant.razaoSocial}
          </DialogTitle>
          <DialogDescription>
            Apaga os dados do nível escolhido. Um <b>export de arquivamento (.zip)</b> é gerado
            automaticamente antes — se ele falhar, nada é apagado. Créditos, plano, metering e
            auditoria são sempre preservados; habilitações de clientes já vivem no registro
            global e não são afetadas.
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4">
          <div>
            <Label className="text-xs">Nível do reset</Label>
            <Select value={nivel} onValueChange={(v) => setNivel(v as ResetNivel)}>
              <SelectTrigger className="h-9"><SelectValue /></SelectTrigger>
              <SelectContent>
                {NIVEIS.map((n) => (
                  <SelectItem key={n.value} value={n.value}>{n.label}</SelectItem>
                ))}
              </SelectContent>
            </Select>
            <p className="mt-1 text-xs text-muted-foreground">
              {NIVEIS.find((n) => n.value === nivel)?.hint}
            </p>
          </div>

          <div className="rounded-md border bg-muted/30 p-3 text-sm">
            {preview.isLoading ? (
              <span className="text-muted-foreground">Calculando o que será apagado…</span>
            ) : totalPreview === 0 ? (
              <span className="text-muted-foreground">Nada a apagar neste nível.</span>
            ) : (
              <>
                <p className="mb-2 font-medium">
                  {totalPreview} registros serão apagados:
                </p>
                <div className="grid max-h-40 grid-cols-2 gap-x-4 gap-y-0.5 overflow-y-auto text-xs">
                  {Object.entries(preview.data ?? {}).map(([tabela, n]) => (
                    <div key={tabela} className="flex justify-between">
                      <span className="text-muted-foreground">{tabela}</span>
                      <span className="tabular-nums">{n}</span>
                    </div>
                  ))}
                </div>
              </>
            )}
          </div>

          <div>
            <Label className="text-xs">
              Digite <code className="rounded bg-muted px-1">{tenant.slug}</code> para confirmar
            </Label>
            <Input
              value={confirmacao}
              onChange={(e) => setConfirmacao(e.target.value)}
              placeholder={tenant.slug}
              className="h-9 font-mono"
              autoComplete="off"
            />
          </div>
        </div>

        <DialogFooter>
          <Button variant="ghost" onClick={() => setOpen(false)}>Cancelar</Button>
          <Button
            variant="destructive"
            disabled={!confirmado || totalPreview === 0 || reset.isPending}
            onClick={() => reset.mutate()}
          >
            {reset.isPending && <Loader2 className="mr-1 size-4 animate-spin" />}
            Resetar ({NIVEIS.find((n) => n.value === nivel)?.label})
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
