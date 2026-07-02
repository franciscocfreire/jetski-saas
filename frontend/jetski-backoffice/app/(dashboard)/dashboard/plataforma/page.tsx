'use client'

import { useEffect, useState } from 'react'
import { useRouter } from 'next/navigation'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { ShieldCheck, Check, Ban, RotateCcw, ShieldAlert, KeyRound, Loader2, Gauge as GaugeIcon } from 'lucide-react'
import { platformService, meteringService } from '@/lib/api/services'
import { useTenantStore } from '@/lib/store/tenant-store'
import type { TenantSummary } from '@/lib/api/types'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Skeleton } from '@/components/ui/skeleton'
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from '@/components/ui/alert-dialog'
import { useToast } from '@/hooks/use-toast'

type BadgeVariant = 'default' | 'secondary' | 'destructive' | 'outline'

const STATUS_BADGE: Record<string, { label: string; variant: BadgeVariant }> = {
  ATIVO: { label: 'Ativo', variant: 'default' },
  TRIAL: { label: 'Trial', variant: 'default' },
  PENDENTE_APROVACAO: { label: 'Pendente', variant: 'secondary' },
  SUSPENSO: { label: 'Suspenso', variant: 'destructive' },
  INATIVO: { label: 'Inativo', variant: 'outline' },
  CANCELADO: { label: 'Cancelado', variant: 'outline' },
}

