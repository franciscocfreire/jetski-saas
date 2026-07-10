'use client'

import { useQuery } from '@tanstack/react-query'
import { documentosConfigService } from '@/lib/api/services'
import { useTenantStore } from '@/lib/store/tenant-store'
import type { PresetCompressaoImagem, TipoImagemDoc } from '@/lib/api/types'

/** Defaults hardcoded — usados enquanto a config carrega ou se a leitura falhar. */
const DEFAULT_PRESETS: Record<TipoImagemDoc, PresetCompressaoImagem> = {
  IDENTIDADE: { maxDimensao: 2000, qualidade: 0.85 },
  COMPROVANTE_RESIDENCIA: { maxDimensao: 2000, qualidade: 0.85 },
  CHA: { maxDimensao: 2000, qualidade: 0.85 },
  GRU_COMPROVANTE: { maxDimensao: 2000, qualidade: 0.85 },
  SELFIE: { maxDimensao: 1280, qualidade: 0.8 },
}

/** Preset para imagem sem tipo definido (ex.: upload genérico). */
const PRESET_GENERICO: PresetCompressaoImagem = { maxDimensao: 2000, qualidade: 0.82 }

/**
 * Presets de compressão da plataforma, com fallback nos defaults — a compressão
 * NUNCA depende do sucesso da leitura (upload não pode quebrar por causa disso).
 * Query cacheada e compartilhada entre todos os FileUpload.
 */
export function useImagemConfig() {
  const { currentTenant } = useTenantStore()

  const { data } = useQuery({
    queryKey: ['imagem-config', currentTenant?.id],
    queryFn: () => documentosConfigService.getImagemConfig(),
    enabled: !!currentTenant,
    staleTime: 30 * 60 * 1000, // muda raramente
    retry: 1,
  })

  const presetPara = (tipo?: TipoImagemDoc): PresetCompressaoImagem => {
    if (!tipo) return PRESET_GENERICO
    return data?.tipos?.[tipo] ?? DEFAULT_PRESETS[tipo]
  }

  return { presetPara }
}
