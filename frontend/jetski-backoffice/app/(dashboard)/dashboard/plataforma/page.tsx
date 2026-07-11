'use client'

import { useEffect, useState } from 'react'
import { useRouter } from 'next/navigation'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { ShieldCheck, Check, Ban, RotateCcw, ShieldAlert, KeyRound, Loader2, Gauge as GaugeIcon, Coins } from 'lucide-react'
import { platformService, meteringService, creditosService } from '@/lib/api/services'
import { ResetEmpresaDialog } from '@/components/plataforma/reset-empresa-dialog'
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
import { Input } from '@/components/ui/input'
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
import { ImagemConfigCard } from '@/components/dashboard/imagem-config-card'

type BadgeVariant = 'default' | 'secondary' | 'destructive' | 'outline'

const STATUS_BADGE: Record<string, { label: string; variant: BadgeVariant }> = {
  ATIVO: { label: 'Ativo', variant: 'default' },
  TRIAL: { label: 'Trial', variant: 'default' },
  PENDENTE_APROVACAO: { label: 'Pendente', variant: 'secondary' },
  SUSPENSO: { label: 'Suspenso', variant: 'destructive' },
  INATIVO: { label: 'Inativo', variant: 'outline' },
  CANCELADO: { label: 'Cancelado', variant: 'outline' },
}

/** Célula "Plano": nome + vencimento da assinatura (Trial vencido ganha destaque). */
function PlanoCell({ t }: { t: TenantSummary }) {
  if (!t.plano) return <span className="text-muted-foreground">—</span>
  const fim = t.assinaturaFim ? new Date(t.assinaturaFim + 'T00:00:00') : null
  const vencida = fim != null && fim.getTime() < Date.now()
  return (
    <div className="flex flex-col">
      <span>{t.plano}</span>
      {fim && (
        <span className={`text-xs ${vencida ? 'font-medium text-red-600 dark:text-red-400' : 'text-muted-foreground'}`}>
          {vencida ? 'venceu em ' : 'vence em '}
          {fim.toLocaleDateString('pt-BR')}
        </span>
      )}
    </div>
  )
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
        <div className="flex items-center justify-end gap-2">
          <LancarCreditosDialog tenant={t} />
          <ResetEmpresaDialog tenant={t} />
          <ConfirmAction
            trigger={<><Ban className="mr-1 size-4" /> Suspender</>}
            variant="destructive"
            title="Suspender empresa?"
            description={`${t.razaoSocial} perderá o acesso até ser reativada.`}
            confirmLabel="Suspender"
            onConfirm={() => suspendMutation.mutate(t.id)}
            pending={suspendMutation.isPending && suspendMutation.variables === t.id}
          />
        </div>
      )
    }
    if (t.status === 'SUSPENSO') {
      return (
        <div className="flex items-center justify-end gap-2">
          <ResetEmpresaDialog tenant={t} />
          <ConfirmAction
            trigger={<><RotateCcw className="mr-1 size-4" /> Reativar</>}
            title="Reativar empresa?"
            description={`${t.razaoSocial} voltará a ter acesso.`}
            confirmLabel="Reativar"
            onConfirm={() => reactivateMutation.mutate(t.id)}
            pending={reactivateMutation.isPending && reactivateMutation.variables === t.id}
          />
        </div>
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
                  <TableHead>Plano</TableHead>
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
                        <PlanoCell t={t} />
                      </TableCell>
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

      <ComprasPendentesCard enabled={isPlatformAdmin} />

      <PrecoCreditoCard enabled={isPlatformAdmin} />

      <ImagemConfigCard enabled={isPlatformAdmin} />

      <EmissoesPorEmpresaCard enabled={isPlatformAdmin} />
    </div>
  )
}

/** Preço do crédito de emissão — vale para novas solicitações de compra. */
function PrecoCreditoCard({ enabled }: { enabled: boolean }) {
  const queryClient = useQueryClient()
  const { toast } = useToast()
  const [preco, setPreco] = useState('')

  const { data: config } = useQuery({
    queryKey: ['platform', 'creditos-config'],
    queryFn: () => creditosService.getPlatformConfig(),
    enabled,
  })

  const salvar = useMutation({
    mutationFn: () => creditosService.atualizarPreco(Number(preco)),
    onSuccess: (r) => {
      queryClient.invalidateQueries({ queryKey: ['platform', 'creditos-config'] })
      setPreco('')
      toast({
        title: 'Preço atualizado',
        description: `Novo preço: ${r.precoUnitario.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })} por crédito (vale para novas solicitações).`,
      })
    },
    onError: (e: unknown) => {
      const err = e as { response?: { data?: { message?: string } }; message?: string }
      toast({
        title: 'Erro ao atualizar preço',
        description: err.response?.data?.message || err.message || 'Erro inesperado',
        variant: 'destructive',
      })
    },
  })

  const precoNum = Number(preco)
  const precoValido = Number.isFinite(precoNum) && precoNum > 0

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <Coins className="size-5" /> Preço do crédito de emissão
        </CardTitle>
        <CardDescription>
          Valor cobrado por crédito na compra via PIX. Solicitações já registradas mantêm o
          preço da época (snapshot) — a mudança vale só para novas compras.
        </CardDescription>
      </CardHeader>
      <CardContent className="flex flex-wrap items-end gap-3">
        <div className="text-sm">
          Preço vigente:{' '}
          <span className="text-lg font-semibold tabular-nums">
            {config
              ? config.precoUnitario.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
              : '—'}
          </span>
          <span className="text-muted-foreground"> / crédito</span>
        </div>
        <div className="flex items-end gap-2">
          <div className="space-y-1">
            <label className="text-xs font-medium" htmlFor="novo-preco">Novo preço (R$)</label>
            <Input
              id="novo-preco"
              type="number"
              min={0.01}
              step="0.01"
              value={preco}
              onChange={(e) => setPreco(e.target.value)}
              placeholder={config ? String(config.precoUnitario) : '5.00'}
              className="w-32 tabular-nums"
            />
          </div>
          <Button
            onClick={() => salvar.mutate()}
            disabled={salvar.isPending || !precoValido}
          >
            {salvar.isPending ? 'Salvando...' : 'Atualizar preço'}
          </Button>
        </div>
      </CardContent>
    </Card>
  )
}

