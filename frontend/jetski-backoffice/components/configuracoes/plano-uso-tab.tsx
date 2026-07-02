'use client'

import { useQuery } from '@tanstack/react-query'
import { meteringService } from '@/lib/api/services'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Loader2, Gauge, FileCheck2, Landmark, Eye } from 'lucide-react'
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
export function PlanoUsoTab() {
  const { data: serie, isLoading } = useQuery({
    queryKey: ['metering-emissoes'],
    queryFn: () => meteringService.getEmissoesMensais(6),
  })

  const mesAtual = serie?.[serie.length - 1]

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
          </>
        )}
      </CardContent>
    </Card>
  )
}
