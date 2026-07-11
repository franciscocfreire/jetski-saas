'use client'

import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Loader2, Trash2, TriangleAlert } from 'lucide-react'
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
import { useToast } from '@/hooks/use-toast'
import { platformService } from '@/lib/api/services/platform'
import type { TenantSummary } from '@/lib/api/types'

type Modo = 'CARENCIA' | 'IMEDIATO'

/**
 * Zona de perigo (super admin): exclusão da empresa. Padrão = carência de 30
 * dias (suspende agora, expurga depois, cancelável — prazo dos Termos de
 * Uso); imediato = expurga na hora (empresas de teste). O export de
 * arquivamento sempre roda antes do expurgo.
 */
export function ExcluirEmpresaDialog({ tenant }: { tenant: TenantSummary }) {
  const { toast } = useToast()
  const queryClient = useQueryClient()
  const [open, setOpen] = useState(false)
  const [modo, setModo] = useState<Modo>('CARENCIA')
  const [confirmacao, setConfirmacao] = useState('')

  const preview = useQuery({
    queryKey: ['platform', 'reset-preview', tenant.id, 'TOTAL'],
    queryFn: () => platformService.resetPreview(tenant.id, 'TOTAL'),
    enabled: open,
  })
  const totalPreview = Object.values(preview.data ?? {}).reduce((a, b) => a + b, 0)

  const excluir = useMutation({
    mutationFn: () => platformService.excluirEmpresa(tenant.id, modo, confirmacao.trim()),
    onSuccess: (r) => {
      toast({
        title: r.modo === 'IMEDIATO' ? 'Empresa excluída' : 'Exclusão agendada',
        description:
          r.modo === 'IMEDIATO'
            ? `${tenant.razaoSocial}: ${r.totalLinhas} registros expurgados (export gerado antes).`
            : `${tenant.razaoSocial} foi suspensa; expurgo definitivo em ${new Date(r.expurgoEm!).toLocaleDateString('pt-BR')} (cancelável até lá).`,
      })
      queryClient.invalidateQueries({ queryKey: ['platform'] })
      setOpen(false)
      setConfirmacao('')
    },
    onError: (e: unknown) => {
      const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
      toast({ title: 'Falha na exclusão', description: msg ?? 'Erro inesperado', variant: 'destructive' })
    },
  })

  const confirmado = confirmacao.trim() === tenant.slug

  return (
    <Dialog open={open} onOpenChange={(o) => { setOpen(o); if (!o) setConfirmacao('') }}>
      <DialogTrigger asChild>
        <Button variant="destructive" size="sm">
          <Trash2 className="mr-1 size-4" /> Excluir
        </Button>
      </DialogTrigger>
      <DialogContent className="max-w-lg">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <TriangleAlert className="size-5 text-destructive" /> Excluir {tenant.razaoSocial}
          </DialogTitle>
          <DialogDescription>
            Um <b>export de arquivamento (.zip)</b> é gerado antes do expurgo — dados e arquivos
            são então removidos definitivamente; a empresa vira um registro anonimizado (créditos,
            metering e auditoria permanecem no histórico da plataforma) e o slug fica livre.
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4">
          <div className="space-y-2">
            <button
              type="button"
              onClick={() => setModo('CARENCIA')}
              className={`w-full rounded-md border p-3 text-left text-sm transition-colors ${
                modo === 'CARENCIA' ? 'border-primary bg-primary/5' : 'hover:bg-muted/50'
              }`}
            >
              <p className="font-medium">Com carência de 30 dias (recomendado)</p>
              <p className="text-xs text-muted-foreground">
                Suspende o acesso agora; o expurgo roda automaticamente em 30 dias — prazo dos
                Termos de Uso. Cancelável até lá.
              </p>
            </button>
            <button
              type="button"
              onClick={() => setModo('IMEDIATO')}
              className={`w-full rounded-md border p-3 text-left text-sm transition-colors ${
                modo === 'IMEDIATO' ? 'border-destructive bg-destructive/5' : 'hover:bg-muted/50'
              }`}
            >
              <p className="font-medium text-destructive">Imediato</p>
              <p className="text-xs text-muted-foreground">
                Expurga tudo agora, sem janela de arrependimento. Para empresas de teste.
              </p>
            </button>
          </div>

          <div className="rounded-md border bg-muted/30 p-3 text-sm">
            {preview.isLoading ? (
              <span className="text-muted-foreground">Calculando…</span>
            ) : (
              <span>
                <b>{totalPreview}</b> registros + todos os arquivos e a equipe inteira
                (inclusive administradores) serão expurgados.
              </span>
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
            disabled={!confirmado || excluir.isPending}
            onClick={() => excluir.mutate()}
          >
            {excluir.isPending && <Loader2 className="mr-1 size-4 animate-spin" />}
            {modo === 'CARENCIA' ? 'Agendar exclusão (30 dias)' : 'Excluir AGORA'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
