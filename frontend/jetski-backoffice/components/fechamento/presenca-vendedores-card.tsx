'use client'

import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Users, UserCheck, UserMinus, DollarSign, ChevronDown, ChevronUp, Edit } from 'lucide-react'
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Skeleton } from '@/components/ui/skeleton'
import {
  Collapsible,
  CollapsibleContent,
  CollapsibleTrigger,
} from '@/components/ui/collapsible'
import { presencasService } from '@/lib/api/services'
import { useTenantStore } from '@/lib/store/tenant-store'
import { formatCurrency } from '@/lib/utils'
import type { ResumoDiariasResponse, PresencaVendedorResponse } from '@/lib/api/types'

interface PresencaVendedoresCardProps {
  dtReferencia: string
  onRegistrarClick?: () => void
  readOnly?: boolean
}

export function PresencaVendedoresCard({
  dtReferencia,
  onRegistrarClick,
  readOnly = false,
}: PresencaVendedoresCardProps) {
  const { currentTenant } = useTenantStore()
  const [isOpen, setIsOpen] = useState(false)

  const { data: resumo, isLoading } = useQuery({
    queryKey: ['presencas', 'dia', currentTenant?.id, dtReferencia],
    queryFn: () => presencasService.getResumoDiarias(currentTenant!.id, dtReferencia),
    enabled: !!currentTenant && !!dtReferencia,
  })

  if (isLoading) {
    return (
      <Card>
        <CardHeader className="pb-2">
          <Skeleton className="h-4 w-[140px]" />
        </CardHeader>
        <CardContent>
          <Skeleton className="h-8 w-[100px]" />
          <Skeleton className="h-4 w-[180px] mt-2" />
        </CardContent>
      </Card>
    )
  }

  const hasPresencas = resumo && resumo.totalVendedoresPresentes > 0

  return (
    <Card>
      <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
        <div>
          <CardTitle className="text-sm font-medium">Diárias de Vendedores</CardTitle>
          <CardDescription className="text-xs">
            {hasPresencas
              ? `${resumo.totalVendedoresPresentes} vendedor(es) presente(s)`
              : 'Nenhuma presença registrada'
            }
          </CardDescription>
        </div>
        <Users className="h-4 w-4 text-muted-foreground" />
      </CardHeader>
      <CardContent>
        <div className="text-2xl font-bold text-purple-600">
          {formatCurrency(resumo?.totalDiarias || 0)}
        </div>

        {hasPresencas && (
          <div className="flex items-center gap-4 mt-2 text-xs text-muted-foreground">
            <div className="flex items-center gap-1">
              <UserCheck className="h-3 w-3 text-green-600" />
              <span>{resumo.totalIntegral} integral</span>
            </div>
            {resumo.totalMeiaDiaria > 0 && (
              <div className="flex items-center gap-1">
                <UserMinus className="h-3 w-3 text-yellow-600" />
                <span>{resumo.totalMeiaDiaria} meia-diária</span>
              </div>
            )}
          </div>
        )}

        {!readOnly && (
          <Button
            variant="outline"
            size="sm"
            className="mt-3 w-full"
            onClick={onRegistrarClick}
          >
            <Edit className="mr-2 h-4 w-4" />
            {hasPresencas ? 'Editar Presenças' : 'Registrar Presenças'}
          </Button>
        )}

        {hasPresencas && resumo.detalhes && resumo.detalhes.length > 0 && (
          <Collapsible open={isOpen} onOpenChange={setIsOpen} className="mt-3">
            <CollapsibleTrigger asChild>
              <Button variant="ghost" size="sm" className="w-full justify-between p-0 h-auto">
                <span className="text-xs text-muted-foreground">Ver detalhes</span>
                {isOpen ? (
                  <ChevronUp className="h-4 w-4" />
                ) : (
                  <ChevronDown className="h-4 w-4" />
                )}
              </Button>
            </CollapsibleTrigger>
            <CollapsibleContent className="mt-2 space-y-2">
              {resumo.detalhes.map((presenca) => (
                <PresencaItem key={presenca.id} presenca={presenca} />
              ))}
            </CollapsibleContent>
          </Collapsible>
        )}
      </CardContent>
    </Card>
  )
}

function PresencaItem({ presenca }: { presenca: PresencaVendedorResponse }) {
  const isAjustado = presenca.valorAjustado !== null && presenca.valorAjustado !== undefined

  return (
    <div className="flex items-center justify-between py-1 px-2 rounded-md bg-muted/50 text-sm">
      <div className="flex items-center gap-2">
        <span className="font-medium">{presenca.vendedorNome}</span>
        <Badge
          variant={presenca.tipo === 'INTEGRAL' ? 'default' : 'secondary'}
          className="text-[10px] px-1.5 py-0"
        >
          {presenca.tipo === 'INTEGRAL' ? 'Integral' : 'Meia'}
        </Badge>
        {isAjustado && (
          <Badge variant="outline" className="text-[10px] px-1.5 py-0 text-warning border-warning/40">
            Ajustado
          </Badge>
        )}
      </div>
      <span className={isAjustado ? 'text-warning font-medium' : ''}>
        {formatCurrency(presenca.valorEfetivo)}
      </span>
    </div>
  )
}

export default PresencaVendedoresCard
