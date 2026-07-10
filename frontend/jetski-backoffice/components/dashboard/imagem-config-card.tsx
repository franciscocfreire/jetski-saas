'use client'

import { useEffect, useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { ImageDown } from 'lucide-react'
import { documentosConfigService } from '@/lib/api/services'
import type { ImagemCompressaoConfig, PresetCompressaoImagem, TipoImagemDoc } from '@/lib/api/types'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { useToast } from '@/hooks/use-toast'

const TIPOS: { key: TipoImagemDoc; label: string }[] = [
  { key: 'IDENTIDADE', label: 'RG / CNH' },
  { key: 'COMPROVANTE_RESIDENCIA', label: 'Comprovante de residência' },
  { key: 'CHA', label: 'CHA / CHV' },
  { key: 'GRU_COMPROVANTE', label: 'Comprovante de pagamento (GRU)' },
  { key: 'SELFIE', label: 'Selfie / foto do cliente' },
]

/**
 * Super admin regula a qualidade de compressão de imagem por tipo de documento
 * (global à plataforma). Aplicada no navegador do backoffice antes do upload.
 * Assinatura fica de fora (é PNG). Molde: PrecoCreditoCard.
 */
export function ImagemConfigCard({ enabled }: { enabled: boolean }) {
  const queryClient = useQueryClient()
  const { toast } = useToast()
  const [tipos, setTipos] = useState<Record<string, PresetCompressaoImagem>>({})

  const { data } = useQuery({
    queryKey: ['platform', 'imagem-config'],
    queryFn: () => documentosConfigService.getPlatformImagemConfig(),
    enabled,
  })

  useEffect(() => {
    if (data?.tipos) setTipos({ ...data.tipos } as Record<string, PresetCompressaoImagem>)
  }, [data])

  const salvar = useMutation({
    mutationFn: (cfg: ImagemCompressaoConfig) => documentosConfigService.atualizarImagemConfig(cfg),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['platform', 'imagem-config'] })
      queryClient.invalidateQueries({ queryKey: ['imagem-config'] }) // hook dos tenants
      toast({ title: 'Compressão de imagem atualizada', description: 'Vale para novos uploads.' })
    },
    onError: (e: unknown) => {
      const err = e as { response?: { data?: { message?: string } }; message?: string }
      toast({
        title: 'Erro ao salvar',
        description: err.response?.data?.message || err.message || 'Erro inesperado',
        variant: 'destructive',
      })
    },
  })

  const set = (key: TipoImagemDoc, campo: keyof PresetCompressaoImagem, valor: number) =>
    setTipos((t) => {
      const atual = t[key] ?? { maxDimensao: 2000, qualidade: 0.85 }
      return { ...t, [key]: { ...atual, [campo]: valor } }
    })

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <ImageDown className="size-5" /> Compressão de imagem por documento
        </CardTitle>
        <CardDescription>
          Fotos são reduzidas no navegador antes de enviar — mais leve na rede e sem estourar
          limites. Qualidade menor = arquivo menor; mantenha alta em documentos que a Marinha lê.
          Assinatura não é comprimida.
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-3">
        <div className="grid grid-cols-[1fr_auto_auto] items-center gap-x-3 gap-y-2 text-sm">
          <span className="text-xs font-medium text-muted-foreground">Documento</span>
          <span className="text-xs font-medium text-muted-foreground">Qualidade (0.3–1.0)</span>
          <span className="text-xs font-medium text-muted-foreground">Resolução máx. (px)</span>
          {TIPOS.map(({ key, label }) => {
            const p = tipos[key] ?? { maxDimensao: 2000, qualidade: 0.85 }
            return (
              <FragmentRow key={key}>
                <span>{label}</span>
                <Input
                  type="number"
                  min={0.3}
                  max={1}
                  step="0.05"
                  value={p.qualidade}
                  onChange={(e) => set(key, 'qualidade', Number(e.target.value))}
                  className="w-24 tabular-nums"
                />
                <Input
                  type="number"
                  min={400}
                  max={4000}
                  step="100"
                  value={p.maxDimensao}
                  onChange={(e) => set(key, 'maxDimensao', Number(e.target.value))}
                  className="w-24 tabular-nums"
                />
              </FragmentRow>
            )
          })}
        </div>
        <div className="flex justify-end">
          <Button
            onClick={() => salvar.mutate({ tipos: tipos as ImagemCompressaoConfig['tipos'] })}
            disabled={salvar.isPending || Object.keys(tipos).length === 0}
          >
            {salvar.isPending ? 'Salvando...' : 'Salvar compressão'}
          </Button>
        </div>
      </CardContent>
    </Card>
  )
}

/** Linha do grid (3 colunas) sem wrapper que quebre o layout. */
function FragmentRow({ children }: { children: React.ReactNode }) {
  return <>{children}</>
}