/** Fila de compras de créditos via PIX aguardando conferência do super admin. */
function ComprasPendentesCard({ enabled }: { enabled: boolean }) {
  const queryClient = useQueryClient()
  const { toast } = useToast()
  const [rejeitando, setRejeitando] = useState<string | null>(null)
  const [observacao, setObservacao] = useState('')

  const { data: compras, isLoading } = useQuery({
    queryKey: ['platform', 'compras-creditos'],
    queryFn: () => creditosService.getComprasPendentes(),
    enabled,
  })

  const onDecided = (msg: string) => {
    queryClient.invalidateQueries({ queryKey: ['platform', 'compras-creditos'] })
    queryClient.invalidateQueries({ queryKey: ['platform', 'creditos'] })
    toast({ title: msg })
    setRejeitando(null)
    setObservacao('')
  }
  const onError = (e: unknown) => {
    const err = e as { response?: { data?: { message?: string } }; message?: string }
    toast({
      title: 'Falha na operação',
      description: err.response?.data?.message || err.message || 'Erro inesperado',
      variant: 'destructive',
    })
  }

  const aprovar = useMutation({
    mutationFn: (c: { tenantId: string; id: string }) => creditosService.aprovarCompra(c.tenantId, c.id),
    onSuccess: () => onDecided('Compra aprovada — créditos liberados'),
    onError,
  })
  const rejeitar = useMutation({
    mutationFn: (c: { tenantId: string; id: string }) =>
      creditosService.rejeitarCompra(c.tenantId, c.id, observacao),
    onSuccess: () => onDecided('Compra rejeitada'),
    onError,
  })

  if (!compras || compras.length === 0) {
    if (isLoading) return null
    return null // sem pendências, sem card — menos ruído
  }

  return (
    <Card className="border-warning/40">
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <Coins className="size-5" /> Compras de créditos aguardando conferência
        </CardTitle>
        <CardDescription>
          Confira o PIX no extrato bancário pelo número da transação antes de aprovar.
          A aprovação credita no ledger (imutável e auditado).
        </CardDescription>
      </CardHeader>
      <CardContent>
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Empresa</TableHead>
              <TableHead className="text-right">Valor pago</TableHead>
              <TableHead className="text-right">Créditos</TableHead>
              <TableHead>Transação PIX</TableHead>
              <TableHead>Solicitada em</TableHead>
              <TableHead className="text-right">Ações</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {compras.map((c) => (
              <TableRow key={c.id}>
                <TableCell className="font-medium">
                  {c.razaoSocial}
                  <span className="ml-2 text-xs text-muted-foreground">{c.slug}</span>
                </TableCell>
                <TableCell className="text-right tabular-nums">
                  {c.valorPago != null
                    ? c.valorPago.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
                    : '—'}
                </TableCell>
                <TableCell className="text-right font-semibold tabular-nums">
                  +{c.quantidade}
                  {c.precoUnitario != null && (
                    <span className="ml-1 text-xs font-normal text-muted-foreground">
                      (a {c.precoUnitario.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })})
                    </span>
                  )}
                </TableCell>
                <TableCell className="font-mono text-xs">{c.pixTxid}</TableCell>
                <TableCell className="text-sm text-muted-foreground">
                  {new Date(c.createdAt).toLocaleString('pt-BR')}
                </TableCell>
                <TableCell className="text-right">
                  {rejeitando === c.id ? (
                    <div className="flex items-center justify-end gap-2">
                      <Input
                        value={observacao}
                        onChange={(e) => setObservacao(e.target.value)}
                        placeholder="Motivo da rejeição"
                        className="h-8 w-52 text-xs"
                        maxLength={200}
                      />
                      <Button
                        size="sm"
                        variant="destructive"
                        disabled={rejeitar.isPending || !observacao.trim()}
                        onClick={() => rejeitar.mutate({ tenantId: c.tenantId, id: c.id })}
                      >
                        Confirmar
                      </Button>
                      <Button size="sm" variant="ghost" onClick={() => setRejeitando(null)}>
                        Cancelar
                      </Button>
                    </div>
                  ) : (
                    <div className="flex items-center justify-end gap-2">
                      <Button
                        size="sm"
                        disabled={aprovar.isPending}
                        onClick={() => aprovar.mutate({ tenantId: c.tenantId, id: c.id })}
                      >
                        <Check className="mr-1 size-4" /> Aprovar
                      </Button>
                      <Button size="sm" variant="outline" onClick={() => { setRejeitando(c.id); setObservacao('') }}>
                        Rejeitar
                      </Button>
                    </div>
                  )}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </CardContent>
    </Card>
  )
}

/** Lançamento de créditos (±) para uma empresa — motivo obrigatório, vai para o ledger + auditoria. */
function LancarCreditosDialog({ tenant }: { tenant: TenantSummary }) {
  const queryClient = useQueryClient()
  const { toast } = useToast()
  const [open, setOpen] = useState(false)
  const [quantidade, setQuantidade] = useState('50')
  const [motivo, setMotivo] = useState('')

  const mutation = useMutation({
    mutationFn: () => creditosService.lancarCreditos(tenant.id, Number(quantidade), motivo),
    onSuccess: (l) => {
      queryClient.invalidateQueries({ queryKey: ['platform', 'creditos'] })
      toast({
        title: 'Créditos lançados',
        description: `${tenant.razaoSocial}: ${l.quantidade > 0 ? '+' : ''}${l.quantidade} créditos (saldo ${l.saldoApos}).`,
      })
      setOpen(false)
      setMotivo('')
    },
    onError: (e: unknown) => {
      const err = e as { response?: { data?: { message?: string } }; message?: string }
      toast({
        title: 'Erro ao lançar créditos',
        description: err.response?.data?.message || err.message || 'Não foi possível lançar.',
        variant: 'destructive',
      })
    },
  })

  const quantidadeValida = Number.isInteger(Number(quantidade)) && Number(quantidade) !== 0

  return (
    <AlertDialog open={open} onOpenChange={setOpen}>
      <AlertDialogTrigger asChild>
        <Button size="sm" variant="outline">
          <Coins className="mr-1 size-4" /> Créditos
        </Button>
      </AlertDialogTrigger>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>Lançar créditos — {tenant.razaoSocial}</AlertDialogTitle>
          <AlertDialogDescription>
            Positivo credita, negativo estorna. O lançamento é imutável (ledger) e auditado.
          </AlertDialogDescription>
        </AlertDialogHeader>
        <div className="space-y-3">
          <div className="space-y-1.5">
            <label className="text-sm font-medium" htmlFor="qtd-creditos">Quantidade</label>
            <Input
              id="qtd-creditos"
              type="number"
              value={quantidade}
              onChange={(e) => setQuantidade(e.target.value)}
              className="tabular-nums"
            />
          </div>
          <div className="space-y-1.5">
            <label className="text-sm font-medium" htmlFor="motivo-creditos">Motivo (obrigatório)</label>
            <Input
              id="motivo-creditos"
              value={motivo}
              onChange={(e) => setMotivo(e.target.value)}
              placeholder="Ex.: Compra de pacote de 50 emissões"
              maxLength={200}
            />
          </div>
        </div>
        <AlertDialogFooter>
          <AlertDialogCancel>Cancelar</AlertDialogCancel>
          <Button
            onClick={() => mutation.mutate()}
            disabled={mutation.isPending || !quantidadeValida || !motivo.trim()}
          >
            {mutation.isPending ? 'Lançando...' : 'Lançar créditos'}
          </Button>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
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
  const { data: saldos } = useQuery({
    queryKey: ['platform', 'creditos'],
    queryFn: () => creditosService.getPlatformSaldos(),
    enabled,
  })
  const saldoPorTenant = new Map((saldos ?? []).map((s) => [s.tenantId, s.saldo]))

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
                <TableHead className="text-right">Saldo de créditos</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {emissoes.map((e) => {
                const saldo = saldoPorTenant.get(e.tenantId)
                return (
                  <TableRow key={e.tenantId}>
                    <TableCell className="font-medium">
                      {e.razaoSocial}
                      <span className="ml-2 text-xs text-muted-foreground">{e.slug}</span>
                    </TableCell>
                    <TableCell className="text-right tabular-nums">{e.documento}</TableCell>
                    <TableCell className="text-right tabular-nums">{e.gru}</TableCell>
                    <TableCell className="text-right tabular-nums text-muted-foreground">{e.previa}</TableCell>
                    <TableCell className="text-right font-semibold tabular-nums">{e.total}</TableCell>
                    <TableCell
                      className={
                        saldo !== undefined && saldo === 0
                          ? 'text-right font-semibold tabular-nums text-destructive'
                          : saldo !== undefined && saldo < 5
                            ? 'text-right font-semibold tabular-nums text-warning'
                            : 'text-right font-semibold tabular-nums'
                      }
                    >
                      {saldo ?? '—'}
                    </TableCell>
                  </TableRow>
                )
              })}
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
