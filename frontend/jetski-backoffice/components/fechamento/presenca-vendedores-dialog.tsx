'use client'

import { useState, useEffect } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { Users, UserCheck, UserMinus, DollarSign, AlertCircle } from 'lucide-react'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Checkbox } from '@/components/ui/checkbox'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Separator } from '@/components/ui/separator'
import { Badge } from '@/components/ui/badge'
import { Skeleton } from '@/components/ui/skeleton'
import { presencasService } from '@/lib/api/services'
import { useTenantStore } from '@/lib/store/tenant-store'
import { formatCurrency, formatDate } from '@/lib/utils'
import type {
  Vendedor,
  TipoPresenca,
  PresencaVendedorRequest,
  RegistrarPresencasRequest,
  ResumoDiariasResponse,
} from '@/lib/api/types'

interface VendedorPresencaState {
  vendedorId: string
  vendedorNome: string
  diariaBase: number
  presente: boolean
  tipo: TipoPresenca
  valorAjustado?: number
  motivoAjuste?: string
}

interface PresencaVendedoresDialogProps {
  dtReferencia: string
  open: boolean
  onOpenChange: (open: boolean) => void
}

export function PresencaVendedoresDialog({
  dtReferencia,
  open,
  onOpenChange,
}: PresencaVendedoresDialogProps) {
  const { currentTenant } = useTenantStore()
  const queryClient = useQueryClient()
  const [vendedoresState, setVendedoresState] = useState<VendedorPresencaState[]>([])

  // Buscar vendedores ativos
  const { data: vendedores, isLoading: isLoadingVendedores } = useQuery({
    queryKey: ['presencas', 'vendedores', currentTenant?.id],
    queryFn: () => presencasService.getVendedoresParaPresenca(currentTenant!.id),
    enabled: !!currentTenant && open,
  })

  // Buscar presenças existentes
  const { data: presencasExistentes, isLoading: isLoadingPresencas } = useQuery({
    queryKey: ['presencas', 'dia', currentTenant?.id, dtReferencia],
    queryFn: () => presencasService.getResumoDiarias(currentTenant!.id, dtReferencia),
    enabled: !!currentTenant && open && !!dtReferencia,
  })

  // Inicializar estado quando dados carregam
  useEffect(() => {
    if (vendedores && open) {
      const initialState: VendedorPresencaState[] = vendedores.map((v) => {
        // Verificar se já existe presença para este vendedor
        const presencaExistente = presencasExistentes?.detalhes?.find(
          (p) => p.vendedorId === v.id
        )

        return {
          vendedorId: v.id,
          vendedorNome: v.nome,
          diariaBase: v.diariaBase || 0,
          presente: !!presencaExistente,
          tipo: presencaExistente?.tipo || 'INTEGRAL',
          valorAjustado: presencaExistente?.valorAjustado ?? undefined,
          motivoAjuste: presencaExistente?.motivoAjuste ?? undefined,
        }
      })
      setVendedoresState(initialState)
    }
  }, [vendedores, presencasExistentes, open])

  // Mutation para registrar presenças
  const registrarMutation = useMutation({
    mutationFn: (data: RegistrarPresencasRequest) =>
      presencasService.registrarPresencas(currentTenant!.id, data),
    onSuccess: (response) => {
      queryClient.invalidateQueries({ queryKey: ['presencas'] })
      queryClient.invalidateQueries({ queryKey: ['fechamentos'] })
      toast.success(
        `${response.totalVendedoresPresentes} presença(s) registrada(s) - Total: ${formatCurrency(response.totalDiarias)}`
      )
      onOpenChange(false)
    },
    onError: (error: Error) => {
      toast.error(`Erro ao registrar presenças: ${error.message}`)
    },
  })

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()

    const presencas: PresencaVendedorRequest[] = vendedoresState
      .filter((v) => v.presente)
      .map((v) => ({
        vendedorId: v.vendedorId,
        tipo: v.tipo,
        valorAjustado: v.valorAjustado,
        motivoAjuste: v.motivoAjuste,
      }))

    // Validar motivo de ajuste
    const semMotivo = presencas.find(
      (p) => p.valorAjustado !== undefined && p.valorAjustado !== null && !p.motivoAjuste?.trim()
    )
    if (semMotivo) {
      toast.error('Motivo do ajuste é obrigatório quando há valor ajustado')
      return
    }

    const request: RegistrarPresencasRequest = {
      dtReferencia,
      presencas,
    }

    registrarMutation.mutate(request)
  }

  const updateVendedor = (vendedorId: string, updates: Partial<VendedorPresencaState>) => {
    setVendedoresState((prev) =>
      prev.map((v) => (v.vendedorId === vendedorId ? { ...v, ...updates } : v))
    )
  }

  const calcularValorDiaria = (diariaBase: number, tipo: TipoPresenca): number => {
    const fator = tipo === 'INTEGRAL' ? 1.0 : 0.5
    return diariaBase * fator
  }

  const calcularTotalDiarias = (): number => {
    return vendedoresState
      .filter((v) => v.presente)
      .reduce((total, v) => {
        const valorCalculado = calcularValorDiaria(v.diariaBase, v.tipo)
        const valorEfetivo = v.valorAjustado ?? valorCalculado
        return total + valorEfetivo
      }, 0)
  }

  const totalPresentes = vendedoresState.filter((v) => v.presente).length
  const totalDiarias = calcularTotalDiarias()
  const isLoading = isLoadingVendedores || isLoadingPresencas

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-[600px] max-h-[85vh] overflow-y-auto">
        <form onSubmit={handleSubmit}>
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2">
              <Users className="h-5 w-5" />
              Registrar Presenças - {formatDate(dtReferencia)}
            </DialogTitle>
            <DialogDescription>
              Marque os vendedores presentes e o tipo de diária (integral ou meia-diária).
            </DialogDescription>
          </DialogHeader>

          <div className="py-4">
            {isLoading ? (
              <div className="space-y-3">
                {[1, 2, 3].map((i) => (
                  <Skeleton key={i} className="h-16 w-full" />
                ))}
              </div>
            ) : vendedoresState.length === 0 ? (
              <div className="text-center py-8 text-muted-foreground">
                <Users className="h-12 w-12 mx-auto mb-2 opacity-50" />
                <p>Nenhum vendedor cadastrado</p>
                <p className="text-sm">Cadastre vendedores com diária base para usar este recurso.</p>
              </div>
            ) : (
              <>
                {/* Resumo */}
                <div className="flex items-center justify-between mb-4 p-3 rounded-lg bg-muted/50">
                  <div className="flex items-center gap-4 text-sm">
                    <div className="flex items-center gap-1">
                      <UserCheck className="h-4 w-4 text-green-600" />
                      <span>{totalPresentes} presente(s)</span>
                    </div>
                  </div>
                  <div className="text-lg font-bold text-purple-600">
                    {formatCurrency(totalDiarias)}
                  </div>
                </div>

                <Separator className="my-4" />

                {/* Lista de vendedores */}
                <div className="space-y-4">
                  {vendedoresState.map((vendedor) => (
                    <VendedorPresencaRow
                      key={vendedor.vendedorId}
                      vendedor={vendedor}
                      onUpdate={(updates) => updateVendedor(vendedor.vendedorId, updates)}
                      calcularValorDiaria={calcularValorDiaria}
                    />
                  ))}
                </div>
              </>
            )}
          </div>

          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              onClick={() => onOpenChange(false)}
              disabled={registrarMutation.isPending}
            >
              Cancelar
            </Button>
            <Button
              type="submit"
              disabled={registrarMutation.isPending || isLoading || vendedoresState.length === 0}
            >
              {registrarMutation.isPending ? 'Salvando...' : 'Salvar Presenças'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

interface VendedorPresencaRowProps {
  vendedor: VendedorPresencaState
  onUpdate: (updates: Partial<VendedorPresencaState>) => void
  calcularValorDiaria: (diariaBase: number, tipo: TipoPresenca) => number
}

function VendedorPresencaRow({
  vendedor,
  onUpdate,
  calcularValorDiaria,
}: VendedorPresencaRowProps) {
  const [showAjuste, setShowAjuste] = useState(
    vendedor.valorAjustado !== undefined && vendedor.valorAjustado !== null
  )

  const valorCalculado = calcularValorDiaria(vendedor.diariaBase, vendedor.tipo)
  const valorEfetivo = vendedor.valorAjustado ?? valorCalculado
  const temDiariaBase = vendedor.diariaBase > 0

  return (
    <div
      className={`p-3 rounded-lg border ${
        vendedor.presente ? 'border-green-200 bg-green-50/50' : 'border-muted bg-muted/30'
      }`}
    >
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <Checkbox
            id={`presente-${vendedor.vendedorId}`}
            checked={vendedor.presente}
            onCheckedChange={(checked) => onUpdate({ presente: checked as boolean })}
          />
          <div>
            <Label
              htmlFor={`presente-${vendedor.vendedorId}`}
              className="font-medium cursor-pointer"
            >
              {vendedor.vendedorNome}
            </Label>
            {temDiariaBase ? (
              <p className="text-xs text-muted-foreground">
                Base: {formatCurrency(vendedor.diariaBase)}
              </p>
            ) : (
              <p className="text-xs text-warning flex items-center gap-1">
                <AlertCircle className="h-3 w-3" />
                Diária não configurada
              </p>
            )}
          </div>
        </div>

        {vendedor.presente && (
          <div className="flex items-center gap-2">
            <Select
              value={vendedor.tipo}
              onValueChange={(value: TipoPresenca) => onUpdate({ tipo: value })}
            >
              <SelectTrigger className="w-[130px] h-8">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="INTEGRAL">
                  <div className="flex items-center gap-2">
                    <UserCheck className="h-3 w-3 text-green-600" />
                    Integral
                  </div>
                </SelectItem>
                <SelectItem value="MEIA_DIARIA">
                  <div className="flex items-center gap-2">
                    <UserMinus className="h-3 w-3 text-yellow-600" />
                    Meia Diária
                  </div>
                </SelectItem>
              </SelectContent>
            </Select>
            <Badge variant="outline" className="min-w-[80px] justify-center">
              {formatCurrency(valorEfetivo)}
            </Badge>
          </div>
        )}
      </div>

      {vendedor.presente && (
        <div className="mt-3 pt-3 border-t border-dashed">
          <div className="flex items-center gap-2 mb-2">
            <Checkbox
              id={`ajuste-${vendedor.vendedorId}`}
              checked={showAjuste}
              onCheckedChange={(checked) => {
                setShowAjuste(checked as boolean)
                if (!checked) {
                  onUpdate({ valorAjustado: undefined, motivoAjuste: undefined })
                }
              }}
            />
            <Label
              htmlFor={`ajuste-${vendedor.vendedorId}`}
              className="text-xs text-muted-foreground cursor-pointer"
            >
              Ajustar valor manualmente
            </Label>
          </div>

          {showAjuste && (
            <div className="grid grid-cols-2 gap-3 mt-2">
              <div>
                <Label htmlFor={`valorAjustado-${vendedor.vendedorId}`} className="text-xs">
                  Valor Ajustado
                </Label>
                <Input
                  id={`valorAjustado-${vendedor.vendedorId}`}
                  type="number"
                  step="0.01"
                  min="0"
                  placeholder={valorCalculado.toFixed(2)}
                  value={vendedor.valorAjustado ?? ''}
                  onChange={(e) =>
                    onUpdate({
                      valorAjustado: e.target.value ? parseFloat(e.target.value) : undefined,
                    })
                  }
                  className="h-8"
                />
              </div>
              <div>
                <Label htmlFor={`motivoAjuste-${vendedor.vendedorId}`} className="text-xs">
                  Motivo *
                </Label>
                <Input
                  id={`motivoAjuste-${vendedor.vendedorId}`}
                  placeholder="Ex: Saiu mais cedo"
                  value={vendedor.motivoAjuste ?? ''}
                  onChange={(e) => onUpdate({ motivoAjuste: e.target.value })}
                  className="h-8"
                  required={showAjuste && vendedor.valorAjustado !== undefined}
                />
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  )
}

export default PresencaVendedoresDialog
