'use client'

import { useQuery } from '@tanstack/react-query'
import { creditosService, meteringService } from '@/lib/api/services'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Loader2, Gauge, FileCheck2, Landmark, Eye, Coins } from 'lucide-react'
import { cn } from '@/lib/utils'
import {
  Bar,
  BarChart,
  CartesianGrid,
  Legend,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'

/**
 * Aba Configurações › Plano e Uso: consumo de emissões do tenant.
 * Hoje é apenas contagem; a cobrança por emissão entrará nos planos futuros.
 */
const TIPO_LABEL: Record<string, string> = {
  ADESAO: 'Créditos de adesão',
  AJUSTE: 'Lançamento Meu Jet',
  CONSUMO: 'Emissão de documento',
  ESTORNO: 'Estorno',
}

export function PlanoUsoTab() {
  const { data: serie, isLoading } = useQuery({
    queryKey: ['metering-emissoes'],
    queryFn: () => meteringService.getEmissoesMensais(6),
  })
  const { data: saldoData } = useQuery({
    queryKey: ['creditos-saldo'],
    queryFn: () => creditosService.getSaldo(),
  })
  const { data: extrato } = useQuery({
    queryKey: ['creditos-extrato'],
    queryFn: () => creditosService.getExtrato(10),
  })

  const mesAtual = serie?.[serie.length - 1]
  const saldo = saldoData?.saldo

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <Gauge className="h-5 w-5" />
          Plano e Uso
        </CardTitle>
        <CardDescription>
          Consumo de emissões da sua empresa. A cobrança por emissão fará parte dos
          planos futuros — por enquanto é apenas contagem informativa.
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-6">
        {isLoading || !serie ? (
          <div className="flex items-center gap-2 text-sm text-muted-foreground">
            <Loader2 className="h-4 w-4 animate-spin" /> Carregando…
          </div>
        ) : (
          <>
            {/* Saldo de créditos — pré-pago: cada documento à Marinha consome 1 */}
            <div
              className={cn(
                'rounded-lg border p-5',
                saldo === undefined
                  ? ''
                  : saldo === 0
                    ? 'border-destructive/50 bg-destructive/5'
                    : saldo < 5
                      ? 'border-warning/50 bg-warning/5'
                      : 'border-gold/40 bg-gold/5'
              )}
            >
              <div className="flex flex-wrap items-center justify-between gap-4">
                <div>
                  <div className="flex items-center gap-2 text-xs font-medium uppercase tracking-wide text-muted-foreground">
                    <Coins className="h-4 w-4" /> Saldo de créditos de emissão
                  </div>
                  <div className="mt-1 text-3xl font-semibold tabular-nums">
                    {saldo ?? '—'}
                  </div>
                  <p className="mt-1 text-xs text-muted-foreground">
                    Cada documento emitido à Marinha consome 1 crédito. Emissões via CHA não consomem.
                  </p>
                </div>
                {saldo !== undefined && saldo === 0 && (
                  <p className="text-sm font-medium text-destructive">
                    Créditos esgotados — novas emissões estão bloqueadas. Fale com o Meu Jet.
                  </p>
                )}
                {saldo !== undefined && saldo > 0 && saldo < 5 && (
                  <p className="text-sm font-medium text-warning">
                    Saldo baixo — garanta mais créditos para não travar o balcão.
                  </p>
                )}
              </div>
            </div>

            <div className="grid gap-4 sm:grid-cols-3">
              <div className="rounded-lg border p-4">
                <div className="flex items-center gap-2 text-xs font-medium uppercase tracking-wide text-muted-foreground">
                  <FileCheck2 className="h-4 w-4" /> Documentos emitidos
                </div>
                <div className="mt-1 text-2xl font-semibold tabular-nums">{mesAtual?.documento ?? 0}</div>
                <div className="text-xs text-muted-foreground">neste mês</div>
              </div>
              <div className="rounded-lg border p-4">
                <div className="flex items-center gap-2 text-xs font-medium uppercase tracking-wide text-muted-foreground">
                  <Landmark className="h-4 w-4" /> GRUs geradas
                </div>
                <div className="mt-1 text-2xl font-semibold tabular-nums">{mesAtual?.gru ?? 0}</div>
                <div className="text-xs text-muted-foreground">neste mês</div>
              </div>
              <div className="rounded-lg border p-4">
                <div className="flex items-center gap-2 text-xs font-medium uppercase tracking-wide text-muted-foreground">
                  <Eye className="h-4 w-4" /> Prévias geradas
                </div>
                <div className="mt-1 text-2xl font-semibold tabular-nums">{mesAtual?.previa ?? 0}</div>
                <div className="text-xs text-muted-foreground">não cobrável</div>
              </div>
            </div>

            <div>
              <p className="mb-2 text-sm font-medium">Últimos 6 meses</p>
              <ResponsiveContainer width="100%" height={240}>
                <BarChart data={serie}>
                  <CartesianGrid strokeDasharray="2 3" vertical={false} />
                  <XAxis dataKey="competencia" fontSize={11} />
                  <YAxis allowDecimals={false} fontSize={11} width={32} />
                  <Tooltip />
                  <Legend />
                  <Bar name="Documentos" dataKey="documento" fill="var(--chart-1)" radius={[3, 3, 0, 0]} />
                  <Bar name="GRUs" dataKey="gru" fill="var(--chart-2)" radius={[3, 3, 0, 0]} />
                  <Bar name="Prévias" dataKey="previa" fill="var(--chart-3)" radius={[3, 3, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
            </div>

            {extrato && extrato.length > 0 && (
              <div>
                <p className="mb-2 text-sm font-medium">Movimentação de créditos</p>
                <div className="divide-y rounded-lg border">
                  {extrato.map((l) => (
                    <div key={l.id} className="flex items-center justify-between gap-4 px-4 py-2.5 text-sm">
                      <div className="min-w-0">
                        <span className="font-medium">{TIPO_LABEL[l.tipo] ?? l.tipo}</span>
                        {l.motivo && (
                          <span className="ml-2 truncate text-xs text-muted-foreground">{l.motivo}</span>
                        )}
                      </div>
                      <div className="flex shrink-0 items-center gap-4 tabular-nums">
                        <span className={cn('font-semibold', l.quantidade > 0 ? 'text-success' : 'text-muted-foreground')}>
                          {l.quantidade > 0 ? `+${l.quantidade}` : l.quantidade}
                        </span>
                        <span className="w-16 text-right text-xs text-muted-foreground">saldo {l.saldoApos}</span>
                        <span className="w-20 text-right text-xs text-muted-foreground">
                          {new Date(l.createdAt).toLocaleDateString('pt-BR')}
                        </span>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </>
        )}
      </CardContent>
    </Card>
  )
}