export default function PlataformaPage() {
  const queryClient = useQueryClient()
  const { toast } = useToast()
  const router = useRouter()
  const { accessType } = useTenantStore()

  // Guarda de rota: só super admin (UNRESTRICTED). Defesa em profundidade — o backend
  // (OPA) já nega, mas evita expor a tela a quem digitar a URL direto.
  const isPlatformAdmin = accessType === 'UNRESTRICTED'
  useEffect(() => {
    if (accessType !== null && !isPlatformAdmin) {
      router.replace('/dashboard')
    }
  }, [accessType, isPlatformAdmin, router])

  const { data: tenants, isLoading } = useQuery({
    queryKey: ['platform', 'tenants'],
    queryFn: () => platformService.listAllTenants(),
    enabled: isPlatformAdmin,
  })

  const errMsg = (e: unknown, fallback: string) =>
    (e as { response?: { data?: { message?: string } } })?.response?.data?.message ?? fallback

  const refresh = () => queryClient.invalidateQueries({ queryKey: ['platform', 'tenants'] })

  const approveMutation = useMutation({
    mutationFn: (tenantId: string) => platformService.approve(tenantId),
    onSuccess: (res) => { toast({ title: 'Empresa aprovada', description: res.message }); refresh() },
    onError: (e) => toast({ title: 'Falha ao aprovar', description: errMsg(e, 'Erro inesperado'), variant: 'destructive' }),
  })

  const suspendMutation = useMutation({
    mutationFn: (tenantId: string) => platformService.suspend(tenantId),
    onSuccess: (res) => { toast({ title: 'Empresa suspensa', description: res.message }); refresh() },
    onError: (e) => toast({ title: 'Falha ao suspender', description: errMsg(e, 'Erro inesperado'), variant: 'destructive' }),
  })

  const reactivateMutation = useMutation({
    mutationFn: (tenantId: string) => platformService.reactivate(tenantId),
    onSuccess: (res) => { toast({ title: 'Empresa reativada', description: res.message }); refresh() },
    onError: (e) => toast({ title: 'Falha ao reativar', description: errMsg(e, 'Erro inesperado'), variant: 'destructive' }),
  })

  const reencryptMutation = useMutation({
    mutationFn: () => platformService.reencryptSecrets(),
    onSuccess: (r) =>
      toast({
        title: r.criptografiaAtiva ? 'Segredos re-cifrados' : 'Criptografia desativada',
        description: r.criptografiaAtiva
          ? `${r.recifrados}/${r.comSegredo} re-cifrados${r.falhas ? ` · ${r.falhas} falha(s)` : ''}.` +
            (r.falhas === 0 ? ' Pode remover a chave anterior.' : '')
          : 'Defina JETSKI_SECRET_KEY para ativar a criptografia.',
        variant: r.falhas > 0 ? 'destructive' : undefined,
      }),
    onError: (e) =>
      toast({ title: 'Falha na re-cifragem', description: errMsg(e, 'Erro inesperado'), variant: 'destructive' }),
  })

  // Guarda de rota: só super admin (UNRESTRICTED). Vem APÓS todos os hooks acima
  // (regras de hooks do React — o return condicional não pode preceder hooks).
  if (!isPlatformAdmin) {
    return (
      <div className="flex flex-1 items-center justify-center p-6">
        <div className="flex flex-col items-center gap-2 text-center text-muted-foreground">
          <ShieldAlert className="size-10" />
          <p className="text-sm">Acesso restrito a administradores de plataforma.</p>
        </div>
      </div>
    )
  }

  const renderAction = (t: TenantSummary) => {
    if (t.status === 'PENDENTE_APROVACAO') {
      return (
        <ConfirmAction
          trigger={<><Check className="mr-1 size-4" /> Aprovar</>}
          title="Aprovar empresa?"
          description={`${t.razaoSocial} será ativada e um trial de 14 dias será iniciado.`}
          confirmLabel="Aprovar"
          onConfirm={() => approveMutation.mutate(t.id)}
          pending={approveMutation.isPending && approveMutation.variables === t.id}
        />
      )
    }
    if (t.status === 'ATIVO' || t.status === 'TRIAL') {
      return (
        <ConfirmAction
          trigger={<><Ban className="mr-1 size-4" /> Suspender</>}
          variant="destructive"
          title="Suspender empresa?"
          description={`${t.razaoSocial} perderá o acesso até ser reativada.`}
          confirmLabel="Suspender"
          onConfirm={() => suspendMutation.mutate(t.id)}
          pending={suspendMutation.isPending && suspendMutation.variables === t.id}
        />
      )
    }
    if (t.status === 'SUSPENSO') {
      return (
        <ConfirmAction
          trigger={<><RotateCcw className="mr-1 size-4" /> Reativar</>}
          title="Reativar empresa?"
          description={`${t.razaoSocial} voltará a ter acesso.`}
          confirmLabel="Reativar"
          onConfirm={() => reactivateMutation.mutate(t.id)}
          pending={reactivateMutation.isPending && reactivateMutation.variables === t.id}
        />
      )
    }
    return <span className="text-xs text-muted-foreground">—</span>
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="flex items-center gap-2 text-2xl font-bold">
          <ShieldCheck className="size-6" /> Empresas
        </h1>
        <p className="text-muted-foreground">
          Visão completa da plataforma — aprovar, suspender e reativar empresas (super admin).
        </p>
      </div>

      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <KeyRound className="size-5" /> Rotação de chave de criptografia
          </CardTitle>
          <CardDescription>
            Re-cifra os segredos (senha SMTP) de todos os tenants com a chave atual. Use após trocar
            a <code>JETSKI_SECRET_KEY</code>; quando <strong>falhas = 0</strong>, remova a{' '}
            <code>JETSKI_SECRET_KEY_PREVIOUS</code>.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <Button onClick={() => reencryptMutation.mutate()} disabled={reencryptMutation.isPending}>
            {reencryptMutation.isPending ? (
              <Loader2 className="mr-2 size-4 animate-spin" />
            ) : (
              <KeyRound className="mr-2 size-4" />
            )}
            Re-cifrar segredos
          </Button>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Todas as empresas</CardTitle>
          <CardDescription>Empresas cadastradas na plataforma, de qualquer status.</CardDescription>
        </CardHeader>
        <CardContent>
          {isLoading ? (
            <div className="space-y-2">
              <Skeleton className="h-10 w-full" />
              <Skeleton className="h-10 w-full" />
              <Skeleton className="h-10 w-full" />
            </div>
          ) : !tenants || tenants.length === 0 ? (
            <p className="py-8 text-center text-sm text-muted-foreground">Nenhuma empresa cadastrada.</p>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Empresa</TableHead>
                  <TableHead>Identificador</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead className="text-right">Ações</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {tenants.map((t) => {
                  const badge = STATUS_BADGE[t.status] ?? { label: t.status, variant: 'outline' as BadgeVariant }
                  return (
                    <TableRow key={t.id}>
                      <TableCell className="font-medium">{t.razaoSocial}</TableCell>
                      <TableCell className="text-muted-foreground">{t.slug}</TableCell>
                      <TableCell>
                        <Badge variant={badge.variant}>{badge.label}</Badge>
                      </TableCell>
                      <TableCell className="text-right">{renderAction(t)}</TableCell>
                    </TableRow>
                  )
                })}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>

      <EmissoesPorEmpresaCard enabled={isPlatformAdmin} />
    </div>
  )
}

/** Metering cross-tenant: emissões por empresa na competência selecionada. */
function EmissoesPorEmpresaCard({ enabled }: { enabled: boolean }) {
  const [competencia, setCompetencia] = useState(() => {
    const hoje = new Date()
    return `${hoje.getFullYear()}-${String(hoje.getMonth() + 1).padStart(2, '0')}`
  })

  const competencias = Array.from({ length: 12 }, (_, i) => {
    const d = new Date()
    d.setMonth(d.getMonth() - i)
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`
  })

  const { data: emissoes, isLoading } = useQuery({
    queryKey: ['platform', 'emissoes', competencia],
    queryFn: () => meteringService.getPlatformEmissoes(competencia),
    enabled,
  })

  return (
    <Card>
      <CardHeader>
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <CardTitle className="flex items-center gap-2">
              <GaugeIcon className="size-5" /> Emissões por empresa
            </CardTitle>
            <CardDescription>
              Consumo mensal (documentos e GRUs — base da cobrança futura). Razão alta de
              prévias com poucas emissões merece atenção.
            </CardDescription>
          </div>
          <select
            className="h-9 rounded-md border bg-background px-2 text-sm"
            value={competencia}
            onChange={(e) => setCompetencia(e.target.value)}
            aria-label="Competência"
          >
            {competencias.map((c) => (
              <option key={c} value={c}>{c}</option>
            ))}
          </select>
        </div>
      </CardHeader>
      <CardContent>
        {isLoading || !emissoes ? (
          <Skeleton className="h-32 w-full" />
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Empresa</TableHead>
                <TableHead className="text-right">Documentos</TableHead>
                <TableHead className="text-right">GRUs</TableHead>
                <TableHead className="text-right">Prévias</TableHead>
                <TableHead className="text-right">Total cobrável</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {emissoes.map((e) => (
                <TableRow key={e.tenantId}>
                  <TableCell className="font-medium">
                    {e.razaoSocial}
                    <span className="ml-2 text-xs text-muted-foreground">{e.slug}</span>
                  </TableCell>
                  <TableCell className="text-right tabular-nums">{e.documento}</TableCell>
                  <TableCell className="text-right tabular-nums">{e.gru}</TableCell>
                  <TableCell className="text-right tabular-nums text-muted-foreground">{e.previa}</TableCell>
                  <TableCell className="text-right font-semibold tabular-nums">{e.total}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </CardContent>
    </Card>
  )
}

function ConfirmAction({
  trigger,
  title,
  description,
  confirmLabel,
  onConfirm,
  pending,
  variant = 'default',
}: {
  trigger: React.ReactNode
  title: string
  description: string
  confirmLabel: string
  onConfirm: () => void
  pending: boolean
  variant?: 'default' | 'destructive'
}) {
  return (
    <AlertDialog>
      <AlertDialogTrigger asChild>
        <Button size="sm" variant={variant === 'destructive' ? 'destructive' : 'default'} disabled={pending}>
          {pending ? 'Processando...' : trigger}
        </Button>
      </AlertDialogTrigger>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>{title}</AlertDialogTitle>
          <AlertDialogDescription>{description}</AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter>
          <AlertDialogCancel>Cancelar</AlertDialogCancel>
          <AlertDialogAction onClick={onConfirm}>{confirmLabel}</AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  )
}
