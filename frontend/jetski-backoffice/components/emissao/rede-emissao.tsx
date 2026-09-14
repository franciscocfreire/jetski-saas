'use client'

import { Anchor, Building2 } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import type { ContagemDelegada, VinculoEmissao } from '@/lib/api/services/emissao-delegada'

const STATUS: Record<VinculoEmissao['status'], { label: string; className: string }> = {
  CONVIDADO: { label: 'convite pendente', className: 'bg-amber-100 text-amber-900' },
  ATIVO: { label: 'ativa', className: 'bg-emerald-100 text-emerald-900' },
  BLOQUEADO: { label: 'bloqueada', className: 'bg-red-100 text-red-900' },
  REVOGADO: { label: 'revogada', className: 'bg-muted text-muted-foreground' },
}

/**
 * Rede de emissão em árvore (§8.M): a EAMA emissora no topo e, abaixo, as operadoras
 * delegadas. Na visão da EAMA, uma folha por parceria (com as emissões do mês); na da
 * delegada, a EAMA dela e a própria empresa.
 */
export function RedeEmissao({
  minhaEmpresa,
  vinculos,
  contagens,
}: {
  minhaEmpresa: string
  vinculos: VinculoEmissao[]
  contagens?: ContagemDelegada[]
}) {
  const vivos = vinculos.filter((v) => v.status !== 'REVOGADO')
  const comoEmissora = vivos.filter((v) => v.papel === 'EMISSORA')
  const comoOperadora = vivos.find((v) => v.papel === 'OPERADORA')
  if (comoEmissora.length === 0 && !comoOperadora) return null

  const mes = new Date().toISOString().slice(0, 7)
  const emissoesNoMes = (operadoraId: string) =>
    (contagens ?? [])
      .filter((c) => c.operadoraTenantId === operadoraId && c.mes === mes)
      .reduce((soma, c) => soma + c.total, 0)

  const souDelegada = !!comoOperadora && comoEmissora.length === 0
  const raiz = souDelegada ? (comoOperadora.parceiroNome ?? 'EAMA parceira') : minhaEmpresa

  return (
    <Card data-testid="delegada-rede">
      <CardHeader>
        <CardTitle className="text-base">Rede de emissão</CardTitle>
        <CardDescription>
          Quem emite em nome de quem: a EAMA emissora responde pelos documentos das operadoras
          delegadas abaixo dela.
        </CardDescription>
      </CardHeader>
      <CardContent>
        <div
          data-testid="delegada-rede-raiz"
          className="inline-flex items-center gap-2 rounded-lg border border-emerald-300 bg-emerald-50 px-3 py-2"
        >
          <Anchor className="h-4 w-4 text-emerald-700" />
          <span className="font-medium">{raiz}</span>
          <Badge className="bg-emerald-100 text-emerald-900">EAMA emissora</Badge>
          {!souDelegada && <span className="text-xs text-muted-foreground">(sua empresa)</span>}
        </div>
        <ul className="ml-5 mt-1 space-y-2 border-l border-dashed border-muted-foreground/40 pl-6 pt-2">
          {souDelegada ? (
            <li className="relative" data-testid="delegada-rede-no" data-status={comoOperadora.status}>
              <span className="absolute -left-6 top-1/2 w-5 border-t border-dashed border-muted-foreground/40" />
              <div className="inline-flex items-center gap-2 rounded-lg border px-3 py-2">
                <Building2 className="h-4 w-4 text-sky-700" />
                <span className="font-medium">{minhaEmpresa}</span>
                <Badge className="bg-sky-100 text-sky-900">delegada</Badge>
                <Badge className={STATUS[comoOperadora.status].className}>
                  {STATUS[comoOperadora.status].label}
                </Badge>
                <span className="text-xs text-muted-foreground">(sua empresa)</span>
              </div>
            </li>
          ) : (
            comoEmissora.map((v) => {
              const total = emissoesNoMes(v.parceiroTenantId)
              return (
                <li
                  key={v.id}
                  className="relative"
                  data-testid="delegada-rede-no"
                  data-status={v.status}
                  data-parceiro={v.parceiroNome ?? ''}
                >
                  <span className="absolute -left-6 top-1/2 w-5 border-t border-dashed border-muted-foreground/40" />
                  <div className="inline-flex flex-wrap items-center gap-2 rounded-lg border px-3 py-2">
                    <Building2 className="h-4 w-4 text-sky-700" />
                    <span className="font-medium">{v.parceiroNome ?? v.parceiroTenantId}</span>
                    <Badge className="bg-sky-100 text-sky-900">delegada</Badge>
                    <Badge className={STATUS[v.status].className}>{STATUS[v.status].label}</Badge>
                    {v.status !== 'CONVIDADO' && (
                      <span className="text-xs text-muted-foreground">
                        {total} {total === 1 ? 'emissão' : 'emissões'} em {mes}
                      </span>
                    )}
                  </div>
                </li>
              )
            })
          )}
        </ul>
      </CardContent>
    </Card>
  )
}
